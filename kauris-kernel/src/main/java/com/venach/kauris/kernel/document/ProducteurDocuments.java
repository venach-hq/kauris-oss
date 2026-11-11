package com.venach.kauris.kernel.document;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.Logo;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fabrique les documents de tous les modules sur la même ossature (en-tête de l'entreprise, titre, cartouche,
 * contenu, signatures, pied légal, bas de page numéroté), avec le thème de l'entreprise.
 *
 * <p>Un module prépare l'{@link Habillage} puis, à l'écran, renvoie sa vue Thymeleaf ; en PDF, il appelle
 * {@link #pdf(String, Map)} avec la même vue. Voir {@code docs/regles.md}, « Documents ».
 */
public class ProducteurDocuments {

    /** Les pays de l'UEMOA sont tous à l'heure de Greenwich. */
    private static final DateTimeFormatter EDITE_LE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH).withZone(ZoneOffset.UTC);

    private final ITemplateEngine moteur;
    private final GererEntrepriseUseCase entreprises;
    private final CataloguePolices polices;
    private final Clock horloge;
    private final String miseEnPage;

    public ProducteurDocuments(ITemplateEngine moteur, GererEntrepriseUseCase entreprises, CataloguePolices polices,
                               Clock horloge) {
        this.moteur = moteur;
        this.entreprises = entreprises;
        this.polices = polices;
        this.horloge = horloge;
        try {
            this.miseEnPage = new ClassPathResource("static/kauris/document.css")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException absente) {
            throw new UncheckedIOException("Feuille static/kauris/document.css introuvable", absente);
        }
    }

    public CataloguePolices polices() {
        return polices;
    }

    /** L'habillage d'un document de l'organisation : ses paramètres, son logo et son thème enregistrés. */
    public Habillage habillage(UUID tenantId, String titre, String precision, String lienPdf, boolean pdf) {
        Entreprise entreprise = entreprises.consulter(tenantId);
        return habillage(entreprise, entreprise.theme(), entreprises.logo(tenantId), titre, precision, lienPdf,
                pdf);
    }

    /** Pour un autre émetteur (la société qui facture l'abonnement) ou pour l'aperçu d'un thème non enregistré. */
    public Habillage habillage(Entreprise entreprise, ThemeDocument theme, Optional<Logo> logo, String titre,
                               String precision, String lienPdf, boolean pdf) {
        String image = logo.map(l -> "data:" + l.typeMime() + ";base64,"
                + Base64.getEncoder().encodeToString(l.contenu())).orElse(null);
        String feuille = miseEnPage + "\n" + theme.feuille() + (pdf ? "" : "\n" + policesPourLEcran(theme));
        return new Habillage(entreprise, theme, image, feuille, titre, precision == null ? "" : precision,
                EDITE_LE.format(horloge.instant()), pdf ? null : lienPdf, pdf);
    }

    /** À l'écran, le navigateur charge les fichiers des polices du thème depuis le serveur. */
    private String policesPourLEcran(ThemeDocument theme) {
        Set<String> familles = new LinkedHashSet<>();
        familles.add(theme.policeTitres());
        familles.add(theme.policeTexte());
        StringBuilder css = new StringBuilder();
        for (String famille : familles) {
            polices.police(famille).ifPresent(p -> p.fichiers().forEach(f -> css
                    .append("@font-face{font-family:'").append(p.famille()).append("';src:url('/kauris/polices/")
                    .append(f.nom()).append("');font-weight:").append(f.graisse()).append(";font-style:")
                    .append(f.italique() ? "italic" : "normal").append(";}")));
        }
        return css.toString();
    }

    /**
     * Le PDF d'une vue de document. {@code variables} contient l'{@link Habillage} (clé « habillage », fabriqué
     * avec {@code pdf = true}) et tout ce que la vue affiche. La vue ne doit pas utiliser d'adresse « @{…} » :
     * hors d'une requête, Thymeleaf ne sait pas les construire.
     */
    public byte[] pdf(String vue, Map<String, Object> variables) {
        Object habillage = variables.get("habillage");
        if (!(habillage instanceof Habillage h) || !h.pdf()) {
            throw new IllegalArgumentException("Variable « habillage » préparée pour le PDF attendue");
        }
        String html = moteur.process(vue, new Context(Locale.FRENCH, variables));
        org.w3c.dom.Document dom = new W3CDom().fromJsoup(Jsoup.parse(html));
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder pdf = new PdfRendererBuilder();
            pdf.useFastMode();
            pdf.withW3cDocument(dom, "/");
            pdf.withProducer("Kauris");
            for (CataloguePolices.Police police : polices.toutes()) {
                for (CataloguePolices.Fichier f : police.fichiers()) {
                    pdf.useFont(f.chemin().toFile(), police.famille(), f.graisse(),
                            f.italique() ? BaseRendererBuilder.FontStyle.ITALIC : BaseRendererBuilder.FontStyle.NORMAL,
                            true);
                }
            }
            pdf.toStream(sortie);
            pdf.run();
        } catch (IOException | RuntimeException echec) {
            throw new ErreurMetier("Le PDF n'a pas pu être produit : " + echec.getMessage());
        }
        return sortie.toByteArray();
    }

    /** « livre-journal-20261009.pdf » : sans accent ni espace, pour tous les navigateurs. */
    public static String nomDeFichier(String titre, String suffixe) {
        String simple = java.text.Normalizer.normalize(titre, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return (simple.isEmpty() ? "document" : simple) + (suffixe == null || suffixe.isBlank() ? "" : "-" + suffixe)
                + ".pdf";
    }
}
