package com.venach.kauris.paie.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.application.PermissionsPaie;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.paie.domain.Avance;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.ModePaiement;
import com.venach.kauris.paie.domain.ModelesRecommandes;
import com.venach.kauris.paie.domain.RegimeFiscal;
import com.venach.kauris.paie.domain.ReglesPaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.SituationFamiliale;
import com.venach.kauris.paie.domain.TypeContrat;
import com.venach.kauris.paie.domain.TypeElement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.venach.kauris.paie.adapter.web.PaieController.PAGE;
import static com.venach.kauris.paie.adapter.web.PaieController.agir;
import static com.venach.kauris.paie.adapter.web.PaieController.commun;
import static com.venach.kauris.paie.application.PermissionsPaie.CONSULTER;

/** Les fiches des salariés, leurs contrats de travail et les réglages de la paie (taux et plafonds). */
@Controller
@RequestMapping(PAGE)
public class GestionPaieController {

    private final ContexteCourant contexte;
    private final PaieService paie;
    private final GererEntrepriseUseCase entreprises;

    public GestionPaieController(ContexteCourant contexte, PaieService paie, GererEntrepriseUseCase entreprises) {
        this.contexte = contexte;
        this.paie = paie;
        this.entreprises = entreprises;
    }

    // ================================================================== salariés

