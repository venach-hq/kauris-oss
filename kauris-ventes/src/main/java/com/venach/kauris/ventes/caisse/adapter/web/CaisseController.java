package com.venach.kauris.ventes.caisse.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.entreprise.EntrepriseController;
import com.venach.kauris.ventes.adapter.web.FormatsVentes;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.caisse.application.CaisseService;
import com.venach.kauris.ventes.caisse.application.CaisseService.Encaissement;
import com.venach.kauris.ventes.caisse.domain.SessionCaisse;
import com.venach.kauris.ventes.caisse.domain.TicketCaisse;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.ReglagesVentes;
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
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.venach.kauris.ventes.application.PermissionsVentes.CAISSE;

/** La caisse : ouverture, écran de vente, ticket, clôture, rapports de caisse et calculatrice. */
@Controller
@RequestMapping(CaisseController.PAGE)
public class CaisseController {

    public static final String PAGE = "/caisse";

    /** Un article tel que l'écran de vente le montre : prix TTC calculé comme sur la facture. */
    public record ArticleCaisse(UUID id, String reference, String designation, String unite, long prixHt,
                                BigDecimal taux, long prixTtc) {
    }

    private final ContexteCourant contexte;
    private final CaisseService caisse;
    private final VentesService ventes;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public CaisseController(ContexteCourant contexte, CaisseService caisse, VentesService ventes,
                            GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.caisse = caisse;
        this.ventes = ventes;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    private FormatsVentes formats(Acces a) {
        return new FormatsVentes(entreprises.consulter(a.tenantId()).devise());
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

    // ================================================================== ouverture et vente

    @GetMapping
    public String accueil(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CAISSE);
        FormatsVentes f = formats(a);
        model.addAttribute("f", f);
        model.addAttribute("onglet", "vente");
        model.addAttribute("tauxTva", ventes.reglages(a).tauxPropose());
        var ouverte = caisse.ouverte(a);
        if (ouverte.isEmpty()) {
            model.addAttribute("comptes", caisse.comptes(a));
            model.addAttribute("derniere", caisse.derniere(a).orElse(null));
            model.addAttribute("sessions", caisse.sessions(a, 10));
            return "caisse/ouverture";
        }
        SessionCaisse s = ouverte.get();
        ReglagesVentes r = ventes.reglages(a);
        model.addAttribute("session", s);
        model.addAttribute("bilan", caisse.bilan(a, s));
        model.addAttribute("perimee", !s.jour().equals(caisse.aujourdHui()));
        model.addAttribute("articles", ventes.articles(a, false).stream().map(x -> article(x, r)).toList());
        model.addAttribute("clients", ventes.clients(a, null, false));
        model.addAttribute("mobile", s.compteMobile() != null || caisse.comptes(a).isEmpty());
        model.addAttribute("carte", s.compteCarte() != null || caisse.comptes(a).isEmpty());
        return "caisse/vente";
    }

    private static ArticleCaisse article(Article x, ReglagesVentes r) {
        BigDecimal taux = r.assujettiTva() ? x.tauxTva() : BigDecimal.ZERO;
        long tva = BigDecimal.valueOf(x.prixHt()).multiply(taux).divide(BigDecimal.valueOf(100), 0,
                RoundingMode.HALF_UP).longValueExact();
        return new ArticleCaisse(x.id(), x.reference(), x.designation(), x.unite(), x.prixHt(), taux,
                x.prixHt() + tva);
    }

    @PostMapping("/ouvrir")
    public String ouvrir(@RequestParam Map<String, String> c, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CAISSE);
        return agir(flash, () -> {
            String fond = c.getOrDefault("fond", "").strip();
            long montantFond = fond.isEmpty() ? 0 : FormatsVentes.lireMontant(fond, "Fond de caisse");
            SessionCaisse s = caisse.ouvrir(a, montantFond,
                    FormatsVentes.lireId(c.get("compteEspeces")), FormatsVentes.lireId(c.get("compteMobile")),
                    FormatsVentes.lireId(c.get("compteCarte")));
            flash.addFlashAttribute("succes", "Caisse " + s.numero() + " ouverte. Bonnes ventes !");
            return "redirect:" + PAGE;
        }, PAGE);
    }

