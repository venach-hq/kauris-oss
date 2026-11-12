package com.venach.kauris.ventes.achats.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.MontantEnLettres;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.entreprise.EntrepriseController;
import com.venach.kauris.ventes.achats.application.AchatsService;
import com.venach.kauris.ventes.achats.application.PermissionsAchats;
import com.venach.kauris.ventes.achats.domain.Commande;
import com.venach.kauris.ventes.achats.domain.Commande.LigneCommande;
import com.venach.kauris.ventes.achats.domain.FactureFournisseur;
import com.venach.kauris.ventes.achats.domain.Fournisseur;
import com.venach.kauris.ventes.achats.domain.Reception;
import com.venach.kauris.ventes.adapter.web.FormatsVentes;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.stock.application.StockService;
import com.venach.kauris.ventes.stock.domain.Depot;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.venach.kauris.ventes.achats.application.PermissionsAchats.CONSULTER;

/**
 * Les écrans des achats : tableau de bord, fournisseurs, bons de commande (et leur PDF), réceptions, factures
 * fournisseurs et leurs règlements, dettes. Les droits sont contrôlés par le service.
 */
@Controller
@RequestMapping(AchatsController.PAGE)
public class AchatsController {

    public static final String PAGE = "/achats";
    private static final int LIGNES_VIDES = 3;

    private final ContexteCourant contexte;
    private final AchatsService achats;
    private final StockService stock;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public AchatsController(ContexteCourant contexte, AchatsService achats, StockService stock,
                            GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.achats = achats;
        this.stock = stock;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    private void commun(Model model, Acces a, String onglet) {
        model.addAttribute("f", new FormatsVentes(entreprises.consulter(a.tenantId()).devise()));
        model.addAttribute("droits", PermissionsAchats.Droits.de(a));
        model.addAttribute("onglet", onglet);
        model.addAttribute("aujourdHui", achats.aujourdHui());
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

    private static UUID obligatoire(String saisie, String message) {
        UUID id = FormatsVentes.lireId(saisie);
        if (id == null) {
            throw new ErreurMetier(message);
        }
        return id;
    }

    // ================================================================== tableau de bord

    @GetMapping
    public String accueil(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "accueil");
        model.addAttribute("bilan", achats.bilan(a));
        model.addAttribute("dettes", achats.dettes(a).stream().limit(10).toList());
        return "achats/accueil";
    }

    // ================================================================== fournisseurs

    @GetMapping("/fournisseurs")
    public String fournisseurs(@RequestParam(name = "q", required = false) String q,
                               @RequestParam(name = "archives", defaultValue = "false") boolean archives,
                               HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "fournisseurs");
        model.addAttribute("fournisseurs", achats.fournisseurs(a, q, archives));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("archives", archives);
        return "achats/fournisseurs";
    }

