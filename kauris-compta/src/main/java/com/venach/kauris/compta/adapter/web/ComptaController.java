package com.venach.kauris.compta.adapter.web;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.compta.application.PermissionsCompta;
import com.venach.kauris.compta.application.PortsCompta;
import com.venach.kauris.compta.domain.Compte;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.compta.domain.Journal;
import com.venach.kauris.compta.domain.LigneEcriture;
import com.venach.kauris.compta.domain.Parametres;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.venach.kauris.compta.application.PermissionsCompta.CONSULTER;
import static com.venach.kauris.compta.application.PermissionsCompta.SAISIR;
import static com.venach.kauris.compta.application.PermissionsCompta.VALIDER;

/**
 * La comptabilité au quotidien : accueil, écritures (liste, saisie, validation, contre-passation), recettes et
 * dépenses du système minimal, opérations reçues des autres modules à revoir.
 */
@Controller
@RequestMapping(ComptaController.PAGE)
public class ComptaController {

    public static final String PAGE = "/compta";

    /** Lignes vides proposées dans le formulaire d'écriture, en plus des lignes saisies. */
    static final int LIGNES_VIDES = 4;

    private final ContexteCourant contexte;
    private final ComptaService compta;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public ComptaController(ContexteCourant contexte, ComptaService compta, GererEntrepriseUseCase entreprises,
                            ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.compta = compta;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    /** Ce que tous les écrans de la comptabilité reçoivent. */
    static FormatsCompta commun(Model model, Acces a, GererEntrepriseUseCase entreprises, ComptaService compta,
                                String onglet) {
        FormatsCompta f = new FormatsCompta(entreprises.consulter(a.tenantId()).devise());
        Parametres p = compta.parametres(a);
        model.addAttribute("f", f);
        model.addAttribute("onglet", onglet);
        model.addAttribute("droits", PermissionsCompta.Droits.de(a));
        model.addAttribute("parametres", p);
        model.addAttribute("aRevoir", compta.aRevoir(a).size());
        model.addAttribute("aujourdhui", compta.aujourdHui());
        return f;
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

    // ================================================================== accueil

    @GetMapping
    public String accueil(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "accueil");
        model.addAttribute("resume", compta.resume(a));
        model.addAttribute("dernieres", compta.ecritures(a, new PortsCompta.Filtre(null, null, null, null, null,
                10)));
        return "compta/accueil";
    }

    // ================================================================== écritures

    @GetMapping("/ecritures")
    public String ecritures(@RequestParam(defaultValue = "") String journal, @RequestParam(defaultValue = "") String du,
                            @RequestParam(defaultValue = "") String au, @RequestParam(defaultValue = "") String statut,
                            @RequestParam(defaultValue = "") String q, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "ecritures");
        LocalDate debut = FormatsCompta.lireDate(du, "Du", null);
        LocalDate fin = FormatsCompta.lireDate(au, "Au", null);
        Ecriture.Statut s = statut.isBlank() ? null : FormatsCompta.lireChoix(Ecriture.Statut.class, statut, "Statut");
        model.addAttribute("ecritures", compta.ecritures(a, new PortsCompta.Filtre(journal, debut, fin, s, q, 500)));
        model.addAttribute("journaux", compta.journaux(a));
        model.addAttribute("filtre", Map.of("journal", journal, "du", du, "au", au, "statut", statut, "q", q));
        return "compta/ecritures";
    }