    /**
     * Le ticket arrive en listes parallèles : {@code article} et {@code quantite}, une valeur par ligne.
     */
    @PostMapping("/vendre")
    public String vendre(@RequestParam(name = "article", required = false) List<String> articles,
                         @RequestParam(name = "quantite", required = false) List<String> quantites,
                         @RequestParam Map<String, String> c, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CAISSE);
        return agir(flash, () -> {
            Map<UUID, BigDecimal> panier = new LinkedHashMap<>();
            List<String> ids = articles == null ? List.of() : articles;
            List<String> qs = quantites == null ? List.of() : quantites;
            if (ids.size() != qs.size()) {
                throw new ErreurMetier("Ticket incomplet : recommencez.");
            }
            for (int i = 0; i < ids.size(); i++) {
                UUID id = FormatsVentes.lireId(ids.get(i));
                BigDecimal q = FormatsVentes.lireDecimal(qs.get(i), "Quantité", null);
                if (id == null || q == null) {
                    throw new ErreurMetier("Ticket incomplet : recommencez.");
                }
                panier.merge(id, q, BigDecimal::add);
            }
            TicketCaisse t = caisse.vendre(a, FormatsVentes.lireId(c.get("client")), panier,
                    FormatsVentes.lireDecimal(c.get("remise"), "Remise", BigDecimal.ZERO),
                    new Encaissement(montant(c.get("especes"), "Espèces reçues"),
                            montant(c.get("mobile"), "Mobile Money"), c.getOrDefault("referenceMobile", ""),
                            montant(c.get("carte"), "Carte")));
            return "redirect:" + PAGE + "/tickets/" + t.documentId();
        }, PAGE);
    }

    private static long montant(String saisi, String champ) {
        return saisi == null || saisi.isBlank() ? 0 : FormatsVentes.lireMontant(saisi, champ);
    }

    @GetMapping("/tickets/{documentId}")
    public String ticket(@PathVariable UUID documentId, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CAISSE);
        TicketCaisse t = caisse.ticket(a, documentId);
        model.addAttribute("t", t);
        model.addAttribute("d", ventes.document(a, documentId));
        model.addAttribute("session", caisse.session(a, t.sessionId()));
        model.addAttribute("entreprise", entreprises.consulter(a.tenantId()));
        model.addAttribute("f", formats(a));
        return "caisse/ticket";
    }

    // ================================================================== clôture et rapports

    @GetMapping("/cloture")
    public String cloture(HttpServletRequest requete, Model model, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CAISSE);
        var ouverte = caisse.ouverte(a);
        if (ouverte.isEmpty()) {
            flash.addFlashAttribute("erreur", "Aucune caisse ouverte.");
            return "redirect:" + PAGE;
        }
        model.addAttribute("f", formats(a));
        model.addAttribute("onglet", "cloture");
        model.addAttribute("session", ouverte.get());
        model.addAttribute("bilan", caisse.bilan(a, ouverte.get()));
        return "caisse/cloture";
    }

    @PostMapping("/sessions/{id}/cloturer")
    public String cloturer(@PathVariable UUID id, @RequestParam Map<String, String> c, HttpServletRequest requete,
                           RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CAISSE);
        return agir(flash, () -> {
            SessionCaisse s = caisse.cloturer(a, id, FormatsVentes.lireMontant(c.getOrDefault("comptees", ""),
                    "Espèces comptées"), c.getOrDefault("note", ""));
            flash.addFlashAttribute("succes", "Caisse " + s.numero() + " clôturée"
                    + (s.ecart() == 0 ? ", sans écart." : ", écart de " + formats(a).montant(s.ecart()) + "."));
            return "redirect:" + PAGE + "/sessions/" + id;
        }, PAGE + "/cloture");
    }

    @GetMapping("/sessions")
    public String sessions(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CAISSE);
        model.addAttribute("f", formats(a));
        model.addAttribute("onglet", "sessions");
        List<SessionCaisse> liste = caisse.sessions(a, 100);
        model.addAttribute("sessions", liste);
        Map<UUID, CaisseService.Bilan> bilans = new HashMap<>();
        liste.forEach(s -> bilans.put(s.id(), caisse.bilan(a, s)));
        model.addAttribute("bilans", bilans);
        return "caisse/sessions";
    }

    @GetMapping("/sessions/{id}")
    public String rapport(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CAISSE);
        model.addAllAttributes(rapport(a, id, requete.getContextPath() + PAGE + "/sessions/" + id + ".pdf", false));
        return "caisse/doc-session";
    }

    @GetMapping("/sessions/{id}.pdf")
    public ResponseEntity<byte[]> rapportPdf(@PathVariable UUID id, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CAISSE);
        Map<String, Object> v = rapport(a, id, null, true);
        SessionCaisse s = (SessionCaisse) v.get("session");
        return EntrepriseController.enPdf(producteur.pdf("caisse/doc-session", v),
                ProducteurDocuments.nomDeFichier("rapport de caisse", s.numero()));
    }

    private Map<String, Object> rapport(Acces a, UUID id, String lienPdf, boolean pdf) {
        SessionCaisse s = caisse.session(a, id);
        CaisseService.Bilan b = caisse.bilan(a, s);
        FormatsVentes f0 = formats(a);
        String titre = s.ouverte() ? "Situation de caisse" : "Rapport de clôture de caisse";
        Habillage h = producteur.habillage(a.tenantId(), titre, s.numero() + " · " + f0.jour(s.jour()), lienPdf,
                pdf);
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", new FormatsVentes(h.entreprise().devise()));
        v.put("session", s);
        v.put("bilan", b);
        v.put("tickets", caisse.tickets(a, s));
        v.put("cartouche", Cartouche.de(Cartouche.c("Caisse", s.numero()), Cartouche.c("Caissier", s.caissier()),
                Cartouche.c("Ouverte le", f0.instant(s.ouverteLe())),
                s.ouverte() ? Cartouche.c("État", "ouverte") : Cartouche.c("Clôturée le", f0.instant(s.closeLe()))));
        v.put("etabliPar", s.ouverte() ? a.nomMembre() : s.closePar());
        v.put("visas", List.of("Visa du caissier", "Visa du responsable"));
        return v;
    }

    // ================================================================== calculatrice

    @GetMapping("/calculatrice")
    public String calculatrice(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CAISSE);
        model.addAttribute("onglet", "calculatrice");
        model.addAttribute("tauxTva", ventes.reglages(a).tauxPropose());
        return "caisse/calculatrice";
    }
}
