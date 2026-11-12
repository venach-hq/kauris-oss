package com.venach.kauris.ventes.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.PermissionsVentes;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.ReglagesVentes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.venach.kauris.ventes.application.PermissionsVentes.CONSULTER;

/**
 * Les écrans des ventes : tableau de bord, clients, catalogue, impayés et réglages. Les documents (devis,
 * factures, avoirs) ont leur propre contrôleur. Les droits sont contrôlés par le service.
 */
@Controller
@RequestMapping(VentesController.PAGE)
public class VentesController {

    public static final String PAGE = "/ventes";

    private final ContexteCourant contexte;
    private final VentesService ventes;
    private final GererEntrepriseUseCase entreprises;

    public VentesController(ContexteCourant contexte, VentesService ventes, GererEntrepriseUseCase entreprises) {
        this.contexte = contexte;
        this.ventes = ventes;
        this.entreprises = entreprises;
    }

    /** Les attributs communs à tous les écrans des ventes : formats, droits, onglet courant. */
    static void commun(Model model, Acces a, GererEntrepriseUseCase entreprises, String onglet) {
        model.addAttribute("f", new FormatsVentes(entreprises.consulter(a.tenantId()).devise()));
        model.addAttribute("droits", PermissionsVentes.Droits.de(a));
        model.addAttribute("onglet", onglet);
    }

    /** Exécute une action de formulaire : message de réussite, ou l'erreur métier affichée sur la page d'arrivée. */
    static String agir(RedirectAttributes flash, Supplier<String> action, String retourEnErreur) {
        try {
            return action.get();
        } catch (ErreurMetier e) {
            if (e.getClass() != ErreurMetier.class) {
                throw e;    // accès refusé, introuvable : pages d'erreur communes
            }
            flash.addFlashAttribute("erreur", e.getMessage());
            return "redirect:" + retourEnErreur;
        }
    }

    // ================================================================== tableau de bord

