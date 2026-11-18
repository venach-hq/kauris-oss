package com.venach.kauris.compta.adapter.web;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.compta.domain.Balance;
import com.venach.kauris.compta.domain.Compte;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.compta.domain.EtatTva;
import com.venach.kauris.compta.domain.EtatsFinanciers;
import com.venach.kauris.compta.domain.Mouvement;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.venach.kauris.compta.adapter.web.ComptaController.PAGE;
import static com.venach.kauris.compta.adapter.web.ComptaController.agir;
import static com.venach.kauris.compta.adapter.web.ComptaController.commun;
import static com.venach.kauris.compta.application.PermissionsCompta.CONSULTER;
import static com.venach.kauris.compta.application.PermissionsCompta.SAISIR;
import static com.venach.kauris.compta.application.PermissionsCompta.VALIDER;

/** Grand livre, balance, lettrage, états financiers, TVA et exercices, avec leurs exports (Excel, CSV, PDF). */
@Controller
@RequestMapping(ComptaController.PAGE)
public class EtatsComptaController {

    private final ContexteCourant contexte;
    private final ComptaService compta;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public EtatsComptaController(ContexteCourant contexte, ComptaService compta, GererEntrepriseUseCase entreprises,
                                 ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.compta = compta;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    private LocalDate du(String saisie) {
        return FormatsCompta.lireDate(saisie, "Du", LocalDate.of(compta.aujourdHui().getYear(), 1, 1));
    }

    private LocalDate au(String saisie, LocalDate du) {
        LocalDate fin = FormatsCompta.lireDate(saisie, "Au", null);
        return fin != null ? fin : LocalDate.of(du.getYear(), 12, 31);
    }

    // ================================================================== grand livre

    @GetMapping("/grand-livre")
    public String grandLivre(@RequestParam(defaultValue = "") String compte, @RequestParam(defaultValue = "") String du,
                             @RequestParam(defaultValue = "") String au, @RequestParam(defaultValue = "") String tiers,
                             HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "grand-livre");
        LocalDate debut = du(du);
        LocalDate fin = au(au, debut);
        model.addAttribute("comptes", compta.plan(a));
        model.addAttribute("du", debut);
        model.addAttribute("au", fin);
        model.addAttribute("compte", compte);
        model.addAttribute("tiers", tiers);
        model.addAttribute("exportTout", "?du=" + debut + "&au=" + fin);
        if (!compte.isBlank()) {
            ComptaService.GrandLivre g = compta.grandLivre(a, numero(compte), debut, fin, tiers);
            model.addAttribute("livre", g);
            model.addAttribute("exportCompte", "?compte=" + g.compte().numero() + "&du=" + debut + "&au=" + fin
                    + "&tiers=" + java.net.URLEncoder.encode(tiers, java.nio.charset.StandardCharsets.UTF_8));
        }
        return "compta/grand-livre";
    }

    private static String numero(String saisie) {
        String s = saisie.strip();
        int espace = s.indexOf(' ');
        return espace > 0 ? s.substring(0, espace) : s;
    }

    @GetMapping("/grand-livre.{format}")
    public ResponseEntity<byte[]> grandLivreExport(@PathVariable String format,
                                                   @RequestParam(defaultValue = "") String compte,
                                                   @RequestParam(defaultValue = "") String du,
                                                   @RequestParam(defaultValue = "") String au,
                                                   @RequestParam(defaultValue = "") String tiers,
                                                   HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        LocalDate debut = du(du);
        LocalDate fin = au(au, debut);
        List<String> numeros = compte.isBlank() ? compta.balance(a, debut, fin, true).lignes().stream()
                .map(Balance.Ligne::cle).toList() : List.of(numero(compte));
        List<List<Object>> lignes = new ArrayList<>();
        long totalD = 0;
        long totalC = 0;
        for (String n : numeros) {
            ComptaService.GrandLivre g = compta.grandLivre(a, n, debut, fin, tiers);
            if (g.report() == 0 && g.lignes().isEmpty()) {
                continue;
            }
            lignes.add(List.of(g.compte().numero(), g.compte().libelle(), "", "", "", "", "", ""));
            lignes.add(List.of("", "Report au " + debut, "", "", "", g.report() > 0 ? g.report() : "",
                    g.report() < 0 ? -g.report() : "", g.report()));
            for (ComptaService.LigneLivre l : g.lignes()) {
                Mouvement m = l.mouvement();
                lignes.add(List.of(m.date().toString(), m.libelle(), m.journal(), m.numero().isEmpty() ? "brouillard"
                        : m.numero(), m.piece() + (m.tiersNom().isEmpty() ? "" : " · " + m.tiersNom()),
                        m.debit() == 0 ? "" : m.debit(), m.credit() == 0 ? "" : m.credit(), l.solde()));
            }
            lignes.add(List.of("", "Total " + n, "", "", "", g.totalDebit(), g.totalCredit(), g.solde()));
            totalD += g.totalDebit();
            totalC += g.totalCredit();
        }
        Tableau t = new Tableau("Grand livre", "Du " + debut + " au " + fin, List.of("Date", "Libellé", "Journal",
                "Écriture", "Pièce / tiers", "Débit", "Crédit", "Solde"), lignes,
                List.of("Total", "", "", "", "", totalD, totalC, ""));
        return ComptaController.exporter(producteur, a, t, "Grand livre " + debut.getYear(), format,
                compta.aujourdHui());
    }