    @PostMapping("/fournisseurs")
    public String creerFournisseur(@RequestParam Map<String, String> c, HttpServletRequest requete,
                                   RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            Fournisseur f = achats.creerFournisseur(a, saisie(c));
            flash.addFlashAttribute("succes", "Fournisseur « " + f.nom() + " » ajouté.");
            return "redirect:" + PAGE + "/fournisseurs/" + f.id();
        }, PAGE + "/fournisseurs");
    }

    @GetMapping("/fournisseurs/{id}")
    public String fournisseur(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "fournisseurs");
        Fournisseur f = achats.fournisseur(a, id);
        model.addAttribute("fournisseur", f);
        model.addAttribute("etat", f.etat());
        model.addAttribute("commandes", achats.commandes(a, id));
        List<FactureFournisseur> factures = achats.factures(a, id);
        model.addAttribute("factures", factures);
        model.addAttribute("du", factures.stream().mapToLong(FactureFournisseur::reste).sum());
        return "achats/fournisseur";
    }

    @PostMapping("/fournisseurs/{id}")
    public String modifierFournisseur(@PathVariable UUID id, @RequestParam Map<String, String> c,
                                      HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            if (c.containsKey("archiver")) {
                achats.archiverFournisseur(a, id, Boolean.parseBoolean(c.get("archiver")));
            } else {
                achats.modifierFournisseur(a, id, saisie(c));
            }
            flash.addFlashAttribute("succes", "Fournisseur enregistré.");
            return "redirect:" + PAGE + "/fournisseurs/" + id;
        }, PAGE + "/fournisseurs/" + id);
    }

    private static Fournisseur.Etat saisie(Map<String, String> c) {
        return new Fournisseur.Etat(null, null, c.get("nom"), c.get("contact"), c.get("email"), c.get("telephone"),
                c.get("adresse"), c.get("ville"), c.get("pays"), c.get("nif"), c.get("rccm"),
                FormatsVentes.lireEntier(c.get("delaiPaiement"), "Délai de paiement", 0), c.get("notes"), true, null);
    }

    // ================================================================== commandes

    @GetMapping("/commandes")
    public String commandes(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "commandes");
        model.addAttribute("commandes", achats.commandes(a, null));
        return "achats/commandes";
    }

    @GetMapping("/commandes/nouvelle")
    public String nouvelleCommande(@RequestParam(name = "fournisseur", required = false) String fournisseur,
                                   HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        edition(model, a, null, FormatsVentes.lireId(fournisseur), achats.aujourdHui(), null, "", "", List.of());
        return "achats/edition";
    }

    @PostMapping("/commandes")
    public String creerCommande(HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            Commande c = achats.creerCommande(a, obligatoire(requete.getParameter("fournisseur"),
                            "Choisissez le fournisseur."), date(requete), FormatsVentes.lireDate(requete.getParameter("livraison")),
                    requete.getParameter("objet"), requete.getParameter("notes"), lignes(requete));
            flash.addFlashAttribute("succes", "Commande enregistrée en brouillon.");
            return "redirect:" + PAGE + "/commandes/" + c.id();
        }, PAGE + "/commandes/nouvelle");
    }

    @GetMapping("/commandes/{id}/modifier")
    public String modifierCommande(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Commande c = achats.commande(a, id);
        if (!c.modifiable()) {
            return "redirect:" + PAGE + "/commandes/" + id;
        }
        edition(model, a, c, c.fournisseurId(), c.date(), c.livraisonPrevue(), c.objet(), c.notes(), c.lignes());
        return "achats/edition";
    }

    @PostMapping("/commandes/{id}")
    public String enregistrerCommande(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            achats.modifierCommande(a, id, obligatoire(requete.getParameter("fournisseur"), "Choisissez le fournisseur."),
                    date(requete), FormatsVentes.lireDate(requete.getParameter("livraison")),
                    requete.getParameter("objet"), requete.getParameter("notes"), lignes(requete));
            flash.addFlashAttribute("succes", "Brouillon enregistré.");
            return "redirect:" + PAGE + "/commandes/" + id;
        }, PAGE + "/commandes/" + id + "/modifier");
    }

    private void edition(Model model, Acces a, Commande c, UUID fournisseurId, LocalDate date, LocalDate livraison,
                         String objet, String notes, List<LigneCommande> lignes) {
        commun(model, a, "commandes");
        model.addAttribute("commande", c);
        model.addAttribute("fournisseurId", fournisseurId);
        model.addAttribute("date", date);
        model.addAttribute("livraison", livraison);
        model.addAttribute("objet", objet);
        model.addAttribute("notes", notes);
        model.addAttribute("lignes", lignes);
        model.addAttribute("vides", LIGNES_VIDES);
        model.addAttribute("fournisseurs", achats.fournisseurs(a, null, false));
        model.addAttribute("articles", achats.articles(a));
        model.addAttribute("tauxPropose", achats.tauxPropose(a));
    }

    private static LocalDate date(HttpServletRequest r) {
        LocalDate d = FormatsVentes.lireDate(r.getParameter("date"));
        if (d == null) {
            throw new ErreurMetier("Indiquez la date.");
        }
        return d;
    }

    /** Les lignes saisies ; les lignes entièrement vides sont ignorées. */
    private static List<LigneCommande> lignes(HttpServletRequest r) {
        String[] designations = valeurs(r, "ligne.designation");
        List<LigneCommande> lignes = new ArrayList<>();
        for (int i = 0; i < designations.length; i++) {
            String designation = designations[i];
            String prix = valeur(r, "ligne.prix", i);
            if ((designation == null || designation.isBlank()) && prix.isBlank()) {
                continue;
            }
            String ou = designation == null || designation.isBlank() ? "ligne " + (i + 1) : "« " + designation + " »";
            lignes.add(new LigneCommande(FormatsVentes.lireId(valeur(r, "ligne.article", i)), designation,
                    FormatsVentes.lireDecimal(valeur(r, "ligne.quantite", i), "Quantité (" + ou + ")", BigDecimal.ONE),
                    valeur(r, "ligne.unite", i),
                    prix.isBlank() ? 0 : FormatsVentes.lireMontant(prix, "Prix unitaire (" + ou + ")"),
                    FormatsVentes.lireDecimal(valeur(r, "ligne.tva", i), "TVA (" + ou + ")", BigDecimal.ZERO), null));
        }
        return lignes;
    }

    private static String[] valeurs(HttpServletRequest r, String nom) {
        String[] v = r.getParameterValues(nom);
        return v == null ? new String[0] : v;
    }

    private static String valeur(HttpServletRequest r, String nom, int i) {
        String[] v = valeurs(r, nom);
        return i < v.length && v[i] != null ? v[i] : "";
    }

    @GetMapping("/commandes/{id}")
    public String commande(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "commandes");
        Commande c = achats.commande(a, id);
        model.addAttribute("c", c);
        model.addAttribute("totaux", c.totaux());
        model.addAttribute("receptions", achats.receptions(a, id));
        model.addAttribute("depots", stock.depotsActifs(a.tenantId()));
        model.addAttribute("nomsDepots", stock.depotsActifs(a.tenantId()).stream()
                .collect(Collectors.toMap(Depot::id, Depot::nom)));
        model.addAttribute("factures", achats.factures(a, c.fournisseurId()).stream()
                .filter(x -> id.equals(x.commandeId())).toList());
        return "achats/commande";
    }

    @PostMapping("/commandes/{id}/{action}")
    public String agirCommande(@PathVariable UUID id, @PathVariable String action, HttpServletRequest requete,
                               RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            switch (action) {
                case "envoyer" -> {
                    Commande c = achats.envoyer(a, id);
                    flash.addFlashAttribute("succes", "Commande " + c.numero() + " prête : téléchargez le PDF pour "
                            + "l'envoyer au fournisseur.");
                }
                case "annuler" -> {
                    achats.annulerOuSolder(a, id);
                    flash.addFlashAttribute("succes", "Commande close.");
                }
                case "supprimer" -> {
                    achats.supprimerBrouillon(a, id);
                    flash.addFlashAttribute("succes", "Brouillon supprimé.");
                    return "redirect:" + PAGE + "/commandes";
                }
                case "recevoir" -> {
                    Commande c = achats.commande(a, id);
                    List<BigDecimal> quantites = new ArrayList<>();
                    for (int i = 0; i < c.lignes().size(); i++) {
                        quantites.add(FormatsVentes.lireDecimal(requete.getParameter("recu." + i),
                                "Quantité reçue (" + c.lignes().get(i).designation() + ")", BigDecimal.ZERO));
                    }
                    Reception r = achats.recevoir(a, id, quantites, obligatoire(requete.getParameter("depot"),
                                    "Choisissez le dépôt."), FormatsVentes.lireDate(requete.getParameter("date")),
                            requete.getParameter("reference"));
                    flash.addFlashAttribute("succes", "Réception " + r.numero() + " enregistrée : le stock est à jour.");
                }
                default -> throw new ErreurMetier("Action inconnue.");
            }
            return "redirect:" + PAGE + "/commandes/" + id;
        }, PAGE + "/commandes/" + id);
    }

    // ------------------------------------------------------------------ bon de commande et bon de réception

    @GetMapping("/commandes/{id}/imprimer")
    public String imprimer(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        model.addAllAttributes(bonDeCommande(a, id, requete.getContextPath() + PAGE + "/commandes/" + id + ".pdf",
                false));
        return "achats/doc-commande";
    }

    @GetMapping("/commandes/{id}.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Map<String, Object> v = bonDeCommande(a, id, null, true);
        return EntrepriseController.enPdf(producteur.pdf("achats/doc-commande", v),
                ProducteurDocuments.nomDeFichier(String.valueOf(v.get("nomFichier")), null));
    }

    private Map<String, Object> bonDeCommande(Acces a, UUID id, String lienPdf, boolean pdf) {
        Commande c = achats.commande(a, id);
        Fournisseur fo = achats.fournisseur(a, c.fournisseurId());
        boolean brouillon = c.modifiable();
        Habillage h = producteur.habillage(a.tenantId(), "Bon de commande",
                brouillon ? "brouillon, sans valeur" : "n° " + c.numero(), lienPdf, pdf);
        FormatsVentes f = new FormatsVentes(h.entreprise().devise());
        List<Cartouche.Case> cases = new ArrayList<>(List.of(
                Cartouche.c("Commande n°", brouillon ? "Brouillon" : c.numero()),
                Cartouche.c("Date", f.jour(c.date()))));
        if (c.livraisonPrevue() != null) {
            cases.add(Cartouche.c("Livraison souhaitée", f.jour(c.livraisonPrevue())));
        }
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", f);
        v.put("c", c);
        v.put("fournisseur", fo);
        v.put("t", c.totaux());
        v.put("brouillon", brouillon);
        v.put("cartouche", new Cartouche(cases));
        v.put("enLettres", MontantEnLettres.montant(c.totaux().totalTtc(), f.devise()));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("Bon pour commande (cachet et signature)"));
        v.put("nomFichier", brouillon ? "bon de commande brouillon" : "bon de commande " + c.numero());
        return v;
    }

    @GetMapping("/receptions/{id}")
    public String bonDeReception(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        model.addAllAttributes(bonDeReception(a, id, requete.getContextPath() + PAGE + "/receptions/" + id + ".pdf",
                false));
        return "achats/doc-reception";
    }

    @GetMapping("/receptions/{id}.pdf")
    public ResponseEntity<byte[]> bonDeReceptionPdf(@PathVariable UUID id, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Map<String, Object> v = bonDeReception(a, id, null, true);
        return EntrepriseController.enPdf(producteur.pdf("achats/doc-reception", v),
                ProducteurDocuments.nomDeFichier("bon de reception " + v.get("numero"), null));
    }

    private Map<String, Object> bonDeReception(Acces a, UUID id, String lienPdf, boolean pdf) {
        Reception r = achats.reception(a, id);
        Habillage h = producteur.habillage(a.tenantId(), "Bon de réception", "n° " + r.numero(), lienPdf, pdf);
        FormatsVentes f = new FormatsVentes(h.entreprise().devise());
        String depot = stock.depotsActifs(a.tenantId()).stream().filter(d -> d.id().equals(r.depotId()))
                .map(Depot::nom).findFirst().orElse("");
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", f);
        v.put("r", r);
        v.put("numero", r.numero());
        v.put("cartouche", Cartouche.de(Cartouche.c("Réception n°", r.numero()), Cartouche.c("Date", f.jour(r.date())),
                Cartouche.c("Fournisseur", r.fournisseurNom()), Cartouche.c("Commande", r.commandeNumero()),
                Cartouche.c("Bon de livraison", r.reference()), Cartouche.c("Dépôt", depot)));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("Visa du magasinier", "Visa du livreur"));
        return v;
    }

    // ================================================================== factures fournisseurs

    @GetMapping("/factures")
    public String factures(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "factures");
        model.addAttribute("factures", achats.factures(a, null));
        return "achats/factures";
    }

    @GetMapping("/dettes")
    public String dettes(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "dettes");
        model.addAttribute("factures", achats.dettes(a));
        return "achats/factures";
    }

    @GetMapping("/factures/nouvelle")
    public String nouvelleFacture(@RequestParam(name = "fournisseur", required = false) String fournisseur,
                                  @RequestParam(name = "commande", required = false) String commande,
                                  HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "factures");
        UUID commandeId = FormatsVentes.lireId(commande);
        Commande c = commandeId == null ? null : achats.commande(a, commandeId);
        model.addAttribute("commande", c);
        model.addAttribute("fournisseurId", c != null ? c.fournisseurId() : FormatsVentes.lireId(fournisseur));
        model.addAttribute("fournisseurs", achats.fournisseurs(a, null, false));
        model.addAttribute("ht", c == null ? "" : String.valueOf(c.totaux().totalHt()));
        model.addAttribute("tva", c == null ? "" : String.valueOf(c.totaux().totalTva()));
        return "achats/facture-nouvelle";
    }

    @PostMapping("/factures")
    public String enregistrerFacture(@RequestParam Map<String, String> c, HttpServletRequest requete,
                                     RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            FactureFournisseur x = achats.enregistrerFacture(a, obligatoire(c.get("fournisseur"),
                            "Choisissez le fournisseur."), c.get("reference"), FormatsVentes.lireDate(c.get("date")),
                    FormatsVentes.lireDate(c.get("echeance")), c.get("objet"),
                    FormatsVentes.lireMontant(c.get("ht"), "Montant hors taxes"),
                    c.get("tva") == null || c.get("tva").isBlank() ? 0 : FormatsVentes.lireMontant(c.get("tva"), "TVA"),
                    FormatsVentes.lireId(c.get("commande")));
            flash.addFlashAttribute("succes", "Facture enregistrée (" + x.numero() + ").");
            return "redirect:" + PAGE + "/factures/" + x.id();
        }, PAGE + "/factures/nouvelle" + (c.get("commande") == null || c.get("commande").isBlank() ? ""
                : "?commande=" + c.get("commande")));
    }

    @GetMapping("/factures/{id}")
    public String facture(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, "factures");
        FactureFournisseur x = achats.facture(a, id);
        model.addAttribute("x", x);
        model.addAttribute("reglements", achats.reglements(a, id));
        model.addAttribute("modes", ModeReglement.values());
        model.addAttribute("comptes", achats.comptesDeDecaissement(a));
        model.addAttribute("actions", achats.actionsDeDecaissement(a, x));
        return "achats/facture";
    }

    @PostMapping("/factures/{id}/reglements")
    public String payer(@PathVariable UUID id, @RequestParam Map<String, String> c, HttpServletRequest requete,
                        RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            achats.payer(a, id, FormatsVentes.lireDate(c.get("date")), FormatsVentes.lireMontant(c.get("montant"),
                            "Montant"), FormatsVentes.lireChoix(ModeReglement.class, c.get("mode"), "Mode de règlement"),
                    c.get("reference"), FormatsVentes.lireId(c.get("compte")));
            flash.addFlashAttribute("succes", "Règlement enregistré.");
            return "redirect:" + PAGE + "/factures/" + id;
        }, PAGE + "/factures/" + id);
    }

    @PostMapping("/factures/{id}/reglements/{reglement}/annuler")
    public String annulerReglement(@PathVariable UUID id, @PathVariable UUID reglement,
                                   @RequestParam(name = "motif", required = false) String motif,
                                   HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            achats.annulerReglement(a, reglement, motif);
            flash.addFlashAttribute("succes", "Règlement annulé ; il reste visible, barré.");
            return "redirect:" + PAGE + "/factures/" + id;
        }, PAGE + "/factures/" + id);
    }

    @PostMapping("/factures/{id}/annuler")
    public String annulerFacture(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            achats.annulerFacture(a, id);
            flash.addFlashAttribute("succes", "Facture annulée.");
            return "redirect:" + PAGE + "/factures/" + id;
        }, PAGE + "/factures/" + id);
    }
}
