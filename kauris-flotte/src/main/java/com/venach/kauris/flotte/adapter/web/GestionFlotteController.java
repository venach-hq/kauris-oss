package com.venach.kauris.flotte.adapter.web;

import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.flotte.application.FlotteService;
import com.venach.kauris.flotte.application.PermissionsFlotte;
import com.venach.kauris.flotte.domain.Ajustement;
import com.venach.kauris.flotte.domain.Compte;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.DocumentVehicule;
import com.venach.kauris.flotte.domain.StatutContrat;
import com.venach.kauris.flotte.domain.StatutVehicule;
import com.venach.kauris.flotte.domain.TypeContrat;
import com.venach.kauris.flotte.domain.TypeDocument;
import com.venach.kauris.flotte.domain.TypeVehicule;
import com.venach.kauris.flotte.domain.Vehicule;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.venach.kauris.flotte.adapter.web.FlotteController.PAGE;
import static com.venach.kauris.flotte.adapter.web.FlotteController.agir;
import static com.venach.kauris.flotte.adapter.web.FlotteController.commun;
import static com.venach.kauris.flotte.adapter.web.FormatsFlotte.lireChoix;
import static com.venach.kauris.flotte.adapter.web.FormatsFlotte.lireDate;
import static com.venach.kauris.flotte.adapter.web.FormatsFlotte.lireEntier;
import static com.venach.kauris.flotte.adapter.web.FormatsFlotte.lireId;
import static com.venach.kauris.flotte.adapter.web.FormatsFlotte.lireMontant;
import static com.venach.kauris.flotte.adapter.web.FormatsFlotte.lireMontantFacultatif;
import static com.venach.kauris.flotte.application.PermissionsFlotte.CONSULTER;

/** Les fiches : véhicules, conducteurs, contrats (avec le compte du conducteur) et papiers. */
@Controller
@RequestMapping(PAGE)
public class GestionFlotteController {

    private final ContexteCourant contexte;
    private final FlotteService flotte;
    private final GererEntrepriseUseCase entreprises;

    public GestionFlotteController(ContexteCourant contexte, FlotteService flotte,
                                   GererEntrepriseUseCase entreprises) {
        this.contexte = contexte;
        this.flotte = flotte;
        this.entreprises = entreprises;
    }

    /** Lundi → « lundi », pour les cases des jours de repos. */
    public record JourSemaine(DayOfWeek jour, String libelle) {
    }

    static List<JourSemaine> semaine() {
        return EnumSet.allOf(DayOfWeek.class).stream()
                .map(j -> new JourSemaine(j, j.getDisplayName(TextStyle.FULL, Locale.FRENCH))).toList();
    }

    // ================================================================== véhicules

