package com.venach.kauris.kernel.document;

import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase.Saisie;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase.SaisieTheme;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument;
import com.venach.kauris.kernel.organisation.EnMemoire;
import com.venach.kauris.kernel.organisation.Monde;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.domain.Identite;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L'ossature commune rendue par Thymeleaf puis en PDF, sans Spring ni base. */
class DocumentsPdfTest {

    private final Monde monde = new Monde();
    private final SpringTemplateEngine moteur = new SpringTemplateEngine();
    private final ProducteurDocuments documents;
    private final OrganisationOuverte o = monde.ouvrir("Atlantis", "awa");
    private final Acces awa = monde.acces.accesDe(Identite.venach("awa")).get(0);

    DocumentsPdfTest() {
        ClassLoaderTemplateResolver gabarits = new ClassLoaderTemplateResolver();
        gabarits.setPrefix("templates/");
        gabarits.setSuffix(".html");
        gabarits.setTemplateMode(TemplateMode.HTML);
        gabarits.setCharacterEncoding("UTF-8");
        moteur.setTemplateResolver(gabarits);
        documents = new ProducteurDocuments(moteur, monde.entreprises, CataloguePolices.vide(), EnMemoire.HORLOGE);
        monde.entreprises.enregistrer(awa, new Saisie("Atlantis Automobiles Togo", "", "SA", "150 000 000", "TG",
                "Lomé", "Lot n° 214, Boulevard du Mono", "", "+228 22 27 64 10", "contact@atlantis-auto.tg",
                "https://www.atlantis-auto.tg", Map.of("nif", "1000842317", "rccm", "TG-LFW-01-2019-B14-00873"),
                "XOF", Set.of(), "Ecobank Togo", "Atlantis Automobiles Togo SA", "TG42 TG90 8010 0100 4521 8730 1642",
                "", "Lomé Centre", ""));
    }