    @GetMapping("/salaries")
    public String salaries(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, paie, "salaries");
        model.addAttribute("lignes", paie.salaries(a));
        return "paie/salaries";
    }

    @GetMapping("/salaries/nouveau")
    public String nouveauSalarie(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        commun(model, a, entreprises, paie, "salaries");
        listesSalarie(model);
        model.addAttribute("s", null);
        return "paie/salarie";
    }

    private static void listesSalarie(Model model) {
        model.addAttribute("situations", SituationFamiliale.values());
        model.addAttribute("modes", ModePaiement.values());
        model.addAttribute("regimes", RegimeFiscal.values());
        model.addAttribute("typesAvance", Avance.Type.values());
    }

    @PostMapping("/salaries")
    public String creerSalarie(@RequestParam Map<String, String> c, HttpServletRequest requete,
                               RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            Salarie s = paie.enregistrerSalarie(a, null, saisieSalarie(c, true));
            flash.addFlashAttribute("succes", s.nomComplet() + " est enregistré(e) sous le matricule " + s.matricule()
                    + ". Ajoutez maintenant son contrat de travail.");
            return "redirect:" + PAGE + "/salaries/" + s.id() + "/contrats/nouveau";
        }, PAGE + "/salaries/nouveau");
    }

    @GetMapping("/salaries/{id}")
    public String salarie(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, paie, "salaries");
        listesSalarie(model);
        model.addAttribute("s", paie.salarie(a, id));
        model.addAttribute("contrats", paie.contratsDuSalarie(a, id));
        model.addAttribute("bulletins", paie.bulletinsDuSalarie(a, id));
        model.addAttribute("avances", paie.avances(a, id));
        model.addAttribute("moisCourant", paie.moisCourant());
        model.addAttribute("aujourdhui", paie.aujourdHui());
        return "paie/salarie";
    }

    @PostMapping("/salaries/{id}")
    public String corrigerSalarie(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                  HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            Salarie s = paie.enregistrerSalarie(a, id, saisieSalarie(c, "on".equals(c.get("actif"))));
            flash.addFlashAttribute("succes", "Fiche de " + s.nomComplet() + " enregistrée.");
            return "redirect:" + PAGE + "/salaries/" + id;
        }, PAGE + "/salaries/" + id);
    }

    private static Salarie.Saisie saisieSalarie(Map<String, String> c, boolean actif) {
        return new Salarie.Saisie(c.get("nom"), c.get("prenoms"), FormatsPaie.lireDate(c.get("naissance"),
                "Date de naissance"), c.get("telephone"), c.get("email"), c.get("adresse"), c.get("pieceIdentite"),
                c.get("numeroSocial"), FormatsPaie.lireChoix(SituationFamiliale.class, c.get("situation"),
                "Situation de famille"), FormatsPaie.lireEntier(c.get("enfants"), "Enfants à charge", 0),
                "on".equals(c.get("conjointACharge")), FormatsPaie.lireEntier(c.get("autresPersonnes"),
                "Autres personnes à charge", 0), "on".equals(c.get("expatrie")), "on".equals(c.get("cadre")),
                c.get("regime") == null || c.get("regime").isBlank() ? RegimeFiscal.NORMAL
                        : FormatsPaie.lireChoix(RegimeFiscal.class, c.get("regime"), "Régime fiscal"),
                FormatsPaie.lireChoix(ModePaiement.class, c.get("modePaiement"), "Mode de paiement"), c.get("banque"),
                c.get("compte"), actif, c.get("notes"));
    }

    // ================================================================== avances et prêts

    @PostMapping("/salaries/{id}/avances")
    public String accorderAvance(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                 HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            Avance x = paie.accorderAvance(a, id, FormatsPaie.lireChoix(Avance.Type.class, c.get("type"), "Type"),
                    c.get("libelle"), FormatsPaie.lireMontant(c.get("montant"), "Montant"),
                    FormatsPaie.lireMontant(c.get("mensualite"), "Retenue par mois"),
                    FormatsPaie.lireMois(c.get("premierMois"), "Premier mois de retenue"), c.get("note"));
            flash.addFlashAttribute("succes", x.type().libelle() + " enregistré(e) : " + x.mensualite()
                    + " retenus chaque mois à partir de " + com.venach.kauris.paie.domain.PeriodePaie.libelle(
                    x.premierMois()) + ", sans jamais rendre le net négatif.");
            return "redirect:" + PAGE + "/salaries/" + id;
        }, PAGE + "/salaries/" + id);
    }

    @PostMapping("/avances/{id}/annuler")
    public String annulerAvance(@PathVariable UUID id, @RequestParam(defaultValue = "") String salarie,
                                HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        String retour = PAGE + "/salaries/" + FormatsPaie.lireId(salarie);
        return agir(flash, () -> {
            paie.annulerAvance(a, id);
            flash.addFlashAttribute("succes", "Retenues arrêtées. Ce qui a déjà été retenu reste sur les bulletins.");
            return "redirect:" + retour;
        }, retour);
    }

    // ================================================================== contrats

    @GetMapping("/salaries/{id}/contrats/nouveau")
    public String nouveauContrat(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        commun(model, a, entreprises, paie, "salaries");
        model.addAttribute("s", paie.salarie(a, id));
        model.addAttribute("c", null);
        model.addAttribute("existants", List.of());
        listesContrat(model, 0);
        model.addAttribute("aujourdhui", paie.aujourdHui());
        return "paie/contrat";
    }

    private static void listesContrat(Model model, int elements) {
        model.addAttribute("typesContrat", TypeContrat.values());
        model.addAttribute("types", java.util.Arrays.stream(TypeElement.values()).filter(TypeElement::dansLeContrat)
                .toList());
        model.addAttribute("lignes", elements + PaieController.LIGNES_VIDES);
    }

    @PostMapping("/salaries/{id}/contrats")
    public String creerContrat(@PathVariable UUID id, @RequestParam Map<String, String> c,
                               HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            ContratTravail x = paie.creerContrat(a, id, saisieContrat(c));
            flash.addFlashAttribute("succes", "Contrat " + x.numero() + " enregistré : " + x.poste() + ".");
            return "redirect:" + PAGE + "/salaries/" + id;
        }, PAGE + "/salaries/" + id + "/contrats/nouveau");
    }

    @GetMapping("/contrats/{id}")
    public String contrat(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, paie, "salaries");
        ContratTravail c = paie.contrat(a, id);
        model.addAttribute("c", c);
        model.addAttribute("existants", c.elements());
        model.addAttribute("s", paie.salarie(a, c.salarieId()));
        listesContrat(model, c.elements().size());
        model.addAttribute("aujourdhui", paie.aujourdHui());
        return "paie/contrat";
    }

    @PostMapping("/contrats/{id}")
    public String corrigerContrat(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                  HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            ContratTravail x = paie.corrigerContrat(a, id, saisieContrat(c));
            flash.addFlashAttribute("succes", "Contrat " + x.numero() + " enregistré. Les mois ouverts sont "
                    + "recalculés ; les bulletins des mois clos ne changent pas.");
            return "redirect:" + PAGE + "/contrats/" + id;
        }, PAGE + "/contrats/" + id);
    }

    @PostMapping("/contrats/{id}/terminer")
    public String terminerContrat(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                  HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            LocalDate jour = FormatsPaie.lireDate(c.get("sortie"), "Dernier jour travaillé");
            ContratTravail x = paie.terminerContrat(a, id, jour, c.getOrDefault("motif", ""));
            flash.addFlashAttribute("succes", "Contrat " + x.numero() + " terminé : le dernier mois sera payé au "
                    + "prorata des jours travaillés.");
            return "redirect:" + PAGE + "/contrats/" + id;
        }, PAGE + "/contrats/" + id);
    }

    @PostMapping("/contrats/{id}/rouvrir")
    public String rouvrirContrat(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PREPARER);
        return agir(flash, () -> {
            ContratTravail x = paie.rouvrirContrat(a, id);
            flash.addFlashAttribute("succes", "Contrat " + x.numero() + " de nouveau en cours.");
            return "redirect:" + PAGE + "/contrats/" + id;
        }, PAGE + "/contrats/" + id);
    }

    private static ContratTravail.Saisie saisieContrat(Map<String, String> c) {
        return new ContratTravail.Saisie(FormatsPaie.lireChoix(TypeContrat.class, c.get("type"), "Type de contrat"),
                c.get("poste"), c.get("categorie"), FormatsPaie.lireDate(c.get("debut"), "Date d'embauche"),
                FormatsPaie.lireDate(c.get("finPrevue"), "Fin prévue"),
                FormatsPaie.lireMontant(c.get("salaireBase"), "Salaire de base"), PaieController.lireElements(c),
                c.get("notes"));
    }

    // ================================================================== règles de paie

    @GetMapping("/regles")
    public String regles(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, paie, "regles");
        Pays pays = paie.pays(a.tenantId()).orElse(null);
        model.addAttribute("regles", paie.regles(a));
        model.addAttribute("enVigueur", pays == null ? null : paie.reglesDuMois(a.tenantId(), pays,
                paie.moisCourant()).orElse(null));
        model.addAttribute("modeles", ModelesRecommandes.tous());
        model.addAttribute("tousLesPays", Pays.values());
        model.addAttribute("moisCourant", paie.moisCourant());
        return "paie/regles";
    }

    @PostMapping("/regles/activer")
    public String activer(@RequestParam Map<String, String> c, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PARAMETRER);
        return agir(flash, () -> {
            String code = c.getOrDefault("modele", "");
            Pays pays = c.get("pays") == null || c.get("pays").isBlank() ? null
                    : FormatsPaie.lireChoix(Pays.class, c.get("pays"), "Pays");
            LocalDate depuis = c.get("depuis") == null || c.get("depuis").isBlank() ? null
                    : FormatsPaie.lireMois(c.get("depuis"), "À partir de").atDay(1);
            ReglesPaie r = paie.activerModele(a, code, pays, depuis);
            flash.addFlashAttribute("succes", "Règles « " + r.nom() + " » enregistrées. Vérifiez-les et corrigez ce "
                    + "qui ne correspond pas à votre entreprise (taux d'accident du travail, convention collective…).");
            return "redirect:" + PAGE + "/regles/" + r.id();
        }, PAGE + "/regles");
    }

    @GetMapping("/regles/{id}")
    public String regle(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        FormatsPaie f = commun(model, a, entreprises, paie, "regles");
        ReglesPaie r = paie.reglesParId(a, id);
        var modele = paie.recommandation(r);
        model.addAttribute("r", r);
        model.addAttribute("c", r.contenu());
        model.addAttribute("modele", modele.orElse(null));
        model.addAttribute("reco", FormulaireRegles.ecarts(r.contenu(), modele, f));
        model.addAttribute("plus", FormulaireRegles.LIGNES_EN_PLUS);
        model.addAttribute("deductions", ReglesPaie.Deduction.values());
        model.addAttribute("applications", ReglesPaie.Application.values());
        model.addAttribute("periodicites", ReglesPaie.Periodicite.values());
        model.addAttribute("familles", ReglesPaie.Famille.values());
        model.addAttribute("arrondis", ReglesPaie.Arrondi.values());
        model.addAttribute("moisCourant", paie.moisCourant());
        return "paie/regle";
    }

    @PostMapping("/regles/{id}")
    public String enregistrerRegles(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                    HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PARAMETRER);
        return agir(flash, () -> {
            ReglesPaie r = paie.enregistrerRegles(a, id, c.get("nom"), FormatsPaie.lireMois(c.get("depuis"),
                    "À partir de").atDay(1), FormulaireRegles.lire(c), c.get("notes"));
            flash.addFlashAttribute("succes", "Règles enregistrées. Les mois en préparation sont recalculés ; les mois "
                    + "clos ne changent pas (rouvrez-les s'il le faut).");
            return "redirect:" + PAGE + "/regles/" + r.id();
        }, PAGE + "/regles/" + id);
    }

    @PostMapping("/regles/{id}/version")
    public String nouvelleVersion(@PathVariable UUID id, @RequestParam(defaultValue = "") String depuis,
                                  HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PARAMETRER);
        return agir(flash, () -> {
            ReglesPaie r = paie.nouvelleVersion(a, id, FormatsPaie.lireMois(depuis, "À partir de").atDay(1));
            flash.addFlashAttribute("succes", "Nouvelle version à partir de " + com.venach.kauris.paie.domain
                    .PeriodePaie.libelle(java.time.YearMonth.from(r.depuis())) + " : corrigez ce qui change.");
            return "redirect:" + PAGE + "/regles/" + r.id();
        }, PAGE + "/regles/" + id);
    }

    @PostMapping("/regles/{id}/supprimer")
    public String supprimerRegles(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsPaie.PARAMETRER);
        return agir(flash, () -> {
            paie.supprimerRegles(a, id);
            flash.addFlashAttribute("succes", "Version supprimée.");
            return "redirect:" + PAGE + "/regles";
        }, PAGE + "/regles/" + id);
    }
}