    // ================================================================== balance

    @GetMapping("/balance")
    public String balance(@RequestParam(defaultValue = "") String du, @RequestParam(defaultValue = "") String au,
                          @RequestParam(defaultValue = "") String brouillard,
                          @RequestParam(defaultValue = "") String compte, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "balance");
        LocalDate debut = du(du);
        LocalDate fin = au(au, debut);
        boolean avec = !"non".equals(brouillard);
        model.addAttribute("du", debut);
        model.addAttribute("au", fin);
        model.addAttribute("avecBrouillard", avec);
        model.addAttribute("compte", compte);
        model.addAttribute("balance", compte.isBlank() ? compta.balance(a, debut, fin, avec)
                : compta.balanceTiers(a, numero(compte), debut, fin, avec));
        model.addAttribute("comptesTiers", compta.plan(a).stream().filter(Compte::lettrable).toList());
        model.addAttribute("export", "?du=" + debut + "&au=" + fin + "&brouillard=" + (avec ? "oui" : "non")
                + "&compte=" + numero(compte));
        return "compta/balance";
    }

    @GetMapping("/balance.{format}")
    public ResponseEntity<byte[]> balanceExport(@PathVariable String format, @RequestParam(defaultValue = "") String du,
                                                @RequestParam(defaultValue = "") String au,
                                                @RequestParam(defaultValue = "") String brouillard,
                                                @RequestParam(defaultValue = "") String compte,
                                                HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        LocalDate debut = du(du);
        LocalDate fin = au(au, debut);
        boolean avec = !"non".equals(brouillard);
        Balance b = compte.isBlank() ? compta.balance(a, debut, fin, avec)
                : compta.balanceTiers(a, numero(compte), debut, fin, avec);
        List<List<Object>> lignes = new ArrayList<>();
        for (Balance.Ligne l : b.lignes()) {
            lignes.add(List.of(compte.isBlank() ? l.cle() : l.libelle(), compte.isBlank() ? l.libelle() : l.cle(),
                    l.reportDebit(), l.reportCredit(), l.debit(), l.credit(), l.soldeDebiteur(), l.soldeCrediteur()));
        }
        Balance.Ligne t = b.total();
        Tableau tab = new Tableau(compte.isBlank() ? "Balance générale" : "Balance du compte " + numero(compte),
                "Du " + debut + " au " + fin + (avec ? " (brouillard compris)" : " (écritures validées)"),
                List.of(compte.isBlank() ? "Compte" : "Tiers", compte.isBlank() ? "Intitulé" : "Identifiant",
                        "Report débit", "Report crédit", "Débit", "Crédit", "Solde débiteur", "Solde créditeur"),
                lignes, List.of("Total", "", t.reportDebit(), t.reportCredit(), t.debit(), t.credit(),
                b.lignes().stream().mapToLong(Balance.Ligne::soldeDebiteur).sum(),
                b.lignes().stream().mapToLong(Balance.Ligne::soldeCrediteur).sum()));
        return ComptaController.exporter(producteur, a, tab, tab.titre() + " " + debut.getYear(), format,
                compta.aujourdHui());
    }

    // ================================================================== lettrage

    @GetMapping("/lettrage")
    public String lettrage(@RequestParam(defaultValue = "411000") String compte,
                           @RequestParam(defaultValue = "") String toutes, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "lettrage");
        String n = numero(compte);
        model.addAttribute("compte", n);
        model.addAttribute("comptesTiers", compta.plan(a).stream().filter(Compte::lettrable).toList());
        model.addAttribute("toutes", !toutes.isBlank());
        model.addAttribute("lignes", compta.lignesALettrer(a, n, !toutes.isBlank()));
        return "compta/lettrage";
    }

    @PostMapping("/lettrage")
    public String lettrer(@RequestParam String compte, @RequestParam(name = "ligne", required = false)
                          List<String> lignes, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        String retour = PAGE + "/lettrage?compte=" + numero(compte);
        return agir(flash, () -> {
            String code = compta.lettrer(a, compte, lignes == null ? List.of() : lignes);
            flash.addFlashAttribute("succes", "Lignes lettrées « " + code + " ».");
            return "redirect:" + retour;
        }, retour);
    }

    @PostMapping("/lettrage/automatique")
    public String lettrerAutomatiquement(@RequestParam String compte, HttpServletRequest requete,
                                         RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        String retour = PAGE + "/lettrage?compte=" + numero(compte);
        return agir(flash, () -> {
            int n = compta.lettrerAutomatiquement(a, compte);
            flash.addFlashAttribute("succes", n == 0 ? "Rien à lettrer automatiquement."
                    : n + " groupe(s) de lignes lettré(s).");
            return "redirect:" + retour;
        }, retour);
    }

    @PostMapping("/lettrage/delettrer")
    public String delettrer(@RequestParam String compte, @RequestParam String code, HttpServletRequest requete,
                            RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, SAISIR);
        String retour = PAGE + "/lettrage?compte=" + numero(compte) + "&toutes=1";
        return agir(flash, () -> {
            compta.delettrer(a, compte, code);
            flash.addFlashAttribute("succes", "Lettrage « " + code + " » retiré.");
            return "redirect:" + retour;
        }, retour);
    }

    // ================================================================== états financiers

    @GetMapping("/etats")
    public String etats(@RequestParam(defaultValue = "") String annee, @RequestParam(defaultValue = "") String brouillard,
                        HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "etats");
        int an = FormatsCompta.lireAnnee(annee, compta.aujourdHui().getYear());
        model.addAttribute("etats", compta.etats(a, an, null, !"non".equals(brouillard)));
        model.addAttribute("export", "?annee=" + an + "&brouillard=" + ("non".equals(brouillard) ? "non" : "oui"));
        model.addAttribute("exercices", compta.exercices(a));
        return "compta/etats";
    }

    @GetMapping("/etats/{quoi}.{format}")
    public ResponseEntity<byte[]> etatsExport(@PathVariable String quoi, @PathVariable String format,
                                              @RequestParam(defaultValue = "") String annee,
                                              @RequestParam(defaultValue = "") String brouillard,
                                              HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        int an = FormatsCompta.lireAnnee(annee, compta.aujourdHui().getYear());
        ComptaService.Etats e = compta.etats(a, an, null, !"non".equals(brouillard));
        Tableau t = switch (quoi) {
            case "bilan" -> bilan(e);
            case "resultat" -> resultat(e);
            default -> throw new ElementIntrouvable("État inconnu.");
        };
        return ComptaController.exporter(producteur, a, t, t.titre() + " " + an, format, compta.aujourdHui());
    }

    private static String precision(ComptaService.Etats e) {
        return e.exercice().modele().nom() + " · exercice " + e.annee() + " au " + e.au()
                + (e.brouillard() > 0 && e.avecBrouillard() ? " · provisoire : " + e.brouillard()
                + " écriture(s) au brouillard" : "");
    }

    static Tableau resultat(ComptaService.Etats e) {
        List<List<Object>> l = new ArrayList<>();
        List<EtatsFinanciers.Ligne> n1 = e.precedent().resultat();
        for (int i = 0; i < e.exercice().resultat().size(); i++) {
            EtatsFinanciers.Ligne x = e.exercice().resultat().get(i);
            l.add(List.of(x.rubrique().ref(), x.rubrique().libelle(), x.net(), n1.get(i).net()));
        }
        return new Tableau("Compte de résultat", precision(e), List.of("Réf.", "Libellé", "Exercice " + e.annee(),
                "Exercice " + (e.annee() - 1)), l, null);
    }

    static Tableau bilan(ComptaService.Etats e) {
        List<List<Object>> l = new ArrayList<>();
        List<EtatsFinanciers.Ligne> a1 = e.precedent().actif();
        for (int i = 0; i < e.exercice().actif().size(); i++) {
            EtatsFinanciers.Ligne x = e.exercice().actif().get(i);
            l.add(List.of("Actif", x.rubrique().ref(), x.rubrique().libelle(), x.brut(), x.amortissements(), x.net(),
                    a1.get(i).net()));
        }
        List<EtatsFinanciers.Ligne> p1 = e.precedent().passif();
        for (int i = 0; i < e.exercice().passif().size(); i++) {
            EtatsFinanciers.Ligne x = e.exercice().passif().get(i);
            l.add(List.of("Passif", x.rubrique().ref(), x.rubrique().libelle(), "", "", x.net(), p1.get(i).net()));
        }
        return new Tableau("Bilan", precision(e), List.of("", "Réf.", "Libellé", "Brut", "Amort. et dépréc.",
                "Net " + e.annee(), "Net " + (e.annee() - 1)), l, null);
    }

    // ================================================================== TVA

    @GetMapping("/tva")
    public String tva(@RequestParam(defaultValue = "") String mois, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "tva");
        YearMonth m = FormatsCompta.lireMois(mois, "Mois", YearMonth.from(compta.aujourdHui()).minusMonths(1));
        model.addAttribute("mois", m);
        model.addAttribute("tva", compta.tva(a, m));
        model.addAttribute("export", "?mois=" + m);
        model.addAttribute("libelles", compta.libelles(a.tenantId()));
        return "compta/tva";
    }

    @GetMapping("/tva.{format}")
    public ResponseEntity<byte[]> tvaExport(@PathVariable String format, @RequestParam(defaultValue = "") String mois,
                                            HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        YearMonth m = FormatsCompta.lireMois(mois, "Mois", YearMonth.from(compta.aujourdHui()).minusMonths(1));
        EtatTva t = compta.tva(a, m);
        Map<String, String> libelles = compta.libelles(a.tenantId());
        List<List<Object>> l = new ArrayList<>();
        l.add(List.of("Chiffre d'affaires hors taxes (comptes 70)", "", t.chiffreAffaires()));
        t.collectee().forEach((c, v) -> l.add(List.of("TVA collectée", c + " " + libelles.getOrDefault(c, ""), v)));
        l.add(List.of("Total TVA collectée", "", t.totalCollectee()));
        t.deductible().forEach((c, v) -> l.add(List.of("TVA déductible", c + " " + libelles.getOrDefault(c, ""), v)));
        l.add(List.of("Total TVA déductible", "", t.totalDeductible()));
        l.add(List.of("Crédit de TVA du mois précédent", EtatTva.COMPTE_CREDIT, t.creditAnterieur()));
        l.add(List.of("TVA due", EtatTva.COMPTE_DUE, t.due()));
        l.add(List.of("Crédit de TVA à reporter", EtatTva.COMPTE_CREDIT, t.creditAReporter()));
        Tableau tab = new Tableau("État de TVA", "Mois de " + new FormatsCompta(null).mois(m), List.of("Rubrique",
                "Compte", "Montant"), l, null);
        return ComptaController.exporter(producteur, a, tab, "Etat de TVA " + m, format, compta.aujourdHui());
    }

    @PostMapping("/tva/liquider")
    public String liquiderTva(@RequestParam String mois, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, VALIDER);
        String retour = PAGE + "/tva?mois=" + mois;
        return agir(flash, () -> {
            Ecriture e = compta.liquiderTva(a, FormatsCompta.lireMois(mois, "Mois", null));
            flash.addFlashAttribute("succes", "Écriture de liquidation de la TVA préparée au brouillard : vérifiez-la "
                    + "et validez-la.");
            return "redirect:" + PAGE + "/ecritures/" + e.id();
        }, retour);
    }

    // ================================================================== exercices

    @GetMapping("/exercices")
    public String exercices(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "exercices");
        var liste = compta.exercices(a);
        model.addAttribute("exercices", liste);
        Map<Integer, List<String>> obstacles = new java.util.HashMap<>();
        liste.stream().filter(x -> !x.clos()).forEach(x -> obstacles.put(x.annee(), compta.obstaclesCloture(a,
                x.annee())));
        model.addAttribute("obstacles", obstacles);
        return "compta/exercices";
    }

    @PostMapping("/exercices/{annee}/cloturer")
    public String cloturer(@PathVariable int annee, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, VALIDER);
        return agir(flash, () -> {
            Ecriture an = compta.cloturer(a, annee);
            flash.addFlashAttribute("succes", "Exercice " + annee + " clôturé." + (an == null ? ""
                    : " Les à-nouveaux de " + (annee + 1) + " sont passés (" + an.numero() + ")."));
            return "redirect:" + PAGE + "/exercices";
        }, PAGE + "/exercices");
    }

    @PostMapping("/exercices/{annee}/rouvrir")
    public String rouvrir(@PathVariable int annee, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, VALIDER);
        return agir(flash, () -> {
            compta.rouvrir(a, annee);
            flash.addFlashAttribute("succes", "Exercice " + annee + " rouvert : ses à-nouveaux sont retirés, ils "
                    + "seront refaits à la prochaine clôture.");
            return "redirect:" + PAGE + "/exercices";
        }, PAGE + "/exercices");
    }
}
