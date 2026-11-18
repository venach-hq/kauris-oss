package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.MontantEnLettres;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.entreprise.EntrepriseController;
import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.application.PermissionsPaie;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Element;
import com.venach.kauris.paie.domain.ModelesRecommandes;
import com.venach.kauris.paie.domain.ReglesPaie;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.TypeElement;
import com.venach.kauris.paie.domain.Variables;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.venach.kauris.paie.application.PermissionsPaie.CONSULTER;

/**
 * La paie du mois : ouvrir un mois, saisir les absences, primes et avances, voir les bulletins, clôturer, préparer
 * le virement, télécharger le livre de paie et les déclarations.
 */
@Controller
@RequestMapping(PaieController.PAGE)
public class PaieController {

    public static final String PAGE = "/paie";

    /** Lignes d'éléments proposées dans un formulaire (en plus de celles déjà saisies). */
    static final int LIGNES_VIDES = 3;

    private final ContexteCourant contexte;
    private final PaieService paie;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public PaieController(ContexteCourant contexte, PaieService paie, GererEntrepriseUseCase entreprises,
                          ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.paie = paie;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    /** Ce que tous les écrans de la paie reçoivent. */
    static FormatsPaie commun(Model model, Acces a, GererEntrepriseUseCase entreprises, PaieService paie,
                              String onglet) {
        FormatsPaie f = new FormatsPaie(entreprises.consulter(a.tenantId()).devise());
        model.addAttribute("f", f);
        model.addAttribute("onglet", onglet);
        model.addAttribute("droits", PermissionsPaie.Droits.de(a));
        model.addAttribute("alertes", paie.alertes(a));
        model.addAttribute("pays", paie.pays(a.tenantId()).orElse(null));
        return f;
    }

    /**
     * Les heures supplémentaires saisies : champs {@code heure_maj_N} (« libellé|taux » d'une majoration des règles,
     * ou vide), {@code heure_nombre_N} ; une ligne sans nombre est ignorée.
     */
    static List<Variables.Heures> lireHeures(Map<String, String> c) {
        List<Variables.Heures> h = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String nombre = c.getOrDefault("heure_nombre_" + i, "").strip();
            String maj = c.getOrDefault("heure_maj_" + i, "").strip();
            if (nombre.isEmpty()) {
                continue;
            }
            int barre = maj.lastIndexOf('|');
            if (barre < 0) {
                throw new ErreurMetier("Heures supplémentaires : choisissez la majoration.");
            }
            h.add(new Variables.Heures(maj.substring(0, barre), FormatsPaie.lireDecimal(maj.substring(barre + 1),
                    "Majoration"), FormatsPaie.lireDecimal(nombre, "Nombre d'heures")));
        }
        return h;
    }

    /** Une erreur prévue devient un message à l'écran ; les autres (accès, introuvable) suivent leur cours. */
    static String agir(RedirectAttributes flash, Supplier<String> action, String retourEnErreur) {
        try {
            return action.get();
        } catch (ErreurMetier e) {
            if (e.getClass() != ErreurMetier.class) {
                throw e;
            }
            flash.addFlashAttribute("erreur", e.getMessage());
            return "redirect:" + retourEnErreur;
        }
    }

