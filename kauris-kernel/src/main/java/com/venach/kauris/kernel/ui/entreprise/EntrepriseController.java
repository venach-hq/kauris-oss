package com.venach.kauris.kernel.ui.entreprise;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase.SaisieTheme;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase.Saisie;
import com.venach.kauris.kernel.entreprise.domain.Devise;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.FormeJuridique;
import com.venach.kauris.kernel.entreprise.domain.Logo;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.entreprise.domain.ThemeDocument;
import com.venach.kauris.kernel.module.PermissionsNoyau;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Écran « Entreprise » : identité et identification légale selon le pays, monnaies, logo et thème des
 * documents. La lecture demande {@code entreprise.consulter} ; les modifications, {@code entreprise.modifier},
 * contrôlée par le service.
 */
@Controller
@RequestMapping("/entreprise")
public class EntrepriseController {

    private static final String PAGE = "/entreprise";
    /** Préfixe des champs d'identification dans le formulaire : « identifiant.nif ». */
    private static final String IDENTIFIANT = "identifiant.";

    /** Un champ d'identification légale et sa valeur, prêts à afficher. */
    public record Champ(String code, String libelle, String aide, String valeur) {
    }

    private final ContexteCourant contexte;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments documents;

    public EntrepriseController(ContexteCourant contexte, GererEntrepriseUseCase entreprises,
                                ProducteurDocuments documents) {
        this.contexte = contexte;
        this.entreprises = entreprises;
        this.documents = documents;
    }

    /**
     * @param pays pour voir les champs d'un autre pays avant d'enregistrer : le formulaire se recharge quand
     *             on change de pays, sans rien enregistrer
     */
    @GetMapping
    public String entreprise(@RequestParam(required = false) String pays, HttpServletRequest requete, Model model) {
        Acces acces = contexte.exiger(requete, PermissionsNoyau.ENTREPRISE_CONSULTER);
        Entreprise entreprise = entreprises.consulter(acces.tenantId());
        Pays affiche = pays == null ? entreprise.pays() : Pays.parCode(pays).orElse(entreprise.pays());
        model.addAttribute("entreprise", entreprise);
        model.addAttribute("pays", affiche);
        model.addAttribute("paysChange", affiche != entreprise.pays());
        // Si on regarde un autre pays que l'enregistré, ses champs sont vides : rien n'est repris d'un pays à l'autre.
        model.addAttribute("champs", affiche.champs().stream()
                .map(c -> new Champ(c.code(), c.libelle(), c.aide(),
                        affiche == entreprise.pays() ? entreprise.identifiant(c.code()) : ""))
                .toList());
        model.addAttribute("tousLesPays", Pays.values());
        model.addAttribute("formes", FormeJuridique.values());
        model.addAttribute("devises", Devise.values());
        model.addAttribute("manques", entreprise.manques());
        model.addAttribute("peutModifier", acces.peut(PermissionsNoyau.ENTREPRISE_MODIFIER));
        return "kauris/entreprise";
    }

