package com.venach.kauris.ventes.stock.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.entreprise.EntrepriseController;
import com.venach.kauris.ventes.adapter.web.FormatsVentes;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.stock.application.PermissionsStock;
import com.venach.kauris.ventes.stock.application.StockService;
import com.venach.kauris.ventes.stock.domain.Depot;
import com.venach.kauris.ventes.stock.domain.MouvementStock;
import com.venach.kauris.ventes.stock.domain.TypeMouvement;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.venach.kauris.ventes.stock.application.PermissionsStock.CONSULTER;

/** Les écrans du stock : état, mouvements, transferts, inventaire, dépôts, fiche d'un article et documents. */
@Controller
@RequestMapping(StockController.PAGE)
public class StockController {

    public static final String PAGE = "/stock";

    private final ContexteCourant contexte;
    private final StockService stock;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public StockController(ContexteCourant contexte, StockService stock, GererEntrepriseUseCase entreprises,
                           ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.stock = stock;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    private void commun(Model model, Acces a, String onglet) {
        model.addAttribute("f", new FormatsVentes(entreprises.consulter(a.tenantId()).devise()));
        model.addAttribute("droits", PermissionsStock.Droits.de(a));
        model.addAttribute("onglet", onglet);
        model.addAttribute("depots", stock.depots(a, false));
    }

    private static String agir(RedirectAttributes flash, Supplier<String> action, String retourEnErreur) {
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

    // ================================================================== état

    @GetMapping
    public String etat(@RequestParam(name = "depot", required = false) String depot,
                       @RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "bas", defaultValue = "false") boolean bas,
                       HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "etat");
        UUID depotId = FormatsVentes.lireId(depot);
        model.addAttribute("etat", stock.etat(a, depotId, q, bas));
        model.addAttribute("depotId", depotId);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("bas", bas);
        return "stock/etat";
    }

