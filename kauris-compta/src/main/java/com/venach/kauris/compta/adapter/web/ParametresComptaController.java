package com.venach.kauris.compta.adapter.web;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.compta.domain.Compte;
import com.venach.kauris.compta.domain.Imputation;
import com.venach.kauris.compta.domain.Parametres;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.venach.kauris.compta.adapter.web.ComptaController.PAGE;
import static com.venach.kauris.compta.adapter.web.ComptaController.agir;
import static com.venach.kauris.compta.adapter.web.ComptaController.commun;
import static com.venach.kauris.compta.application.PermissionsCompta.CONSULTER;
import static com.venach.kauris.compta.application.PermissionsCompta.PARAMETRER;

/** Le paramétrage : système comptable, plan comptable, journaux et imputations des opérations reçues. */
@Controller
@RequestMapping(ComptaController.PAGE)
public class ParametresComptaController {

    private final ContexteCourant contexte;
    private final ComptaService compta;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public ParametresComptaController(ContexteCourant contexte, ComptaService compta,
                                      GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.compta = compta;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    /** Une ligne d'imputation à l'écran : rôle, précision (avec son libellé) et compte. */
    public record LigneImputation(OperationComptable.Role role, String precision, String libellePrecision,
                                  String compte) {
    }

    @GetMapping("/parametres")
    public String parametres(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "parametres");
        model.addAttribute("systemes", Parametres.Systeme.values());
        model.addAttribute("journaux", compta.journaux(a));
        List<Imputation> imputations = compta.imputations(a);
        List<LigneImputation> lignes = new ArrayList<>();
        for (OperationComptable.Role r : OperationComptable.Role.values()) {
            if (r == OperationComptable.Role.COMPTE) {
                continue;
            }
            Set<String> precisions = new LinkedHashSet<>();
            precisions.add("");
            imputations.stream().filter(i -> i.role() == r).forEach(i -> precisions.add(i.precision()));
            if (r == OperationComptable.Role.VENTE_SERVICES) {
                precisions.add("FLOTTE");
            }
            if (r == OperationComptable.Role.TRESORERIE) {
                precisions.addAll(Imputation.MODES);
                precisions.addAll(compta.precisionsRecues(a));
            }
            for (String p : precisions) {
                String compte = imputations.stream().filter(i -> i.role() == r && i.precision().equals(p)).findFirst()
                        .map(Imputation::compte).orElse("");
                lignes.add(new LigneImputation(r, p, p.isEmpty() ? "Par défaut"
                        : Imputation.PRECISIONS.getOrDefault(p, "Compte de dépôt " + p), compte));
            }
        }
        model.addAttribute("imputations", lignes);
        model.addAttribute("comptes", compta.plan(a).stream().filter(Compte::actif).toList());
        return "compta/parametres";
    }

    @PostMapping("/parametres")
    public String enregistrerParametres(@RequestParam(defaultValue = "NORMAL") String systeme,
                                        @RequestParam(defaultValue = "") String validationAutomatique,
                                        HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PARAMETRER);
        return agir(flash, () -> {
            compta.definirParametres(a, FormatsCompta.lireChoix(Parametres.Systeme.class, systeme, "Système"),
                    "on".equals(validationAutomatique));
            flash.addFlashAttribute("succes", "Paramètres enregistrés.");
            return "redirect:" + PAGE + "/parametres";
        }, PAGE + "/parametres");
    }

    /** Champs {@code imp_role_N}, {@code imp_precision_N}, {@code imp_compte_N} ; une ligne précise vide est retirée. */
    @PostMapping("/imputations")
    public String imputations(@RequestParam Map<String, String> c, HttpServletRequest requete,
                              RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PARAMETRER);
        return agir(flash, () -> {
            List<Imputation> l = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                String role = c.get("imp_role_" + i);
                if (role == null) {
                    continue;
                }
                String compte = c.getOrDefault("imp_compte_" + i, "").strip();
                String precision = c.getOrDefault("imp_precision_" + i, "");
                if (compte.isEmpty() && !precision.isBlank()) {
                    continue;
                }
                int espace = compte.indexOf(' ');
                l.add(new Imputation(a.tenantId(), FormatsCompta.lireChoix(OperationComptable.Role.class, role,
                        "Rôle"), precision, espace > 0 ? compte.substring(0, espace) : compte));
            }
            compta.enregistrerImputations(a, l);
            flash.addFlashAttribute("succes", "Imputations enregistrées : elles s'appliquent aux prochaines opérations "
                    + "reçues (et aux opérations à revoir que vous réessayez).");
            return "redirect:" + PAGE + "/parametres#imputations";
        }, PAGE + "/parametres#imputations");
    }

    @PostMapping("/journaux")
    public String journal(@RequestParam(defaultValue = "") String code, @RequestParam(defaultValue = "") String libelle,
                          HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PARAMETRER);
        return agir(flash, () -> {
            compta.enregistrerJournal(a, code, libelle);
            flash.addFlashAttribute("succes", "Journal « " + code.strip().toUpperCase() + " » enregistré.");
            return "redirect:" + PAGE + "/parametres#journaux";
        }, PAGE + "/parametres#journaux");
    }

    // ================================================================== plan comptable

    @GetMapping("/plan")
    public String plan(@RequestParam(defaultValue = "") String q, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, compta, "plan");
        String r = q.strip().toLowerCase();
        model.addAttribute("q", q);
        model.addAttribute("comptes", compta.plan(a).stream().filter(c -> r.isEmpty() || c.numero().startsWith(r)
                || c.libelle().toLowerCase().contains(r)).toList());
        return "compta/plan";
    }

    @PostMapping("/plan")
    public String ajouterCompte(@RequestParam(defaultValue = "") String numero,
                                @RequestParam(defaultValue = "") String libelle,
                                @RequestParam(defaultValue = "") String lettrable, HttpServletRequest requete,
                                RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PARAMETRER);
        return agir(flash, () -> {
            Compte c = compta.ajouterCompte(a, numero, libelle, "on".equals(lettrable));
            flash.addFlashAttribute("succes", "Compte " + c.numero() + " ajouté.");
            return "redirect:" + PAGE + "/plan?q=" + c.numero();
        }, PAGE + "/plan");
    }

    @PostMapping("/plan/{numero}")
    public String modifierCompte(@PathVariable String numero, @RequestParam(defaultValue = "") String libelle,
                                 @RequestParam(defaultValue = "") String lettrable,
                                 @RequestParam(defaultValue = "") String actif, HttpServletRequest requete,
                                 RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PARAMETRER);
        return agir(flash, () -> {
            compta.modifierCompte(a, numero, libelle, "on".equals(lettrable), "on".equals(actif));
            flash.addFlashAttribute("succes", "Compte " + numero + " enregistré.");
            return "redirect:" + PAGE + "/plan?q=" + numero;
        }, PAGE + "/plan?q=" + numero);
    }

    @GetMapping("/plan.{format}")
    public ResponseEntity<byte[]> planExport(@PathVariable String format, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        List<List<Object>> l = new ArrayList<>();
        for (Compte c : compta.plan(a)) {
            l.add(List.of(c.numero(), c.libelle(), c.classe(), c.lettrable() ? "oui" : "", c.actif() ? "" : "inactif"));
        }
        Tableau t = new Tableau("Plan comptable", "SYSCOHADA révisé", List.of("Compte", "Intitulé", "Classe",
                "Lettrable", "État"), l, null);
        return ComptaController.exporter(producteur, a, t, "Plan comptable", format, compta.aujourdHui());
    }
}