    @PostMapping
    public String enregistrer(@RequestParam Map<String, String> champs,
                              @RequestParam(required = false) Set<String> autresDevises,
                              HttpServletRequest requete, RedirectAttributes flash) {
        Acces acces = contexte.exiger(requete);
        Map<String, String> identifiants = new LinkedHashMap<>();
        champs.forEach((nom, valeur) -> {
            if (nom.startsWith(IDENTIFIANT)) {
                identifiants.put(nom.substring(IDENTIFIANT.length()), valeur);
            }
        });
        try {
            entreprises.enregistrer(acces, new Saisie(champs.get("denomination"), champs.get("sigle"),
                    champs.get("forme"), champs.get("capital"), champs.get("pays"), champs.get("ville"),
                    champs.get("adresse"), champs.get("boitePostale"), champs.get("telephone"), champs.get("email"),
                    champs.get("siteWeb"), identifiants, champs.get("devise"), autresDevises, champs.get("banque"),
                    champs.get("titulaireCompte"), champs.get("numeroCompte"), champs.get("bic"),
                    champs.get("agence"), champs.get("mentions")));
            flash.addFlashAttribute("succes", "Paramètres de l'entreprise enregistrés.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:" + PAGE;
    }

    // ------------------------------------------------------------------ logo

    @PostMapping("/logo")
    public String definirLogo(@RequestParam("fichier") MultipartFile fichier, HttpServletRequest requete,
                              RedirectAttributes flash) throws IOException {
        Acces acces = contexte.exiger(requete);
        try {
            if (fichier.getSize() > Logo.TAILLE_MAX) {
                throw new ErreurMetier("Logo trop lourd : 512 Ko au plus.");
            }
            entreprises.definirLogo(acces, fichier.getBytes());
            flash.addFlashAttribute("succes", "Logo enregistré.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:" + PAGE;
    }

    @PostMapping("/logo/retirer")
    public String retirerLogo(HttpServletRequest requete, RedirectAttributes flash) {
        Acces acces = contexte.exiger(requete);
        try {
            entreprises.retirerLogo(acces);
            flash.addFlashAttribute("succes", "Logo retiré.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:" + PAGE;
    }

    /** Le logo de l'organisation courante, pour tout membre : il figure sur les documents qu'il consulte. */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(HttpServletRequest requete) {
        Acces acces = contexte.exiger(requete);
        return entreprises.logo(acces.tenantId())
                .map(logo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(logo.typeMime()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .body(logo.contenu()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------ thème des documents

    @GetMapping("/theme")
    public String theme(HttpServletRequest requete, Model model) {
        Acces acces = contexte.exiger(requete, PermissionsNoyau.ENTREPRISE_CONSULTER);
        model.addAttribute("theme", entreprises.consulter(acces.tenantId()).theme());
        model.addAttribute("modeles", ThemeDocument.Modele.values());
        model.addAttribute("polices", documents.polices().toutes());
        model.addAttribute("tailles", java.util.stream.IntStream
                .rangeClosed(ThemeDocument.TAILLE_MIN, ThemeDocument.TAILLE_MAX).boxed().toList());
        model.addAttribute("positions", ThemeDocument.PositionLogo.values());
        model.addAttribute("styles", ThemeDocument.StyleTitre.values());
        Map<String, Map<String, String>> valeurs = new LinkedHashMap<>();
        for (ThemeDocument.Modele m : ThemeDocument.Modele.values()) {
            if (m != ThemeDocument.Modele.PERSONNALISE) {
                valeurs.put(m.name(), valeurs(ThemeDocument.du(m)));
            }
        }
        model.addAttribute("valeursModeles", valeurs);
        model.addAttribute("peutModifier", acces.peut(PermissionsNoyau.ENTREPRISE_MODIFIER));
        return "kauris/theme";
    }

    private static Map<String, String> valeurs(ThemeDocument t) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("policeTitres", t.policeTitres());
        v.put("policeTexte", t.policeTexte());
        v.put("taille", String.valueOf(t.taille()));
        v.put("couleurPrincipale", t.couleurPrincipale().toLowerCase());
        v.put("couleurEntetes", t.couleurEntetes().toLowerCase());
        v.put("couleurTexte", t.couleurTexte().toLowerCase());
        v.put("logo", t.logo().name());
        v.put("titre", t.titre().name());
        return v;
    }

    @PostMapping("/theme")
    public String enregistrerTheme(@RequestParam Map<String, String> champs, HttpServletRequest requete,
                                   RedirectAttributes flash) {
        Acces acces = contexte.exiger(requete);
        try {
            ThemeDocument theme = entreprises.definirTheme(acces, saisieTheme(champs));
            flash.addFlashAttribute("succes", "Thème « " + theme.modele().libelle() + " » enregistré.");
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:" + PAGE + "/theme";
    }

    private static SaisieTheme saisieTheme(Map<String, String> c) {
        return new SaisieTheme(c.get("modele"), c.get("policeTitres"), c.get("policeTexte"), c.get("taille"),
                c.get("couleurPrincipale"), c.get("couleurEntetes"), c.get("couleurTexte"), c.get("logo"),
                c.get("titre"));
    }

    // ------------------------------------------------------------------ document d'exemple

    /**
     * Un document d'exemple sur l'ossature commune. Avec des réglages dans l'adresse (éditeur de thème), il
     * montre ce thème sans l'enregistrer ; des réglages incorrects montrent le thème enregistré.
     */
    @GetMapping("/apercu")
    public String apercu(@RequestParam Map<String, String> reglages, HttpServletRequest requete, Model model) {
        Acces acces = contexte.exiger(requete, PermissionsNoyau.ENTREPRISE_CONSULTER);
        String lienPdf = requete.getContextPath() + PAGE + "/apercu.pdf"
                + (requete.getQueryString() == null ? "" : "?" + requete.getQueryString());
        model.addAllAttributes(exemple(acces, reglages, lienPdf, false));
        return "kauris/apercu-document";
    }

    @GetMapping("/apercu.pdf")
    public ResponseEntity<byte[]> apercuPdf(@RequestParam Map<String, String> reglages, HttpServletRequest requete) {
        Acces acces = contexte.exiger(requete, PermissionsNoyau.ENTREPRISE_CONSULTER);
        byte[] pdf = documents.pdf("kauris/apercu-document", exemple(acces, reglages, null, true));
        return enPdf(pdf, ProducteurDocuments.nomDeFichier("document d'exemple", null));
    }

    private Map<String, Object> exemple(Acces acces, Map<String, String> reglages, String lienPdf, boolean pdf) {
        Entreprise entreprise = entreprises.consulter(acces.tenantId());
        ThemeDocument theme = entreprise.theme();
        if (reglages.containsKey("modele")) {
            try {
                theme = entreprises.lire(saisieTheme(reglages));
            } catch (ErreurMetier incorrect) {
                // aperçu du thème enregistré
            }
        }
        Habillage habillage = documents.habillage(entreprise, theme, entreprises.logo(acces.tenantId()),
                "Document d'exemple", "", lienPdf, pdf);
        Map<String, Object> variables = new HashMap<>();
        variables.put("habillage", habillage);
        variables.put("cartouche", Cartouche.de(
                Cartouche.c("Entité", entreprise.raisonSociale()),
                Cartouche.c("Établissement", acces.organisation()),
                Cartouche.c(entreprise.pays().identifiantFiscal().sigle(),
                        entreprise.identifiant(entreprise.pays().identifiantFiscal().code())),
                Cartouche.c("RCCM", entreprise.identifiant("rccm")),
                Cartouche.c("Pays", entreprise.pays().nom()),
                Cartouche.c("Monnaie", entreprise.devise().code() + " — " + entreprise.devise().nom())));
        variables.put("etabliPar", acces.nomMembre());
        variables.put("visas", List.of("Visa du comptable", "Visa du dirigeant"));
        return variables;
    }

    /** Un PDF à ouvrir dans le navigateur, avec un nom de fichier propre s'il est enregistré. */
    public static ResponseEntity<byte[]> enPdf(byte[] pdf, String nomDeFichier) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(nomDeFichier).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }
}