    private Map<String, Object> exemple(Habillage habillage) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("habillage", habillage);
        variables.put("cartouche", Cartouche.de(Cartouche.c("Entité", "Atlantis Automobiles Togo SA"),
                Cartouche.c("Période", "du 01/01/2026 au 09/10/2026"), Cartouche.c("Monnaie", "FCFA")));
        variables.put("etabliPar", "Awa");
        variables.put("visas", List.of("Visa du comptable", "Visa du dirigeant"));
        return variables;
    }

    private static String texte(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void lePdfPorteEnTeteTitreCartouchePiedLegalEtNumerosDePage() throws IOException {
        Habillage h = documents.habillage(o.tenantId(), "Livre-journal", "du 01/01/2026 au 09/10/2026", null, true);

        byte[] pdf = documents.pdf("kauris/apercu-document", exemple(h));

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
        String lu = texte(pdf);
        assertTrue(lu.contains("ATLANTIS AUTOMOBILES TOGO SA"), lu);
        assertTrue(lu.contains("NIF : 1000842317"), lu);
        assertTrue(lu.contains("RCCM : TG-LFW-01-2019-B14-00873"), lu);
        assertTrue(lu.contains("LIVRE-JOURNAL"), lu);
        assertTrue(lu.contains("PÉRIODE"), lu);
        assertTrue(lu.contains("Visa du dirigeant"), lu);
        assertTrue(lu.contains("Capital social : 150"), lu);
        assertTrue(lu.contains("Coordonnées bancaires"), lu);
        assertTrue(lu.contains("Page 1 / 1"), lu);
        assertTrue(lu.contains("édité le 11/11/2026 à 08:00"), lu);
    }

    @Test
    void lEcranAffichesLeBoutonPdfEtChargeLesPolicesDuServeur() {
        Habillage h = documents.habillage(o.tenantId(), "Document d'exemple", "", "/entreprise/apercu.pdf", false);

        String html = moteur.process("kauris/apercu-document", new Context(Locale.FRENCH, exemple(h)));

        assertTrue(html.contains("Télécharger le PDF"));
        assertTrue(html.contains("/entreprise/apercu.pdf"));
        assertTrue(html.contains("'Inter', Helvetica"));
        assertFalse(html.contains("Page 1 /"));
    }

    @Test
    void unThemeNonEnregistrePeutEtreMontreEtLeLogoSePlaceADroite() throws IOException {
        ThemeDocument elegant = monde.entreprises.lire(new SaisieTheme("ELEGANT", "", "", "", "", "", "", "", ""));
        Habillage h = documents.habillage(monde.entreprises.consulter(o.tenantId()), elegant, Optional.empty(),
                "Reçu", "n° 12", null, true);

        assertFalse(h.logoAGauche());
        assertTrue(h.feuille().contains("'Poppins'"));
        assertTrue(texte(documents.pdf("kauris/apercu-document", exemple(h))).contains("REÇU"));
        assertEquals("Atlantis Automobiles Togo SA — Reçu n° 12 — édité le 11/11/2026 à 08:00",
                h.basDePage());
    }

    @Test
    void leNomDeFichierEstSimple() {
        assertEquals("livre-journal-20261009.pdf", ProducteurDocuments.nomDeFichier("Livre-journal", "20261009"));
        assertEquals("releve-des-mouvements.pdf", ProducteurDocuments.nomDeFichier("Relevé des mouvements", null));
    }

    /**
     * Les trois modèles, avec un logo, écrits dans target/documents-exemple pour être regardés (la CI les garde).
     * Avec les polices DejaVu du système quand elles existent, pour vérifier aussi l'intégration des polices.
     */
    @Test
    void lesTroisModelesEnPdfPourLesRegarder() throws IOException {
        Path sortie = Path.of("target", "documents-exemple");
        Files.createDirectories(sortie);
        CataloguePolices polices = CataloguePolices.vide();
        Path dejavu = Path.of("/usr/share/fonts/truetype/dejavu");
        if (Files.isRegularFile(dejavu.resolve("DejaVuSans.ttf"))) {
            Path dossier = Files.createTempDirectory("polices");
            Files.copy(dejavu.resolve("DejaVuSans.ttf"), dossier.resolve("DejaVuSans-Regular.ttf"));
            if (Files.isRegularFile(dejavu.resolve("DejaVuSans-Bold.ttf"))) {
                Files.copy(dejavu.resolve("DejaVuSans-Bold.ttf"), dossier.resolve("DejaVuSans-Bold.ttf"));
            }
            polices = CataloguePolices.depuis(dossier);
        }
        ProducteurDocuments producteur = new ProducteurDocuments(moteur, monde.entreprises, polices,
                EnMemoire.HORLOGE);
        monde.entreprises.definirLogo(awa, logo());
        for (ThemeDocument.Modele modele : ThemeDocument.Modele.values()) {
            ThemeDocument theme = modele == ThemeDocument.Modele.PERSONNALISE
                    ? new ThemeDocument(modele, polices.connait("DejaVuSans") ? "DejaVuSans" : "Times",
                    polices.connait("DejaVuSans") ? "DejaVuSans" : "Helvetica", 10, "#B45309", "#FEF3C7",
                    "#1C1917", ThemeDocument.PositionLogo.GAUCHE, ThemeDocument.StyleTitre.MAJUSCULES)
                    : ThemeDocument.du(modele);
            Habillage h = producteur.habillage(monde.entreprises.consulter(o.tenantId()), theme,
                    monde.entreprises.logo(o.tenantId()), "Livre-journal", "du 01/01/2026 au 09/10/2026", null,
                    true);
            byte[] pdf = producteur.pdf("kauris/apercu-document", exemple(h));
            Files.write(sortie.resolve("exemple-" + modele.name().toLowerCase(Locale.ROOT) + ".pdf"), pdf);
            assertTrue(texte(pdf).contains("ATLANTIS AUTOMOBILES TOGO SA"));
        }
    }

    /** Un logo PNG dessiné pour l'exemple : un carré de couleur avec un « A ». */
    private static byte[] logo() throws IOException {
        BufferedImage image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1E293B));
        g.fillRoundRect(0, 0, 160, 160, 28, 28);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 110));
        g.drawString("A", 42, 122);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return png.toByteArray();
    }
}