    @GetMapping
    public String accueil(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, "accueil");
        model.addAttribute("bilan", ventes.bilan(a));
        model.addAttribute("aujourdHui", ventes.aujourdHui());
        return "ventes/accueil";
    }

    // ================================================================== clients

    @GetMapping("/clients")
    public String clients(@RequestParam(name = "q", required = false) String recherche,
                          @RequestParam(name = "archives", defaultValue = "false") boolean archives,
                          HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, "clients");
        model.addAttribute("clients", ventes.clients(a, recherche, archives));
        model.addAttribute("q", recherche == null ? "" : recherche);
        model.addAttribute("archives", archives);
        model.addAttribute("types", Client.Type.values());
        model.addAttribute("delaiParDefaut", ventes.reglages(a).delaiPaiement());
        return "ventes/clients";
    }

    @PostMapping("/clients")
    public String creerClient(@RequestParam Map<String, String> champs, HttpServletRequest requete,
                              RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            Client c = ventes.creerClient(a, saisieClient(champs));
            flash.addFlashAttribute("succes", "Client « " + c.nom() + " » ajouté.");
            return "redirect:" + PAGE + "/clients/" + c.id();
        }, PAGE + "/clients");
    }

    @GetMapping("/clients/{id}")
    public String client(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, "clients");
        Client c = ventes.client(a, id);
        model.addAttribute("client", c);
        model.addAttribute("etat", c.etat());
        model.addAttribute("types", Client.Type.values());
        model.addAttribute("documents", ventes.documents(a, new Ports.Filtre(null, id, null, null, null, false, 50)));
        long du = ventes.documents(a, new Ports.Filtre(null, id, null, null, null, true, 0)).stream()
                .mapToLong(d -> d.resteAPayer()).sum();
        model.addAttribute("du", du);
        model.addAttribute("aujourdHui", ventes.aujourdHui());
        return "ventes/client";
    }

    @PostMapping("/clients/{id}")
    public String modifierClient(@PathVariable UUID id, @RequestParam Map<String, String> champs,
                                 HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.modifierClient(a, id, saisieClient(champs));
            flash.addFlashAttribute("succes", "Fiche du client enregistrée.");
            return "redirect:" + PAGE + "/clients/" + id;
        }, PAGE + "/clients/" + id);
    }

    @PostMapping("/clients/{id}/archiver")
    public String archiverClient(@PathVariable UUID id, @RequestParam(name = "archiver") boolean archiver,
                                 HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            if (archiver) {
                ventes.archiverClient(a, id);
                flash.addFlashAttribute("succes", "Client archivé : il n'apparaît plus dans les choix.");
            } else {
                ventes.reactiverClient(a, id);
                flash.addFlashAttribute("succes", "Client réactivé.");
            }
            return "redirect:" + PAGE + "/clients/" + id;
        }, PAGE + "/clients/" + id);
    }

    private static Client.Etat saisieClient(Map<String, String> c) {
        return new Client.Etat(null, null, FormatsVentes.lireChoix(Client.Type.class, c.get("type"), "Type de client"),
                c.get("nom"), c.get("contact"), c.get("email"), c.get("telephone"), c.get("adresse"), c.get("ville"),
                c.get("pays"), c.get("nif"), c.get("rccm"),
                FormatsVentes.lireEntier(c.get("delaiPaiement"), "Délai de paiement", 0), c.get("notes"), true, null);
    }

    // ================================================================== catalogue

    @GetMapping("/catalogue")
    public String catalogue(@RequestParam(name = "archives", defaultValue = "false") boolean archives,
                            HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, "catalogue");
        model.addAttribute("articles", ventes.articles(a, archives));
        model.addAttribute("archives", archives);
        model.addAttribute("types", Article.Type.values());
        model.addAttribute("reglages", ventes.reglages(a));
        return "ventes/catalogue";
    }

    @PostMapping("/catalogue")
    public String creerArticle(@RequestParam Map<String, String> champs, HttpServletRequest requete,
                               RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            Article x = ventes.creerArticle(a, saisieArticle(champs, ventes.reglages(a)));
            flash.addFlashAttribute("succes", "« " + x.designation() + " » ajouté au catalogue.");
            return "redirect:" + PAGE + "/catalogue";
        }, PAGE + "/catalogue");
    }

    @PostMapping("/catalogue/{id}")
    public String modifierArticle(@PathVariable UUID id, @RequestParam Map<String, String> champs,
                                  HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.modifierArticle(a, id, saisieArticle(champs, ventes.reglages(a)));
            flash.addFlashAttribute("succes", "Article enregistré. Les documents déjà faits ne changent pas.");
            return "redirect:" + PAGE + "/catalogue";
        }, PAGE + "/catalogue");
    }

    @PostMapping("/catalogue/{id}/archiver")
    public String archiverArticle(@PathVariable UUID id, @RequestParam(name = "archiver") boolean archiver,
                                  HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.archiverArticle(a, id, archiver);
            flash.addFlashAttribute("succes", archiver ? "Article archivé." : "Article réactivé.");
            return "redirect:" + PAGE + "/catalogue" + (archiver ? "" : "?archives=true");
        }, PAGE + "/catalogue");
    }

    private static Article.Etat saisieArticle(Map<String, String> c, ReglagesVentes r) {
        return new Article.Etat(null, null, FormatsVentes.lireChoix(Article.Type.class, c.get("type"), "Type"),
                c.get("reference"), c.get("designation"), c.get("unite"),
                FormatsVentes.lireMontant(c.get("prixHt"), "Prix hors taxes"),
                r.assujettiTva() ? FormatsVentes.lireDecimal(c.get("tauxTva"), "Taux de TVA", r.tauxTva())
                        : BigDecimal.ZERO,
                "on".equals(c.get("suiviStock")),
                FormatsVentes.lireDecimal(c.get("seuilAlerte"), "Seuil d'alerte", null),
                true, null);
    }

    // ================================================================== impayés

    @GetMapping("/impayes")
    public String impayes(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, "impayes");
        model.addAttribute("factures", ventes.impayes(a));
        model.addAttribute("parClient", ventes.duParClient(a));
        model.addAttribute("aujourdHui", ventes.aujourdHui());
        return "ventes/impayes";
    }

    // ================================================================== réglages

    @GetMapping("/reglages")
    public String reglages(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, "reglages");
        model.addAttribute("reglages", ventes.reglages(a));
        return "ventes/reglages";
    }

    @PostMapping("/reglages")
    public String definirReglages(@RequestParam Map<String, String> c, HttpServletRequest requete,
                                  RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.definirReglages(a, "on".equals(c.get("assujettiTva")),
                    FormatsVentes.lireDecimal(c.get("tauxTva"), "Taux de TVA", BigDecimal.ZERO), c.get("mentionTva"),
                    FormatsVentes.lireEntier(c.get("delaiPaiement"), "Délai de paiement", 0),
                    FormatsVentes.lireEntier(c.get("validiteDevis"), "Validité des devis", 30), c.get("conditions"));
            flash.addFlashAttribute("succes", "Réglages des ventes enregistrés.");
            return "redirect:" + PAGE + "/reglages";
        }, PAGE + "/reglages");
    }
}