    /**
     * Les éléments saisis dans un formulaire : champs {@code element_libelle_N}, {@code element_type_N},
     * {@code element_montant_N} ; une ligne sans libellé ni montant est ignorée.
     */
    static List<Element> lireElements(Map<String, String> c) {
        List<Element> e = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String libelle = c.getOrDefault("element_libelle_" + i, "").strip();
            String montant = c.getOrDefault("element_montant_" + i, "").strip();
            if (!c.containsKey("element_libelle_" + i) && !c.containsKey("element_montant_" + i)) {
                continue;
            }
            if (libelle.isEmpty() && montant.isEmpty()) {
                continue;
            }
            String champ = libelle.isEmpty() ? "Élément " + (i + 1) : libelle;
            e.add(new Element(libelle, FormatsPaie.lireChoix(TypeElement.class, c.get("element_type_" + i), champ),
                    FormatsPaie.lireMontant(montant, champ)));
        }
        return e;
    }

    // ================================================================== accueil : les mois de paie

    @GetMapping
    public String accueil(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, paie, "mois");
        List<PeriodePaie> periodes = paie.periodes(a);
        Map<UUID, PaieService.Totaux> totaux = new HashMap<>();
        for (PeriodePaie p : periodes.stream().limit(12).toList()) {
            totaux.put(p.id(), paie.mois(a, p.id()).totaux());
        }
        model.addAttribute("periodes", periodes);
        model.addAttribute("totaux", totaux);
        model.addAttribute("moisPropose", periodes.isEmpty() ? paie.moisCourant()
                : periodes.get(0).mois().plusMonths(1).isAfter(paie.moisCourant().plusMonths(1))
                ? paie.moisCourant() : periodes.get(0).mois().plusMonths(1));
        model.addAttribute("salaries", paie.salaries(a).stream().filter(l -> l.contrat() != null).count());
        model.addAttribute("modele", paie.pays(a.tenantId()).flatMap(ModelesRecommandes::pour).orElse(null));
        return "paie/accueil";
    }

    @PostMapping("/mois")
    public String ouvrirMois(@RequestParam(defaultValue = "") String mois, HttpServletRequest requete,
                             RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            PeriodePaie p = paie.ouvrirMois(a, FormatsPaie.lireMois(mois, "Mois"));
            flash.addFlashAttribute("succes", "Paie de " + p.libelle() + " ouverte : vérifiez les bulletins, saisissez "
                    + "les absences et les primes du mois, puis clôturez.");
            return "redirect:" + PAGE + "/mois/" + p.id();
        }, PAGE);
    }

    // ================================================================== un mois

    @GetMapping("/mois/{id}")
    public String mois(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, paie, "mois");
        PaieService.Mois m = paie.mois(a, id);
        model.addAttribute("m", m);
        model.addAttribute("p", m.periode());
        model.addAttribute("problemes", paie.problemes(a, id));
        model.addAttribute("aVirer", PaieService.aVirer(m.bulletins()));
        model.addAttribute("versementDisponible", paie.versementDisponible(a));
        model.addAttribute("aujourdhui", paie.aujourdHui());
        model.addAttribute("organismes", ExportsPaie.organismesSociaux(m.bulletins()));
        model.addAttribute("impot", m.regles() == null ? "Impôt" : m.regles().contenu().impot().nom());
        return "paie/mois";
    }

    @PostMapping("/mois/{id}/clore")
    public String clore(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.CLOTURER);
        return agir(flash, () -> {
            PeriodePaie p = paie.clore(a, id);
            flash.addFlashAttribute("succes", "Paie de " + p.libelle() + " clôturée : les bulletins sont numérotés et "
                    + "ne bougeront plus. Préparez le virement et téléchargez les déclarations.");
            return "redirect:" + PAGE + "/mois/" + id;
        }, PAGE + "/mois/" + id);
    }

    @PostMapping("/mois/{id}/rouvrir")
    public String rouvrir(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.CLOTURER);
        return agir(flash, () -> {
            PeriodePaie p = paie.rouvrir(a, id);
            flash.addFlashAttribute("succes", "Paie de " + p.libelle() + " rouverte : les bulletins sont de nouveau "
                    + "des brouillons.");
            return "redirect:" + PAGE + "/mois/" + id;
        }, PAGE + "/mois/" + id);
    }

    @PostMapping("/mois/{id}/payer")
    public String payer(@PathVariable UUID id, @RequestParam(defaultValue = "") String date,
                        HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.CLOTURER);
        return agir(flash, () -> {
            PeriodePaie p = paie.marquerPaye(a, id, FormatsPaie.lireDate(date, "Date du paiement"));
            flash.addFlashAttribute("succes", "Salaires de " + p.libelle() + " marqués comme payés.");
            return "redirect:" + PAGE + "/mois/" + id;
        }, PAGE + "/mois/" + id);
    }

    @PostMapping("/mois/{id}/verser")
    public String verser(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.CLOTURER);
        return agir(flash, () -> {
            flash.addFlashAttribute("succes", paie.verser(a, id));
            return "redirect:" + PAGE + "/mois/" + id;
        }, PAGE + "/mois/" + id);
    }

    // ================================================================== un salarié dans le mois

    /** Le bulletin du salarié pour ce mois, avec la saisie des absences, primes et avances (mois ouvert). */
    @GetMapping("/mois/{id}/salaries/{salarieId}")
    public String salarieDuMois(@PathVariable UUID id, @PathVariable UUID salarieId, HttpServletRequest requete,
                                Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, paie, "mois");
        PaieService.Mois m = paie.mois(a, id);
        Salarie s = paie.salarie(a, salarieId);
        Variables v = paie.variables(a, id, salarieId);
        model.addAttribute("p", m.periode());
        model.addAttribute("s", s);
        model.addAttribute("v", v);
        model.addAttribute("b", m.bulletins().stream().filter(x -> x.salarieId().equals(salarieId)).findFirst()
                .orElse(null));
        model.addAttribute("probleme", paie.problemes(a, id).stream().filter(x -> x.salarie().id().equals(salarieId))
                .map(PaieService.Probleme::message).findFirst().orElse(null));
        model.addAttribute("types", TypeElement.values());
        model.addAttribute("lignes", v.elements().size() + LIGNES_VIDES);
        ReglesPaie regles = m.regles();
        model.addAttribute("regles", regles);
        // Les majorations des règles, plus celles déjà saisies qui n'y figurent plus (règles corrigées depuis).
        List<ReglesPaie.Majoration> majorations = new ArrayList<>(regles == null ? List.of()
                : regles.contenu().heures().majorations());
        v.heures().stream().filter(h -> majorations.stream().noneMatch(x -> x.libelle().equals(h.libelle())))
                .forEach(h -> majorations.add(new ReglesPaie.Majoration(h.libelle(), h.majoration())));
        model.addAttribute("majorations", majorations);
        model.addAttribute("lignesHeures", v.heures().size() + LIGNES_VIDES);
        model.addAttribute("impot", regles == null ? "Impôt" : regles.contenu().impot().nom());
        // Le salarié précédent et le suivant, pour passer d'un bulletin à l'autre.
        List<UUID> ordre = m.bulletins().stream().map(Bulletin::salarieId).toList();
        int i = ordre.indexOf(salarieId);
        model.addAttribute("precedent", i > 0 ? ordre.get(i - 1) : null);
        model.addAttribute("suivant", i >= 0 && i < ordre.size() - 1 ? ordre.get(i + 1) : null);
        return "paie/salarie-mois";
    }

    @PostMapping("/mois/{id}/salaries/{salarieId}/variables")
    public String enregistrerVariables(@PathVariable UUID id, @PathVariable UUID salarieId,
                                       @RequestParam Map<String, String> c, HttpServletRequest requete,
                                       RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        String ici = PAGE + "/mois/" + id + "/salaries/" + salarieId;
        return agir(flash, () -> {
            paie.enregistrerVariables(a, id, salarieId, FormatsPaie.lireEntier(c.get("joursAbsence"),
                    "Jours d'absence", 0), lireHeures(c), lireElements(c));
            flash.addFlashAttribute("succes", "Éléments du mois enregistrés : le bulletin est recalculé.");
            return "redirect:" + ici;
        }, ici);
    }

    /** Le bulletin de paie, à l'écran (imprimable). */
    @GetMapping("/mois/{id}/salaries/{salarieId}/bulletin")
    public String bulletin(@PathVariable UUID id, @PathVariable UUID salarieId, HttpServletRequest requete,
                           Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        model.addAllAttributes(documentBulletin(a, paie.bulletinDe(a, id, salarieId), requete.getContextPath() + PAGE
                + "/mois/" + id + "/salaries/" + salarieId + "/bulletin.pdf", false));
        return "paie/doc-bulletin";
    }

    @GetMapping("/mois/{id}/salaries/{salarieId}/bulletin.pdf")
    public ResponseEntity<byte[]> bulletinPdf(@PathVariable UUID id, @PathVariable UUID salarieId,
                                              HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Bulletin b = paie.bulletinDe(a, id, salarieId);
        PeriodePaie p = paie.periode(a, id);
        return EntrepriseController.enPdf(producteur.pdf("paie/doc-bulletin", documentBulletin(a, b, null, true)),
                ProducteurDocuments.nomDeFichier("Bulletin " + b.nomSalarie(), p.mois().toString()));
    }

    private Map<String, Object> documentBulletin(Acces a, Bulletin b, String lienPdf, boolean pdf) {
        PeriodePaie p = paie.periode(a, b.periodeId());
        ContratTravail c = paie.contrat(a, b.contratId());
        Habillage h = producteur.habillage(a.tenantId(), "Bulletin de paie", p.libelle()
                + (b.brouillon() ? " · brouillon" : " · n° " + b.numero()), lienPdf, pdf);
        FormatsPaie f = new FormatsPaie(h.entreprise().devise());
        List<Cartouche.Case> cases = new ArrayList<>(List.of(
                Cartouche.c("Période", p.libelle()),
                Cartouche.c("Bulletin n°", b.brouillon() ? "brouillon" : b.numero()),
                Cartouche.c("Matricule", b.matricule()),
                Cartouche.c("Embauche", f.jour(c.debut())),
                Cartouche.c("Emploi", b.poste()),
                Cartouche.c("Catégorie", b.categorie()),
                Cartouche.c("N° sécurité sociale", b.numeroSocial()),
                Cartouche.c("Situation", b.situation() + (b.enfants() > 0 ? ", " + b.enfants() + " enfant(s)" : ""))));
        if (!b.famille().isEmpty()) {
            cases.add(Cartouche.c("Charges de famille", b.famille()));
        }
        if (b.regime() != com.venach.kauris.paie.domain.RegimeFiscal.NORMAL) {
            cases.add(Cartouche.c("Régime", b.regime().libelle()));
        }
        cases.add(Cartouche.c("Jours payés", b.joursPayes() + " / " + CalculPaie.JOURS_DU_MOIS));
        cases.add(Cartouche.c("Salaire de base", f.nombre(b.salaireBase())));
        cases.add(Cartouche.c("Paiement", b.modePaiement().libelle()));
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", f);
        v.put("b", b);
        v.put("r", b.resultat());
        v.put("pays", p.pays());
        v.put("brouillon", b.brouillon());
        v.put("cartouche", new Cartouche(cases));
        v.put("netEnLettres", MontantEnLettres.montant(b.net(), h.entreprise().devise()));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("L'employeur", "Le salarié"));
        return v;
    }

    // ================================================================== exports

    /** {@code quoi} : livre, sociale, fiscale, virements, ecritures ; {@code format} : csv, xlsx, pdf. */
    @GetMapping("/mois/{id}/export/{quoi}.{format}")
    public ResponseEntity<byte[]> exporter(@PathVariable UUID id, @PathVariable String quoi,
                                           @PathVariable String format, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        PaieService.Mois m = paie.mois(a, id);
        PeriodePaie p = m.periode();
        Tableau t = switch (quoi) {
            case "livre" -> ExportsPaie.livre(p, m.bulletins());
            case "sociale" -> ExportsPaie.sociale(p, m.bulletins());
            case "fiscale" -> ExportsPaie.fiscale(p, m.bulletins());
            case "virements" -> ExportsPaie.virements(p, m.bulletins());
            case "ecritures" -> ExportsPaie.ecritures(p, m.bulletins());
            default -> throw new ElementIntrouvable("Export inconnu.");
        };
        String nom = ProducteurDocuments.nomDeFichier(t.titre(), p.mois().toString()).replaceAll("\\.pdf$", "");
        return switch (format) {
            case "csv" -> fichier(t.csv(), nom + ".csv", "text/csv; charset=UTF-8");
            case "xlsx" -> fichier(t.xlsx(), nom + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "pdf" -> EntrepriseController.enPdf(pdf(a, t, quoi.equals("virements")), nom + ".pdf");
            default -> throw new ElementIntrouvable("Format inconnu.");
        };
    }

    private byte[] pdf(Acces a, Tableau t, boolean ordreDeVirement) {
        Habillage h = producteur.habillage(a.tenantId(), t.titre(), t.precision(), null, true);
        FormatsPaie f = new FormatsPaie(h.entreprise().devise());
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("t", t);
        v.put("numeriques", numeriques(t));
        v.put("cartouche", Cartouche.de(Cartouche.c("Édité le", f.jour(paie.aujourdHui())),
                Cartouche.c("Lignes", String.valueOf(t.lignes().size()))));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", ordreDeVirement ? List.of("Signature autorisée et cachet") : List.of("Visa du responsable"));
        v.put("ordre", ordreDeVirement ? "Nous vous prions de bien vouloir virer, par le débit de notre compte, les "
                + "montants ci-dessous aux bénéficiaires indiqués." : null);
        v.put("lignes", t.lignes().stream().map(l -> textes(l, f)).toList());
        v.put("total", t.total() == null ? null : textes(t.total(), f));
        return producteur.pdf("paie/doc-tableau", v);
    }

    private static List<String> textes(List<Object> cases, FormatsPaie f) {
        return cases.stream().map(x -> x == null ? "" : x instanceof Number n ? f.nombre(n.longValue())
                : x.toString()).toList();
    }

    private static List<Boolean> numeriques(Tableau t) {
        List<Boolean> n = new ArrayList<>();
        for (int i = 0; i < t.colonnes().size(); i++) {
            int col = i;
            n.add(t.lignes().stream().anyMatch(l -> l.size() > col && l.get(col) instanceof Number)
                    || (t.total() != null && t.total().size() > col && t.total().get(col) instanceof Number));
        }
        return n;
    }

    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, String type) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"").body(contenu);
    }
}