    @GetMapping("/articles/{id}")
    public String fiche(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "etat");
        model.addAttribute("fiche", stock.fiche(a, id, null, null));
        model.addAttribute("nomsDepots", noms(a));
        return "stock/fiche";
    }

    @GetMapping("/mouvements")
    public String mouvements(@RequestParam(name = "depot", required = false) String depot,
                             @RequestParam(name = "du", required = false) String du,
                             @RequestParam(name = "au", required = false) String au,
                             HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "mouvements");
        UUID depotId = FormatsVentes.lireId(depot);
        model.addAttribute("mouvements", stock.mouvements(a, depotId, FormatsVentes.lireDate(du),
                FormatsVentes.lireDate(au), 300));
        model.addAttribute("articles", articlesParId(a));
        model.addAttribute("nomsDepots", noms(a));
        model.addAttribute("depotId", depotId);
        model.addAttribute("du", du == null ? "" : du);
        model.addAttribute("au", au == null ? "" : au);
        return "stock/mouvements";
    }

    private Map<UUID, String> noms(Acces a) {
        return stock.depots(a, true).stream().collect(Collectors.toMap(Depot::id, Depot::nom));
    }

    private Map<UUID, Article> articlesParId(Acces a) {
        Map<UUID, Article> r = new HashMap<>();
        stock.etat(a, null, null, false).lignes().forEach(l -> r.put(l.article().id(), l.article()));
        return r;
    }

    // ================================================================== saisies

    @GetMapping("/mouvement")
    public String formulaire(@RequestParam(name = "article", required = false) String article,
                             HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "saisie");
        model.addAttribute("articles", stock.articlesSuivis(a));
        model.addAttribute("types", Arrays.stream(TypeMouvement.values()).filter(TypeMouvement::manuel).toList());
        model.addAttribute("articleId", FormatsVentes.lireId(article));
        model.addAttribute("aujourdHui", stock.aujourdHui());
        return "stock/saisie";
    }

    @PostMapping("/mouvement")
    public String saisir(@RequestParam Map<String, String> c, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            TypeMouvement type = FormatsVentes.lireChoix(TypeMouvement.class, c.get("type"), "Type de mouvement");
            UUID article = obligatoire(c.get("article"), "Choisissez l'article.");
            MouvementStock m = stock.saisir(a, type, article, obligatoire(c.get("depot"), "Choisissez le dépôt."),
                    FormatsVentes.lireDecimal(c.get("quantite"), "Quantité", null),
                    type.entree() ? FormatsVentes.lireMontant(c.get("cout"), "Coût unitaire d'achat") : 0,
                    FormatsVentes.lireDate(c.get("date")), c.get("libelle"), c.get("reference"));
            flash.addFlashAttribute("succes", type.libelle() + " enregistrée (" + m.numero() + ").");
            return "redirect:" + PAGE + "/articles/" + article;
        }, PAGE + "/mouvement");
    }

    @GetMapping("/transfert")
    public String transfert(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "transfert");
        model.addAttribute("articles", stock.articlesSuivis(a));
        model.addAttribute("aujourdHui", stock.aujourdHui());
        return "stock/transfert";
    }

    @PostMapping("/transfert")
    public String transferer(@RequestParam Map<String, String> c, HttpServletRequest requete,
                             RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            UUID article = obligatoire(c.get("article"), "Choisissez l'article.");
            stock.transferer(a, article, obligatoire(c.get("de"), "Choisissez le dépôt de départ."),
                    obligatoire(c.get("vers"), "Choisissez le dépôt d'arrivée."),
                    FormatsVentes.lireDecimal(c.get("quantite"), "Quantité", null),
                    FormatsVentes.lireDate(c.get("date")), c.get("libelle"));
            flash.addFlashAttribute("succes", "Transfert enregistré.");
            return "redirect:" + PAGE + "/articles/" + article;
        }, PAGE + "/transfert");
    }

    @GetMapping("/inventaire")
    public String inventaire(@RequestParam(name = "depot", required = false) String depot,
                             HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "inventaire");
        List<Depot> actifs = stock.depots(a, false);
        UUID depotId = FormatsVentes.lireId(depot) == null ? actifs.get(0).id() : FormatsVentes.lireId(depot);
        model.addAttribute("depotId", depotId);
        model.addAttribute("etat", stock.etat(a, depotId, null, false));
        model.addAttribute("aujourdHui", stock.aujourdHui());
        return "stock/inventaire";
    }

    @PostMapping("/inventaire")
    public String inventorier(@RequestParam Map<String, String> c, HttpServletRequest requete,
                              RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        UUID depot = FormatsVentes.lireId(c.get("depot"));
        return agir(flash, () -> {
            Map<UUID, BigDecimal> comptes = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : c.entrySet()) {
                if (e.getKey().startsWith("compte.") && e.getValue() != null && !e.getValue().isBlank()) {
                    comptes.put(FormatsVentes.lireId(e.getKey().substring(7)),
                            FormatsVentes.lireDecimal(e.getValue(), "Quantité comptée", null));
                }
            }
            if (comptes.isEmpty()) {
                throw new ErreurMetier("Saisissez au moins une quantité comptée.");
            }
            int n = stock.inventorier(a, obligatoire(c.get("depot"), "Choisissez le dépôt."), comptes,
                    FormatsVentes.lireDate(c.get("date")), c.get("reference"));
            flash.addFlashAttribute("succes", n == 0 ? "Inventaire conforme : aucun écart."
                    : n + " écart(s) enregistré(s) en ajustement d'inventaire.");
            return "redirect:" + PAGE + (depot == null ? "" : "?depot=" + depot);
        }, PAGE + "/inventaire" + (depot == null ? "" : "?depot=" + depot));
    }

    private static UUID obligatoire(String saisie, String message) {
        UUID id = FormatsVentes.lireId(saisie);
        if (id == null) {
            throw new ErreurMetier(message);
        }
        return id;
    }

    // ================================================================== dépôts

    @GetMapping("/depots")
    public String depots(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "depots");
        model.addAttribute("tous", stock.depots(a, true));
        return "stock/depots";
    }

    @PostMapping("/depots")
    public String creerDepot(@RequestParam(name = "nom", defaultValue = "") String nom, HttpServletRequest requete,
                             RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            Depot d = stock.creerDepot(a, nom);
            flash.addFlashAttribute("succes", "Dépôt « " + d.nom() + " » créé.");
            return "redirect:" + PAGE + "/depots";
        }, PAGE + "/depots");
    }

    @PostMapping("/depots/{id}")
    public String changerDepot(@PathVariable UUID id, @RequestParam(name = "action") String action,
                               @RequestParam(name = "nom", defaultValue = "") String nom,
                               HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            switch (action) {
                case "renommer" -> stock.renommerDepot(a, id, nom);
                case "principal" -> stock.rendrePrincipal(a, id);
                case "archiver" -> stock.archiverDepot(a, id, true);
                case "reactiver" -> stock.archiverDepot(a, id, false);
                default -> throw new ErreurMetier("Action inconnue.");
            }
            flash.addFlashAttribute("succes", "Dépôt enregistré.");
            return "redirect:" + PAGE + "/depots";
        }, PAGE + "/depots");
    }

    // ================================================================== documents

    @GetMapping("/documents/etat")
    public String documentEtat(@RequestParam(name = "depot", required = false) String depot,
                               HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        String lien = requete.getContextPath() + PAGE + "/documents/etat.pdf"
                + (requete.getQueryString() == null ? "" : "?" + requete.getQueryString());
        model.addAllAttributes(documentEtat(a, FormatsVentes.lireId(depot), lien, false));
        return "stock/doc-etat";
    }

    @GetMapping("/documents/etat.pdf")
    public ResponseEntity<byte[]> documentEtatPdf(@RequestParam(name = "depot", required = false) String depot,
                                                  HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Map<String, Object> v = documentEtat(a, FormatsVentes.lireId(depot), null, true);
        return EntrepriseController.enPdf(producteur.pdf("stock/doc-etat", v),
                ProducteurDocuments.nomDeFichier("etat du stock", stock.aujourdHui().toString().replace("-", "")));
    }

    private Map<String, Object> documentEtat(Acces a, UUID depotId, String lienPdf, boolean pdf) {
        StockService.Etat etat = stock.etat(a, depotId, null, false);
        FormatsVentes f0 = new FormatsVentes(entreprises.consulter(a.tenantId()).devise());
        String ou = etat.depot() == null ? "tous dépôts" : etat.depot().nom();
        Habillage h = producteur.habillage(a.tenantId(), "État du stock", "au " + f0.jour(stock.aujourdHui())
                + " · " + ou, lienPdf, pdf);
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", new FormatsVentes(h.entreprise().devise()));
        v.put("etat", etat);
        v.put("cartouche", Cartouche.de(Cartouche.c("Date", f0.jour(stock.aujourdHui())),
                Cartouche.c("Dépôt", ou), Cartouche.c("Articles", String.valueOf(etat.lignes().size())),
                Cartouche.c("Valeur (CMUP)", f0.montant(etat.valeurTotale()))));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("Visa du magasinier", "Visa du responsable"));
        return v;
    }

    @GetMapping("/articles/{id}/fiche")
    public String documentFiche(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        model.addAllAttributes(documentFiche(a, id, requete.getContextPath() + PAGE + "/articles/" + id + "/fiche.pdf",
                false));
        return "stock/doc-fiche";
    }

    @GetMapping("/articles/{id}/fiche.pdf")
    public ResponseEntity<byte[]> documentFichePdf(@PathVariable UUID id, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return EntrepriseController.enPdf(producteur.pdf("stock/doc-fiche", documentFiche(a, id, null, true)),
                ProducteurDocuments.nomDeFichier("fiche de stock", null));
    }

    private Map<String, Object> documentFiche(Acces a, UUID id, String lienPdf, boolean pdf) {
        StockService.Fiche fiche = stock.fiche(a, id, null, null);
        Habillage h = producteur.habillage(a.tenantId(), "Fiche de stock", fiche.article().designation(), lienPdf, pdf);
        FormatsVentes f = new FormatsVentes(h.entreprise().devise());
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", f);
        v.put("fiche", fiche);
        v.put("nomsDepots", noms(a));
        v.put("cartouche", Cartouche.de(Cartouche.c("Article", fiche.article().designation()),
                Cartouche.c("Référence", fiche.article().reference()),
                Cartouche.c("Quantité", f.decimal(fiche.position().quantite()) + " " + fiche.article().unite()),
                Cartouche.c("Valeur (CMUP)", f.montant(fiche.position().valeur()))));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("Visa du magasinier"));
        return v;
    }
}