    @GetMapping("/vehicules")
    public String vehicules(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "vehicules");
        model.addAttribute("vehicules", flotte.vehicules(a).stream()
                .sorted(Comparator.comparing(Vehicule::immatriculation)).toList());
        model.addAttribute("enContrat", enContrat(a));
        return "flotte/vehicules";
    }

    /** véhicule → libellé du contrat en cours (« CT-2026-0001 · Koffi »). */
    private Map<UUID, String> enContrat(Acces a) {
        Map<UUID, String> noms = new HashMap<>();
        flotte.conducteurs(a).forEach(d -> noms.put(d.id(), d.nom()));
        Map<UUID, String> m = new HashMap<>();
        flotte.contrats(a).stream().filter(Contrat::enCours)
                .forEach(c -> m.put(c.vehiculeId(), c.numero() + " · " + noms.getOrDefault(c.conducteurId(), "")));
        return m;
    }

    @GetMapping("/vehicules/nouveau")
    public String nouveauVehicule(HttpServletRequest requete, Model model) {
        return ficheVehicule(null, requete, model);
    }

    @GetMapping("/vehicules/{id}")
    public String ficheVehicule(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "vehicules");
        Vehicule v = id == null ? null : flotte.vehicule(a, id);
        model.addAttribute("v", v);
        model.addAttribute("types", TypeVehicule.values());
        model.addAttribute("statuts", StatutVehicule.values());
        model.addAttribute("typesPapier", TypeDocument.values());
        model.addAttribute("papiers", v == null ? List.of() : flotte.documents(a).stream()
                .filter(d -> d.vehiculeId().equals(id))
                .sorted(Comparator.comparing(DocumentVehicule::expireLe,
                        Comparator.nullsLast(Comparator.reverseOrder()))).toList());
        model.addAttribute("contrats", v == null ? List.of() : flotte.contrats(a).stream()
                .filter(c -> c.vehiculeId().equals(id)).sorted(Comparator.comparing(Contrat::debut).reversed())
                .toList());
        model.addAttribute("noms", noms(a));
        model.addAttribute("aujourdhui", flotte.aujourdHui());
        return "flotte/vehicule";
    }

    @PostMapping("/vehicules")
    public String creerVehicule(@RequestParam Map<String, String> c, HttpServletRequest requete,
                                RedirectAttributes flash) {
        return enregistrerVehicule(null, c, requete, flash);
    }

    @PostMapping("/vehicules/{id}")
    public String enregistrerVehicule(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                      HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return agir(flash, () -> {
            Vehicule v = flotte.enregistrerVehicule(a, id, new Vehicule.Saisie(
                    lireChoix(TypeVehicule.class, c.get("type"), "Type"), c.get("immatriculation"), c.get("marque"),
                    c.get("modele"), c.get("couleur"), lireEntier(c.get("annee"), "Année"), c.get("chassis"),
                    lireDate(c.get("miseEnService"), "Mise en service"),
                    lireMontantFacultatif(c.get("prixAchat"), "Prix d'achat"),
                    lireMontantFacultatif(c.get("compteur"), "Compteur"), c.get("referencePaiement"),
                    c.containsKey("statut") ? lireChoix(StatutVehicule.class, c.get("statut"), "Statut")
                            : StatutVehicule.EN_SERVICE, c.get("notes")));
            flash.addFlashAttribute("succes", "Véhicule " + v.immatriculation() + " enregistré.");
            return "redirect:" + PAGE + "/vehicules/" + v.id();
        }, PAGE + "/vehicules/" + (id == null ? "nouveau" : id));
    }

    // ================================================================== conducteurs

    @GetMapping("/conducteurs")
    public String conducteurs(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "conducteurs");
        model.addAttribute("conducteurs", flotte.conducteurs(a).stream()
                .sorted(Comparator.comparing(Conducteur::actif).reversed().thenComparing(Conducteur::nom)).toList());
        Map<UUID, String> vehicules = new HashMap<>();
        flotte.vehicules(a).forEach(v -> vehicules.put(v.id(), v.immatriculation()));
        Map<UUID, String> m = new HashMap<>();
        flotte.contrats(a).stream().filter(Contrat::enCours)
                .forEach(c -> m.put(c.conducteurId(), vehicules.getOrDefault(c.vehiculeId(), "")));
        model.addAttribute("vehiculeDe", m);
        model.addAttribute("aujourdhui", flotte.aujourdHui());
        return "flotte/conducteurs";
    }

    @GetMapping("/conducteurs/nouveau")
    public String nouveauConducteur(HttpServletRequest requete, Model model) {
        return ficheConducteur(null, requete, model);
    }

    @GetMapping("/conducteurs/{id}")
    public String ficheConducteur(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "conducteurs");
        model.addAttribute("d", id == null ? null : flotte.conducteur(a, id));
        model.addAttribute("contrats", id == null ? List.of() : flotte.contrats(a).stream()
                .filter(c -> c.conducteurId().equals(id)).sorted(Comparator.comparing(Contrat::debut).reversed())
                .toList());
        model.addAttribute("noms", noms(a));
        model.addAttribute("aujourdhui", flotte.aujourdHui());
        return "flotte/conducteur";
    }

    @PostMapping("/conducteurs")
    public String creerConducteur(@RequestParam Map<String, String> c, HttpServletRequest requete,
                                  RedirectAttributes flash) {
        return enregistrerConducteur(null, c, requete, flash);
    }

    @PostMapping("/conducteurs/{id}")
    public String enregistrerConducteur(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                        HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return agir(flash, () -> {
            Conducteur d = flotte.enregistrerConducteur(a, id, new Conducteur.Saisie(c.get("nom"),
                    c.get("telephone"), c.get("pieceIdentite"), c.get("permisNumero"), c.get("permisCategorie"),
                    lireDate(c.get("permisExpireLe"), "Expiration du permis"), c.get("garantNom"),
                    c.get("garantTelephone"), id == null || "true".equals(c.get("actif")), c.get("notes")));
            flash.addFlashAttribute("succes", d.nom() + " enregistré.");
            return "redirect:" + PAGE + "/conducteurs/" + d.id();
        }, PAGE + "/conducteurs/" + (id == null ? "nouveau" : id));
    }

    // ================================================================== contrats

    /** Les noms à afficher : véhicules (immatriculation) et conducteurs, par identifiant. */
    private Map<UUID, String> noms(Acces a) {
        Map<UUID, String> m = new HashMap<>();
        flotte.vehicules(a).forEach(v -> m.put(v.id(), v.immatriculation()));
        flotte.conducteurs(a).forEach(d -> m.put(d.id(), d.nom()));
        return m;
    }

    @GetMapping("/contrats")
    public String contrats(@RequestParam(defaultValue = "false") boolean tous, HttpServletRequest requete,
                           Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "contrats");
        model.addAttribute("contrats", flotte.contrats(a).stream().filter(c -> tous || c.enCours())
                .sorted(Comparator.comparing(Contrat::numero).reversed()).toList());
        model.addAttribute("tous", tous);
        model.addAttribute("noms", noms(a));
        model.addAttribute("aujourdhui", flotte.aujourdHui());
        return "flotte/contrats";
    }

    @GetMapping("/contrats/nouveau")
    public String nouveauContrat(@RequestParam(required = false) UUID vehicule,
                                 @RequestParam(required = false) UUID conducteur, HttpServletRequest requete,
                                 Model model) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.GERER);
        commun(model, a, entreprises, flotte, "contrats");
        Set<UUID> occupes = new java.util.HashSet<>();
        flotte.contrats(a).stream().filter(Contrat::enCours).forEach(c -> {
            occupes.add(c.vehiculeId());
            occupes.add(c.conducteurId());
        });
        model.addAttribute("vehicules", flotte.vehicules(a).stream().filter(v -> !occupes.contains(v.id())
                        && v.statut() != StatutVehicule.CEDE && v.statut() != StatutVehicule.HORS_SERVICE)
                .sorted(Comparator.comparing(Vehicule::immatriculation)).toList());
        model.addAttribute("conducteurs", flotte.conducteurs(a).stream()
                .filter(d -> d.actif() && !occupes.contains(d.id())).sorted(Comparator.comparing(Conducteur::nom))
                .toList());
        model.addAttribute("vehiculeChoisi", vehicule);
        model.addAttribute("conducteurChoisi", conducteur);
        model.addAttribute("types", TypeContrat.values());
        model.addAttribute("semaine", semaine());
        model.addAttribute("aujourdhui", flotte.aujourdHui());
        return "flotte/contrat-nouveau";
    }

    @PostMapping("/contrats")
    public String creerContrat(@RequestParam MultiValueMap<String, String> c, HttpServletRequest requete,
                               RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.GERER);
        return agir(flash, () -> {
            TypeContrat type = lireChoix(TypeContrat.class, c.getFirst("type"), "Type de contrat");
            Contrat x = flotte.creerContrat(a, lireId(c.getFirst("vehicule")), lireId(c.getFirst("conducteur")), type,
                    lireDate(c.getFirst("debut"), "Début"),
                    type.versements() ? lireMontant(c.getFirst("parJour"), "Versement par jour") : 0,
                    repos(c.get("repos")), type == TypeContrat.LOCATION_VENTE
                            ? lireMontant(c.getFirst("prixTotal"), "Total à payer") : null,
                    c.getFirst("notes"));
            flash.addFlashAttribute("succes", "Contrat " + x.numero() + " créé.");
            return "redirect:" + PAGE + "/contrats/" + x.id();
        }, PAGE + "/contrats/nouveau");
    }

    private static Set<DayOfWeek> repos(List<String> jours) {
        Set<DayOfWeek> s = EnumSet.noneOf(DayOfWeek.class);
        if (jours != null) {
            jours.forEach(j -> s.add(lireChoix(DayOfWeek.class, j, "Jour de repos")));
        }
        return s;
    }

    /** Le contrat et le compte du conducteur : situation, calendrier du mois, relevé, corrections. */
    @GetMapping("/contrats/{id}")
    public String contrat(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "contrats");
        Contrat c = flotte.contrat(a, id);
        Compte.Situation s = flotte.compte(a, id);
        LocalDate jour = flotte.aujourdHui();
        model.addAttribute("c", c);
        model.addAttribute("compte", s);
        model.addAttribute("vehicule", flotte.vehicule(a, c.vehiculeId()));
        model.addAttribute("conducteur", flotte.conducteur(a, c.conducteurId()));
        model.addAttribute("repos", Compte.repos(c.joursRepos()));
        model.addAttribute("releve", s.releve().reversed());
        model.addAttribute("versements", flotte.versementsDuContrat(a, id).stream()
                .sorted(Comparator.comparing(com.venach.kauris.flotte.domain.Versement::date).reversed()).toList());
        model.addAttribute("ajustements", flotte.ajustementsDuContrat(a, id).stream()
                .sorted(Comparator.comparing(Ajustement::date).reversed()).toList());
        model.addAttribute("typesAjustement", Ajustement.Type.values());
        model.addAttribute("semaine", semaine());
        model.addAttribute("aujourdhui", jour);
        return "flotte/contrat";
    }

    private String changer(UUID id, HttpServletRequest requete, RedirectAttributes flash, String message,
                           Function<Contrat, Contrat> changement) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.GERER);
        return agir(flash, () -> {
            flotte.changerContrat(a, id, changement);
            flash.addFlashAttribute("succes", message);
            return "redirect:" + PAGE + "/contrats/" + id;
        }, PAGE + "/contrats/" + id);
    }

    @PostMapping("/contrats/{id}/tarif")
    public String tarif(@PathVariable UUID id, @RequestParam String depuis, @RequestParam String montant,
                        HttpServletRequest requete, RedirectAttributes flash) {
        return changer(id, requete, flash, "Nouveau tarif enregistré.", c -> c.changerTarif(
                lireDate(depuis, "À partir du"), lireMontant(montant, "Versement par jour")));
    }

    @PostMapping("/contrats/{id}/repos")
    public String joursRepos(@PathVariable UUID id, @RequestParam(required = false) List<String> repos,
                             HttpServletRequest requete, RedirectAttributes flash) {
        return changer(id, requete, flash, "Jours de repos enregistrés.", c -> c.changerJoursRepos(repos(repos)));
    }

    @PostMapping("/contrats/{id}/pauses")
    public String pause(@PathVariable UUID id, @RequestParam Map<String, String> p, HttpServletRequest requete,
                        RedirectAttributes flash) {
        return changer(id, requete, flash, "Pause enregistrée.", c -> {
            LocalDate du = lireDate(p.get("du"), "Du");
            if (du == null) {
                throw new com.venach.kauris.kernel.commun.ErreurMetier("Indiquez le premier jour de la pause.");
            }
            return c.ajouterPause(new Contrat.Pause(UUID.randomUUID(), du, lireDate(p.get("au"), "Au"),
                    p.getOrDefault("motif", ""), "true".equals(p.get("aCharge"))));
        });
    }

    @PostMapping("/contrats/{id}/pauses/{pause}/finir")
    public String finirPause(@PathVariable UUID id, @PathVariable UUID pause, @RequestParam String au,
                             HttpServletRequest requete, RedirectAttributes flash) {
        return changer(id, requete, flash, "Pause terminée : les versements reprennent.",
                c -> c.finirPause(pause, lireDate(au, "Dernier jour")));
    }

    @PostMapping("/contrats/{id}/pauses/{pause}/retirer")
    public String retirerPause(@PathVariable UUID id, @PathVariable UUID pause, HttpServletRequest requete,
                               RedirectAttributes flash) {
        return changer(id, requete, flash, "Pause retirée.", c -> c.retirerPause(pause));
    }

    @PostMapping("/contrats/{id}/corriger")
    public String corriger(@PathVariable UUID id, @RequestParam Map<String, String> p, HttpServletRequest requete,
                           RedirectAttributes flash) {
        return changer(id, requete, flash, "Contrat corrigé.", c -> c.corriger(lireDate(p.get("debut"), "Début"),
                lireMontantFacultatif(p.get("prixTotal"), "Total à payer"))
                .modifierNotes(p.getOrDefault("notes", "")));
    }

    @PostMapping("/contrats/{id}/terminer")
    public String terminer(@PathVariable UUID id, @RequestParam Map<String, String> p, HttpServletRequest requete,
                           RedirectAttributes flash) {
        boolean resiliation = "true".equals(p.get("resiliation"));
        return changer(id, requete, flash, resiliation ? "Contrat résilié." : "Contrat terminé.",
                c -> c.terminer(lireDate(p.get("dernierJour"), "Dernier jour"), resiliation,
                        p.getOrDefault("motif", "")));
    }

    @PostMapping("/contrats/{id}/rouvrir")
    public String rouvrir(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return changer(id, requete, flash, "Contrat rouvert.", Contrat::rouvrir);
    }

    @PostMapping("/contrats/{id}/ceder")
    public String ceder(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.GERER);
        return agir(flash, () -> {
            flotte.transfererPropriete(a, id);
            flash.addFlashAttribute("succes", "Véhicule cédé au conducteur : le contrat est terminé.");
            return "redirect:" + PAGE + "/contrats/" + id;
        }, PAGE + "/contrats/" + id);
    }

    @PostMapping("/contrats/{id}/ajustements")
    public String ajustement(@PathVariable UUID id, @RequestParam Map<String, String> p, HttpServletRequest requete,
                             RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.GERER);
        return agir(flash, () -> {
            Ajustement x = flotte.ajouterAjustement(a, id, lireChoix(Ajustement.Type.class, p.get("type"), "Type"),
                    lireMontant(p.get("montant"), "Montant"), p.getOrDefault("motif", ""),
                    lireDate(p.get("date"), "Date"));
            flash.addFlashAttribute("succes", x.type().libelle() + " enregistrée.");
            return "redirect:" + PAGE + "/contrats/" + id;
        }, PAGE + "/contrats/" + id);
    }

    @PostMapping("/contrats/{id}/ajustements/{ajustement}/annuler")
    public String annulerAjustement(@PathVariable UUID id, @PathVariable UUID ajustement,
                                    @RequestParam(defaultValue = "") String motif, HttpServletRequest requete,
                                    RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.GERER);
        return agir(flash, () -> {
            flotte.annulerAjustement(a, ajustement, motif);
            flash.addFlashAttribute("succes", "Opération annulée.");
            return "redirect:" + PAGE + "/contrats/" + id;
        }, PAGE + "/contrats/" + id);
    }

    // ================================================================== papiers

    @GetMapping("/papiers")
    public String papiers(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "papiers");
        LocalDate jour = flotte.aujourdHui();
        model.addAttribute("papiers", flotte.documents(a).stream().sorted(Comparator.comparing(
                DocumentVehicule::expireLe, Comparator.nullsLast(Comparator.naturalOrder()))).toList());
        model.addAttribute("noms", noms(a));
        model.addAttribute("vehicules", flotte.vehicules(a).stream()
                .sorted(Comparator.comparing(Vehicule::immatriculation)).toList());
        model.addAttribute("typesPapier", TypeDocument.values());
        model.addAttribute("aujourdhui", jour);
        return "flotte/papiers";
    }

    @GetMapping("/papiers/{id}")
    public String papier(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "papiers");
        model.addAttribute("p", flotte.document(a, id));
        model.addAttribute("vehicules", flotte.vehicules(a).stream()
                .sorted(Comparator.comparing(Vehicule::immatriculation)).toList());
        model.addAttribute("typesPapier", TypeDocument.values());
        return "flotte/papier";
    }

    @PostMapping("/papiers")
    public String creerPapier(@RequestParam Map<String, String> p, HttpServletRequest requete,
                              RedirectAttributes flash) {
        return enregistrerPapier(null, p, requete, flash);
    }

    @PostMapping("/papiers/{id}")
    public String enregistrerPapier(@PathVariable UUID id, @RequestParam Map<String, String> p,
                                    HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        String retour = p.getOrDefault("retour", "");
        String suite = retour.matches("/flotte/vehicules/[0-9a-f-]{36}") ? retour : PAGE + "/papiers";
        return agir(flash, () -> {
            DocumentVehicule d = flotte.enregistrerDocument(a, id, new DocumentVehicule.Saisie(
                    lireId(p.get("vehicule")), lireChoix(TypeDocument.class, p.get("type"), "Papier"),
                    p.get("reference"), lireDate(p.get("emisLe"), "Émis le"), lireDate(p.get("expireLe"), "Expire le"),
                    lireMontantFacultatif(p.get("cout"), "Coût"), p.get("notes")));
            flash.addFlashAttribute("succes", d.type().libelle() + " enregistré(e).");
            return "redirect:" + suite;
        }, id == null ? suite : PAGE + "/papiers/" + id);
    }

    @PostMapping("/papiers/{id}/supprimer")
    public String supprimerPapier(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return agir(flash, () -> {
            flotte.supprimerDocument(a, id);
            flash.addFlashAttribute("succes", "Papier supprimé.");
            return "redirect:" + PAGE + "/papiers";
        }, PAGE + "/papiers/" + id);
    }

    /** Pour le gabarit : le statut d'un contrat en pastille. */
    public static boolean actif(StatutContrat s) {
        return s == StatutContrat.EN_COURS;
    }
}
