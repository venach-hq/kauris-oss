package com.venach.kauris.ventes.adapter.web;

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
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.DocumentVente.Contenu;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.domain.ReglagesVentes;
import com.venach.kauris.ventes.domain.TypeDocument;
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
import java.util.function.Consumer;

import static com.venach.kauris.ventes.adapter.web.VentesController.PAGE;
import static com.venach.kauris.ventes.adapter.web.VentesController.agir;
import static com.venach.kauris.ventes.adapter.web.VentesController.commun;
import static com.venach.kauris.ventes.application.PermissionsVentes.CONSULTER;

/**
 * Les devis, factures et avoirs : liste, saisie des brouillons, émission, suite (facturer un devis, faire un
 * avoir), règlements ; le document lui-même à l'écran et en PDF, et le relevé de compte d'un client.
 */
@Controller
@RequestMapping(PAGE)
public class DocumentsVentesController {

    private static final int LIGNES_VIDES = 3;

    private final ContexteCourant contexte;
    private final VentesService ventes;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public DocumentsVentesController(ContexteCourant contexte, VentesService ventes,
                                     GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.ventes = ventes;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    // ================================================================== liste

    @GetMapping("/documents")
    public String liste(@RequestParam(name = "type", required = false) String type,
                        @RequestParam(name = "q", required = false) String recherche,
                        @RequestParam(name = "du", required = false) String du,
                        @RequestParam(name = "au", required = false) String au,
                        HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        TypeDocument t = type == null || type.isBlank() ? null
                : FormatsVentes.lireChoix(TypeDocument.class, type, "Type de document");
        commun(model, a, entreprises, t == null ? "documents" : t.name().toLowerCase());
        model.addAttribute("documents", ventes.documents(a, new Ports.Filtre(t, null, FormatsVentes.lireDate(du),
                FormatsVentes.lireDate(au), recherche, false, 300)));
        model.addAttribute("type", t);
        model.addAttribute("q", recherche == null ? "" : recherche);
        model.addAttribute("du", du == null ? "" : du);
        model.addAttribute("au", au == null ? "" : au);
        model.addAttribute("types", TypeDocument.values());
        model.addAttribute("aujourdHui", ventes.aujourdHui());
        return "ventes/documents";
    }

    // ================================================================== brouillons

    @GetMapping("/documents/nouveau")
    public String nouveau(@RequestParam(name = "type", defaultValue = "FACTURE") String type,
                          @RequestParam(name = "client", required = false) String client,
                          HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        TypeDocument t = FormatsVentes.lireChoix(TypeDocument.class, type, "Type de document");
        if (t == TypeDocument.AVOIR) {
            throw new ElementIntrouvable("Un avoir se fait depuis la facture qu'il corrige.");
        }
        UUID clientId = FormatsVentes.lireId(client);
        edition(model, a, t, null, clientId, ventes.propose(a, t, clientId));
        return "ventes/edition";
    }

    @PostMapping("/documents")
    public String creer(@RequestParam(name = "type") String type, HttpServletRequest requete,
                        RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            TypeDocument t = FormatsVentes.lireChoix(TypeDocument.class, type, "Type de document");
            DocumentVente d = ventes.creerBrouillon(a, t, clientChoisi(requete), contenu(requete));
            flash.addFlashAttribute("succes", t.libelle() + " enregistré en brouillon.");
            return "redirect:" + PAGE + "/documents/" + d.id();
        }, PAGE + "/documents/nouveau?type=" + type);
    }

    @GetMapping("/documents/{id}/modifier")
    public String modifier(@PathVariable UUID id, HttpServletRequest requete, Model model,
                           RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        DocumentVente d = ventes.document(a, id);
        if (!d.modifiable()) {
            flash.addFlashAttribute("erreur", "Ce document est émis : il ne se modifie plus.");
            return "redirect:" + PAGE + "/documents/" + id;
        }
        edition(model, a, d.type(), d, d.client().id(), d.contenu());
        return "ventes/edition";
    }

    @PostMapping("/documents/{id}")
    public String enregistrer(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.modifierBrouillon(a, id, clientChoisi(requete), contenu(requete));
            flash.addFlashAttribute("succes", "Brouillon enregistré.");
            return "redirect:" + PAGE + "/documents/" + id;
        }, PAGE + "/documents/" + id + "/modifier");
    }

    private void edition(Model model, Acces a, TypeDocument type, DocumentVente d, UUID clientId, Contenu contenu) {
        commun(model, a, entreprises, type.name().toLowerCase());
        ReglagesVentes r = ventes.reglages(a);
        List<Ligne> lignes = new ArrayList<>(contenu.lignes());
        model.addAttribute("type", type);
        model.addAttribute("titrePage", d == null
                ? (type == TypeDocument.DEVIS ? "Nouveau devis" : "Nouvelle facture")
                : type.libelle() + " (brouillon)");
        model.addAttribute("document", d);
        model.addAttribute("contenu", contenu);
        model.addAttribute("lignes", lignes);
        model.addAttribute("vides", LIGNES_VIDES);
        model.addAttribute("clientId", clientId);
        List<Client> clients = new ArrayList<>(ventes.clients(a, null, false));
        if (d != null && clients.stream().noneMatch(c -> c.id().equals(d.client().id()))) {
            // client archivé depuis le brouillon : on le montre pour que la personne voie pourquoi c'est refusé
            clients.add(0, ventes.client(a, d.client().id()));
        }
        model.addAttribute("clients", clients);
        model.addAttribute("articles", ventes.articles(a, false));
        model.addAttribute("reglages", r);
        model.addAttribute("tauxPropose", r.tauxPropose());
    }

    private static UUID clientChoisi(HttpServletRequest requete) {
        UUID id = FormatsVentes.lireId(requete.getParameter("client"));
        if (id == null) {
            throw new ErreurMetier("Choisissez le client.");
        }
        return id;
    }

    /** Le contenu saisi ; les lignes entièrement vides sont ignorées. */
    static Contenu contenu(HttpServletRequest r) {
        LocalDate date = FormatsVentes.lireDate(r.getParameter("date"));
        if (date == null) {
            throw new ErreurMetier("Indiquez la date du document.");
        }
        String[] designations = valeurs(r, "ligne.designation");
        String[] articles = valeurs(r, "ligne.article");
        String[] quantites = valeurs(r, "ligne.quantite");
        String[] unites = valeurs(r, "ligne.unite");
        String[] prix = valeurs(r, "ligne.prix");
        String[] remises = valeurs(r, "ligne.remise");
        String[] taux = valeurs(r, "ligne.tva");
        List<Ligne> lignes = new ArrayList<>();
        for (int i = 0; i < designations.length; i++) {
            String designation = designations[i];
            String p = valeur(prix, i);
            if ((designation == null || designation.isBlank()) && p.isBlank()) {
                continue;
            }
            String ou = designation == null || designation.isBlank() ? "ligne " + (i + 1) : "« " + designation + " »";
            lignes.add(new Ligne(FormatsVentes.lireId(valeur(articles, i)), designation,
                    FormatsVentes.lireDecimal(valeur(quantites, i), "Quantité (" + ou + ")", BigDecimal.ONE),
                    valeur(unites, i),
                    p.isBlank() ? 0 : FormatsVentes.lireMontant(p, "Prix unitaire (" + ou + ")"),
                    FormatsVentes.lireDecimal(valeur(remises, i), "Remise (" + ou + ")", BigDecimal.ZERO),
                    FormatsVentes.lireDecimal(valeur(taux, i), "TVA (" + ou + ")", BigDecimal.ZERO)));
        }
        return new Contenu(date, FormatsVentes.lireDate(r.getParameter("echeance")), r.getParameter("objet"),
                r.getParameter("reference"), lignes,
                FormatsVentes.lireDecimal(r.getParameter("remiseGlobale"), "Remise globale", BigDecimal.ZERO),
                r.getParameter("notes"));
    }

    private static String[] valeurs(HttpServletRequest r, String nom) {
        String[] v = r.getParameterValues(nom);
        return v == null ? new String[0] : v;
    }

    private static String valeur(String[] valeurs, int i) {
        return i < valeurs.length && valeurs[i] != null ? valeurs[i] : "";
    }

    // ================================================================== fiche et actions

    @GetMapping("/documents/{id}")
    public String fiche(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        DocumentVente d = ventes.document(a, id);
        commun(model, a, entreprises, d.type().name().toLowerCase());
        model.addAttribute("d", d);
        model.addAttribute("totaux", d.totaux());
        model.addAttribute("aujourdHui", ventes.aujourdHui());
        model.addAttribute("situation", d.situation(ventes.aujourdHui()));
        model.addAttribute("issus", ventes.issusDe(a, id));
        model.addAttribute("reglements", d.type() == TypeDocument.FACTURE ? ventes.reglements(a, id) : List.of());
        model.addAttribute("modes", ModeReglement.values());
        return "ventes/document";
    }

    @PostMapping("/documents/{id}/supprimer")
    public String supprimer(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.supprimerBrouillon(a, id);
            flash.addFlashAttribute("succes", "Brouillon supprimé.");
            return "redirect:" + PAGE + "/documents";
        }, PAGE + "/documents/" + id);
    }

    @PostMapping("/documents/{id}/emettre")
    public String emettre(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            DocumentVente d = ventes.emettre(a, id);
            flash.addFlashAttribute("succes", d.intitule() + " émis.");
            return "redirect:" + PAGE + "/documents/" + id;
        }, PAGE + "/documents/" + id);
    }

    @PostMapping("/documents/{id}/accepter")
    public String accepter(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return devis(id, requete, flash, a -> ventes.accepterDevis(a, id), "Devis marqué accepté.");
    }

    @PostMapping("/documents/{id}/refuser")
    public String refuser(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return devis(id, requete, flash, a -> ventes.refuserDevis(a, id), "Devis marqué refusé.");
    }

    @PostMapping("/documents/{id}/annuler")
    public String annuler(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return devis(id, requete, flash, a -> ventes.annulerDevis(a, id), "Devis annulé.");
    }

    private String devis(UUID id, HttpServletRequest requete, RedirectAttributes flash, Consumer<Acces> action,
                         String message) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            action.accept(a);
            flash.addFlashAttribute("succes", message);
            return "redirect:" + PAGE + "/documents/" + id;
        }, PAGE + "/documents/" + id);
    }

    @PostMapping("/documents/{id}/facturer")
    public String facturer(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            DocumentVente f = ventes.facturerDevis(a, id);
            flash.addFlashAttribute("succes", "Facture préparée à partir du devis : vérifiez-la, puis émettez-la.");
            return "redirect:" + PAGE + "/documents/" + f.id() + "/modifier";
        }, PAGE + "/documents/" + id);
    }

    @PostMapping("/documents/{id}/avoir")
    public String avoir(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            DocumentVente av = ventes.avoirSur(a, id);
            flash.addFlashAttribute("succes", "Avoir préparé avec toutes les lignes de la facture : retirez ou "
                    + "réduisez ce qui ne s'annule pas, puis émettez-le.");
            return "redirect:" + PAGE + "/documents/" + av.id() + "/modifier";
        }, PAGE + "/documents/" + id);
    }

    @PostMapping("/documents/{id}/reglements")
    public String encaisser(@PathVariable UUID id, @RequestParam Map<String, String> c, HttpServletRequest requete,
                            RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.encaisser(a, id, FormatsVentes.lireDate(c.get("date")),
                    FormatsVentes.lireMontant(c.get("montant"), "Montant"),
                    FormatsVentes.lireChoix(ModeReglement.class, c.get("mode"), "Mode de règlement"),
                    c.get("reference"));
            flash.addFlashAttribute("succes", "Règlement enregistré.");
            return "redirect:" + PAGE + "/documents/" + id;
        }, PAGE + "/documents/" + id);
    }

    @PostMapping("/documents/{id}/reglements/{reglement}/annuler")
    public String annulerReglement(@PathVariable UUID id, @PathVariable UUID reglement,
                                   @RequestParam(name = "motif", required = false) String motif,
                                   HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete);
        return agir(flash, () -> {
            ventes.annulerReglement(a, reglement, motif);
            flash.addFlashAttribute("succes", "Règlement annulé ; il reste visible, barré.");
            return "redirect:" + PAGE + "/documents/" + id;
        }, PAGE + "/documents/" + id);
    }

    // ================================================================== le document (écran et PDF)

    @GetMapping("/documents/{id}/imprimer")
    public String imprimer(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        model.addAllAttributes(piece(a, id, requete.getContextPath() + PAGE + "/documents/" + id + ".pdf", false));
        return "ventes/doc-vente";
    }

    @GetMapping("/documents/{id}.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Map<String, Object> v = piece(a, id, null, true);
        return EntrepriseController.enPdf(producteur.pdf("ventes/doc-vente", v),
                ProducteurDocuments.nomDeFichier(String.valueOf(v.get("nomFichier")), null));
    }

    private Map<String, Object> piece(Acces a, UUID id, String lienPdf, boolean pdf) {
        DocumentVente d = ventes.document(a, id);
        boolean brouillon = d.modifiable();
        String precision = brouillon ? "brouillon, sans valeur" : "n° " + d.numero();
        Habillage h = producteur.habillage(a.tenantId(), d.type().libelle(), precision, lienPdf, pdf);
        FormatsVentes f = new FormatsVentes(h.entreprise().devise());
        List<Cartouche.Case> cases = new ArrayList<>();
        cases.add(Cartouche.c(d.type().libelle() + " n°", brouillon ? "Brouillon" : d.numero()));
        cases.add(Cartouche.c("Date", f.jour(d.contenu().date())));
        if (d.contenu().echeance() != null) {
            cases.add(Cartouche.c(d.type() == TypeDocument.DEVIS ? "Valable jusqu'au" : "Échéance",
                    f.jour(d.contenu().echeance())));
        }
        if (!d.contenu().reference().isEmpty()) {
            cases.add(Cartouche.c("Votre référence", d.contenu().reference()));
        }
        if (d.origineNumero() != null) {
            cases.add(Cartouche.c(d.type() == TypeDocument.AVOIR ? "Facture corrigée" : "Devis", d.origineNumero()));
        }
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", f);
        v.put("d", d);
        v.put("t", d.totaux());
        v.put("brouillon", brouillon);
        v.put("cartouche", new Cartouche(cases));
        v.put("enLettres", MontantEnLettres.montant(d.totaux().totalTtc(), f.devise()));
        v.put("avecRemise", d.contenu().lignes().stream().anyMatch(l -> l.remise().signum() > 0));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", d.type() == TypeDocument.DEVIS ? List.of("Bon pour accord du client (date, signature)")
                : List.of("Cachet et signature"));
        v.put("nomFichier", brouillon ? d.type().libelle() + " brouillon" : d.type().libelle() + " " + d.numero());
        return v;
    }

    // ================================================================== relevé de compte client

    @GetMapping("/clients/{id}/releve")
    public String releve(@PathVariable UUID id, @RequestParam(name = "du", required = false) String du,
                         @RequestParam(name = "au", required = false) String au, HttpServletRequest requete,
                         Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        String lien = requete.getContextPath() + PAGE + "/clients/" + id + "/releve.pdf"
                + (requete.getQueryString() == null ? "" : "?" + requete.getQueryString());
        model.addAllAttributes(releve(a, id, du, au, lien, false));
        return "ventes/doc-releve";
    }

    @GetMapping("/clients/{id}/releve.pdf")
    public ResponseEntity<byte[]> relevePdf(@PathVariable UUID id,
                                            @RequestParam(name = "du", required = false) String du,
                                            @RequestParam(name = "au", required = false) String au,
                                            HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Map<String, Object> v = releve(a, id, du, au, null, true);
        return EntrepriseController.enPdf(producteur.pdf("ventes/doc-releve", v),
                ProducteurDocuments.nomDeFichier("releve client", String.valueOf(v.get("suffixe"))));
    }

    private Map<String, Object> releve(Acces a, UUID clientId, String du, String au, String lienPdf, boolean pdf) {
        LocalDate jour = ventes.aujourdHui();
        LocalDate fin = FormatsVentes.lireDate(au) == null ? jour : FormatsVentes.lireDate(au);
        LocalDate debut = FormatsVentes.lireDate(du) == null ? fin.withDayOfYear(1) : FormatsVentes.lireDate(du);
        if (fin.isBefore(debut)) {
            LocalDate x = debut;
            debut = fin;
            fin = x;
        }
        VentesService.Releve r = ventes.releve(a, clientId, debut, fin);
        FormatsVentes f0 = new FormatsVentes(entreprises.consulter(a.tenantId()).devise());
        Habillage h = producteur.habillage(a.tenantId(), "Relevé de compte client",
                "du " + f0.jour(debut) + " au " + f0.jour(fin), lienPdf, pdf);
        FormatsVentes f = new FormatsVentes(h.entreprise().devise());
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("f", f);
        v.put("r", r);
        v.put("aujourdHui", jour);
        v.put("cartouche", Cartouche.de(
                Cartouche.c("Client", r.client().nom()),
                Cartouche.c("Période", "du " + f.jour(debut) + " au " + f.jour(fin)),
                Cartouche.c(r.soldeFinal() >= 0 ? "Reste dû" : "En votre faveur", f.montant(Math.abs(r.soldeFinal()))),
                Cartouche.c("NIF", r.client().nif())));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("Cachet et signature"));
        v.put("suffixe", debut.toString().replace("-", "") + "-" + fin.toString().replace("-", ""));
        return v;
    }
}