    @PostMapping("/brouillard/valider")
    public String validerBrouillard(@RequestParam(defaultValue = "") String jusqua, HttpServletRequest requete,
                                    RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, VALIDER);
        return agir(flash, () -> {
            int n = compta.validerBrouillard(a, FormatsCompta.lireDate(jusqua, "Jusqu'au", compta.aujourdHui()));
            flash.addFlashAttribute("succes", n == 0 ? "Aucune écriture à valider."
                    : n + " écriture(s) validée(s) et numérotée(s).");
            return "redirect:" + PAGE + "/ecritures";
        }, PAGE + "/ecritures");
    }

    @GetMapping("/ecritures/nouvelle")
    public String nouvelle(@RequestParam(defaultValue = "OD") String journal, HttpServletRequest requete,
                           Model model) {
        Acces a = contexte.exiger(requete, SAISIR);
        commun(model, a, entreprises, compta, "ecritures");
        formulaire(model, a, null);
        model.addAttribute("journalPropose", journal);
        return "compta/ecriture";
    }

    private void formulaire(Model model, Acces a, Ecriture e) {
        model.addAttribute("e", e);
        model.addAttribute("journaux", compta.journaux(a).stream().filter(j -> !j.aNouveaux()).toList());
        model.addAttribute("comptes", compta.plan(a).stream().filter(Compte::actif).toList());
        int lignes = (e == null ? 0 : e.lignes().size()) + LIGNES_VIDES;
        model.addAttribute("lignes", Math.max(lignes, 6));
    }

    @PostMapping("/ecritures")
    public String saisir(@RequestParam Map<String, String> c, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        return agir(flash, () -> {
            Ecriture e = compta.saisir(a, c.get("journal"), FormatsCompta.lireDate(c.get("date"), "Date", null),
                    c.get("piece"), c.get("libelle"), lireLignes(c));
            flash.addFlashAttribute("succes", "Écriture enregistrée au brouillard. Vérifiez-la puis validez-la.");
            return "redirect:" + PAGE + "/ecritures/" + e.id();
        }, PAGE + "/ecritures/nouvelle?journal=" + c.getOrDefault("journal", "OD"));
    }

    /**
     * Les lignes saisies : {@code ligne_compte_N}, {@code ligne_tiers_N} (nom), {@code ligne_libelle_N},
     * {@code ligne_debit_N}, {@code ligne_credit_N} ; une ligne sans compte ni montant est ignorée.
     */
    static List<LigneEcriture> lireLignes(Map<String, String> c) {
        List<LigneEcriture> l = new ArrayList<>();
        for (int i = 0; i < Ecriture.LIGNES_MAX + LIGNES_VIDES; i++) {
            String compte = c.getOrDefault("ligne_compte_" + i, "").strip();
            String debit = c.getOrDefault("ligne_debit_" + i, "").strip();
            String credit = c.getOrDefault("ligne_credit_" + i, "").strip();
            if (compte.isEmpty() && debit.isEmpty() && credit.isEmpty()) {
                continue;
            }
            // « 571000 Caisse » choisi dans la liste : le numéro est avant l'espace.
            int espace = compte.indexOf(' ');
            String numero = espace > 0 ? compte.substring(0, espace) : compte;
            String champ = "Ligne " + (i + 1);
            l.add(new LigneEcriture(numero, "", c.getOrDefault("ligne_tiers_" + i, ""),
                    c.getOrDefault("ligne_libelle_" + i, ""), FormatsCompta.lireMontant(debit, champ + ", débit"),
                    FormatsCompta.lireMontant(credit, champ + ", crédit"), ""));
        }
        return l;
    }

    @GetMapping("/ecritures/{id}")
    public String ecriture(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "ecritures");
        Ecriture e = compta.ecriture(a, id);
        if (!e.validee() && a.peut(SAISIR)) {
            formulaire(model, a, e);
            model.addAttribute("journalPropose", e.journal());
        } else {
            model.addAttribute("e", e);
        }
        model.addAttribute("libelles", compta.libelles(a.tenantId()));
        model.addAttribute("inverse", compta.inverseDe(a, id).orElse(null));
        model.addAttribute("origine", e.inverseDe() == null ? null : compta.ecriture(a, e.inverseDe()));
        return "compta/ecriture";
    }

    @PostMapping("/ecritures/{id}")
    public String corriger(@PathVariable UUID id, @RequestParam Map<String, String> c, HttpServletRequest requete,
                           RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        return agir(flash, () -> {
            compta.corriger(a, id, c.get("journal"), FormatsCompta.lireDate(c.get("date"), "Date", null),
                    c.get("piece"), c.get("libelle"), lireLignes(c));
            flash.addFlashAttribute("succes", "Écriture corrigée.");
            return "redirect:" + PAGE + "/ecritures/" + id;
        }, PAGE + "/ecritures/" + id);
    }

    @PostMapping("/ecritures/{id}/valider")
    public String valider(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, VALIDER);
        return agir(flash, () -> {
            Ecriture e = compta.valider(a, id);
            flash.addFlashAttribute("succes", "Écriture validée : n° " + e.numero() + ".");
            return "redirect:" + PAGE + "/ecritures/" + id;
        }, PAGE + "/ecritures/" + id);
    }

    @PostMapping("/ecritures/{id}/supprimer")
    public String supprimer(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        return agir(flash, () -> {
            compta.supprimer(a, id);
            flash.addFlashAttribute("succes", "Brouillard supprimé.");
            return "redirect:" + PAGE + "/ecritures";
        }, PAGE + "/ecritures/" + id);
    }

    @PostMapping("/ecritures/{id}/contre-passer")
    public String contrePasser(@PathVariable UUID id, @RequestParam(defaultValue = "") String date,
                               @RequestParam(defaultValue = "") String motif, HttpServletRequest requete,
                               RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, VALIDER);
        return agir(flash, () -> {
            Ecriture e = compta.contrePasser(a, id, FormatsCompta.lireDate(date, "Date", compta.aujourdHui()), motif);
            flash.addFlashAttribute("succes", "Écriture contre-passée par " + e.numero() + ". Saisissez si besoin "
                    + "l'écriture correcte.");
            return "redirect:" + PAGE + "/ecritures/" + e.id();
        }, PAGE + "/ecritures/" + id);
    }

    /** Une écriture en PDF (pièce comptable). */
    @GetMapping("/ecritures/{id}.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Ecriture e = compta.ecriture(a, id);
        Map<String, String> libelles = compta.libelles(a.tenantId());
        List<List<Object>> lignes = new ArrayList<>();
        for (LigneEcriture l : e.lignes()) {
            lignes.add(List.of(l.compte(), libelles.getOrDefault(l.compte(), ""), l.tiersNom(), l.libelle(),
                    l.debit() == 0 ? "" : l.debit(), l.credit() == 0 ? "" : l.credit()));
        }
        Tableau t = new Tableau("Pièce comptable " + (e.validee() ? e.numero() : "(brouillard)"),
                e.libelle() + " · " + e.journal() + " · " + e.date(), List.of("Compte", "Intitulé", "Tiers",
                "Libellé", "Débit", "Crédit"), lignes, List.of("Total", "", "", "", e.total(), e.total()));
        return pdfDe(a, t, "Pièce " + (e.validee() ? e.numero() : e.id().toString().substring(0, 8)));
    }

    // ================================================================== recettes et dépenses (système minimal)

    @GetMapping("/recettes-depenses")
    public String recettesDepenses(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, SAISIR);
        commun(model, a, entreprises, compta, "ecritures");
        List<Compte> plan = compta.plan(a).stream().filter(Compte::actif).toList();
        model.addAttribute("tresorerie", plan.stream().filter(x -> x.numero().startsWith("5")).toList());
        model.addAttribute("recettes", plan.stream().filter(x -> x.numero().startsWith("7")
                || x.numero().startsWith("4") || x.numero().startsWith("1")).toList());
        model.addAttribute("depenses", plan.stream().filter(x -> x.numero().startsWith("6")
                || x.numero().startsWith("2") || x.numero().startsWith("4")).toList());
        return "compta/recettes-depenses";
    }

    @PostMapping("/recettes-depenses")
    public String recetteOuDepense(@RequestParam Map<String, String> c, HttpServletRequest requete,
                                   RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        return agir(flash, () -> {
            boolean recette = "RECETTE".equals(c.get("sens"));
            Ecriture e = compta.recetteOuDepense(a, recette, FormatsCompta.lireDate(c.get("date"), "Date",
                            compta.aujourdHui()), c.get("tresorerie"), recette ? c.get("recette") : c.get("depense"),
                    FormatsCompta.lireMontant(c.get("montant"), "Montant"), c.get("libelle"), c.get("piece"));
            flash.addFlashAttribute("succes", (recette ? "Recette" : "Dépense") + " enregistrée au brouillard du "
                    + "journal de trésorerie.");
            return "redirect:" + PAGE + "/recettes-depenses?derniere=" + e.id();
        }, PAGE + "/recettes-depenses");
    }

    // ================================================================== opérations reçues à revoir

    @GetMapping("/operations")
    public String operations(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "operations");
        model.addAttribute("operations", compta.aRevoir(a));
        return "compta/operations";
    }

    @PostMapping("/operations/{id}/reessayer")
    public String reessayer(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        return agir(flash, () -> {
            var o = compta.reessayer(a, id);
            flash.addFlashAttribute(o.statut().name().startsWith("A_") || o.statut().name().endsWith("REVOIR")
                    ? "erreur" : "succes", o.message().isEmpty() ? "Opération comptabilisée." : o.message());
            return "redirect:" + PAGE + "/operations";
        }, PAGE + "/operations");
    }

    @PostMapping("/operations/{id}/ignorer")
    public String ignorer(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, VALIDER);
        return agir(flash, () -> {
            compta.ignorer(a, id);
            flash.addFlashAttribute("succes", "Opération écartée : elle ne sera pas comptabilisée.");
            return "redirect:" + PAGE + "/operations";
        }, PAGE + "/operations");
    }

    // ================================================================== documents

    /** Un tableau en PDF, sur le papier de l'entreprise. */
    ResponseEntity<byte[]> pdfDe(Acces a, Tableau t, String nom) {
        return ComptaController.enPdf(pdfTableau(producteur, a, t, compta.aujourdHui()),
                ProducteurDocuments.nomDeFichier(nom, ""));
    }

    static byte[] pdfTableau(ProducteurDocuments producteur, Acces a, Tableau t, LocalDate edition) {
        Habillage h = producteur.habillage(a.tenantId(), t.titre(), t.precision(), null, true);
        FormatsCompta f = new FormatsCompta(h.entreprise().devise());
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("t", t);
        v.put("numeriques", numeriques(t));
        v.put("cartouche", Cartouche.de(Cartouche.c("Édité le", f.jour(edition)),
                Cartouche.c("Lignes", String.valueOf(t.lignes().size()))));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("Le comptable", "La direction"));
        v.put("lignes", t.lignes().stream().map(l -> textes(l, f)).toList());
        v.put("total", t.total() == null ? null : textes(t.total(), f));
        return producteur.pdf("compta/doc-tableau", v);
    }

    private static List<String> textes(List<Object> cases, FormatsCompta f) {
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

    static ResponseEntity<byte[]> enPdf(byte[] contenu, String nom) {
        return com.venach.kauris.kernel.ui.entreprise.EntrepriseController.enPdf(contenu,
                nom.endsWith(".pdf") ? nom : nom + ".pdf");
    }

    static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, String type) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"").body(contenu);
    }

    /** Un tableau dans le format demandé : csv, xlsx ou pdf. */
    static ResponseEntity<byte[]> exporter(ProducteurDocuments producteur, Acces a, Tableau t, String nom,
                                           String format, LocalDate edition) {
        String base = ProducteurDocuments.nomDeFichier(nom, "").replaceAll("\\.pdf$", "");
        return switch (format) {
            case "csv" -> fichier(t.csv(), base + ".csv", "text/csv; charset=UTF-8");
            case "xlsx" -> fichier(t.xlsx(), base + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "pdf" -> enPdf(pdfTableau(producteur, a, t, edition), base + ".pdf");
            default -> throw new com.venach.kauris.kernel.commun.ElementIntrouvable("Format inconnu.");
        };
    }

    /** Les journaux, pour les listes de choix (sans les à-nouveaux). */
    static List<Journal> journauxDeSaisie(List<Journal> j) {
        return j.stream().filter(x -> !x.aNouveaux()).toList();
    }
}
