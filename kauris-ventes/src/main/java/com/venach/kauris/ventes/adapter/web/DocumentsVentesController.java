package com.venach.kauris.ventes.adapter.web;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.MontantEnLettres;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
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
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.venach.kauris.ventes.adapter.web.VentesController.PAGE;
import static com.venach.kauris.ventes.adapter.web.VentesController.agir;
import static com.venach.kauris.ventes.adapter.web.VentesController.commun;
import static com.venach.kauris.ventes.application.PermissionsVentes.CONSULTER;
import static com.venach.kauris.ventes.application.PermissionsVentes.ENCAISSER;
import static com.venach.kauris.ventes.application.PermissionsVentes.SAISIR;

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
    private final Ports.Courriels courriels;

    public DocumentsVentesController(ContexteCourant contexte, VentesService ventes,
                                     GererEntrepriseUseCase entreprises, ProducteurDocuments producteur) {
        this(contexte, ventes, entreprises, producteur, Ports.Courriels.AUCUN);
    }

    public DocumentsVentesController(ContexteCourant contexte, VentesService ventes,
                                     GererEntrepriseUseCase entreprises, ProducteurDocuments producteur,
                                     Ports.Courriels courriels) {
        this.courriels = courriels;
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
        model.addAttribute("saisie", contenu);    // « contenu » est pris par le gabarit commun
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
        model.addAttribute("comptes", ventes.comptesDEncaissement(a));
        model.addAttribute("actions", ventes.actionsDEncaissement(a, d));
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
                    c.get("reference"), FormatsVentes.lireId(c.get("compte")));
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

    // ================================================================== envoi par email

    /** Ce que l'écran d'envoi montre et ce qui part. */
    public record Envoi(String titre, String retour, String action, String destinataire, String sujet, String texte,
                        String nomPiece, String lienPiece, boolean disponible, String mailto) {
    }

    @GetMapping("/documents/{id}/envoyer")
    public String preparerEnvoi(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        DocumentVente d = ventes.document(a, id);
        commun(model, a, entreprises, d.type().name().toLowerCase());
        model.addAttribute("envoi", envoiDocument(a, d, requete.getContextPath()));
        return "ventes/envoi";
    }

    @PostMapping("/documents/{id}/envoyer")
    public String envoyer(@PathVariable UUID id, @RequestParam Map<String, String> c, HttpServletRequest requete,
                          RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return agir(flash, () -> {
            DocumentVente d = ventes.document(a, id);
            if (d.modifiable()) {
                throw new ErreurMetier("Émettez le document avant de l'envoyer.");
            }
            Map<String, Object> v = piece(a, id, null, true);
            partir(a, c, new Ports.Courriels.Piece(ProducteurDocuments.nomDeFichier(
                    String.valueOf(v.get("nomFichier")), null), producteur.pdf("ventes/doc-vente", v)));
            flash.addFlashAttribute("succes", d.intitule() + " envoyé à " + c.get("destinataire") + ".");
            return "redirect:" + PAGE + "/documents/" + id;
        }, PAGE + "/documents/" + id + "/envoyer");
    }

    @GetMapping("/clients/{id}/relance")
    public String preparerRelance(@PathVariable UUID id, HttpServletRequest requete, Model model,
                                  RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return agir(flash, () -> {
            model.addAttribute("envoi", envoiRelance(a, id, requete.getContextPath()));
            commun(model, a, entreprises, "impayes");
            return "ventes/envoi";
        }, PAGE + "/clients/" + id);
    }

    @PostMapping("/clients/{id}/relance")
    public String relancer(@PathVariable UUID id, @RequestParam Map<String, String> c, HttpServletRequest requete,
                           RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return agir(flash, () -> {
            LocalDate debut = debutRelance(a, id);
            Map<String, Object> v = releve(a, id, debut.toString(), ventes.aujourdHui().toString(), null, true);
            partir(a, c, new Ports.Courriels.Piece(ProducteurDocuments.nomDeFichier("releve client",
                    String.valueOf(v.get("suffixe"))), producteur.pdf("ventes/doc-releve", v)));
            flash.addFlashAttribute("succes", "Relance envoyée à " + c.get("destinataire") + ", relevé joint.");
            return "redirect:" + PAGE + "/clients/" + id;
        }, PAGE + "/clients/" + id + "/relance");
    }

    /** Envoie l'email saisi ; seules les personnes qui peuvent encaisser ou saisir envoient. */
    private void partir(Acces a, Map<String, String> c, Ports.Courriels.Piece piece) {
        if (!a.peut(SAISIR)
                && !a.peut(ENCAISSER)) {
            throw new AccesRefuse("Vos rôles ne vous permettent pas d'écrire aux "
                    + "clients.");
        }
        if (!courriels.disponible()) {
            throw new ErreurMetier("L'envoi d'emails n'est pas disponible ici : utilisez « Ouvrir dans ma "
                    + "messagerie ».");
        }
        String destinataire = Saisie.email(c.get("destinataire"));
        String sujet = c.getOrDefault("sujet", "").strip();
        String texte = c.getOrDefault("texte", "").strip();
        if (sujet.isEmpty() || sujet.length() > 200 || texte.isEmpty() || texte.length() > 5000) {
            throw new ErreurMetier("Objet (200 caractères au plus) et message (5 000 au plus) obligatoires.");
        }
        var e = entreprises.consulter(a.tenantId());
        // Les réponses vont à l'entreprise, ou à la personne qui écrit si l'entreprise n'a pas d'email.
        String repondreA = e.email() == null || e.email().isBlank() ? a.email() : e.email();
        courriels.envoyer(a.tenantId(), destinataire, repondreA, e.raisonSociale(), sujet, texte, piece);
    }

    private Envoi envoiDocument(Acces a, DocumentVente d, String contexteWeb) {
        var e = entreprises.consulter(a.tenantId());
        FormatsVentes f = new FormatsVentes(e.devise());
        String quoi = switch (d.type()) {
            case DEVIS -> "notre devis";
            case AVOIR -> "notre avoir";
            case FACTURE -> "notre facture";
        };
        StringBuilder t = new StringBuilder("Bonjour")
                .append(d.client().contact().isEmpty() ? "" : " " + d.client().contact()).append(",\n\n")
                .append("Veuillez trouver ci-joint ").append(quoi).append(" ").append(d.numero() == null ? "" : d.numero())
                .append(" du ").append(f.jour(d.contenu().date())).append(", d'un montant de ")
                .append(f.montant(d.totaux().totalTtc())).append(d.totaux().avecTva() ? " TTC" : "");
        if (d.type() == TypeDocument.DEVIS && d.contenu().echeance() != null) {
            t.append(", valable jusqu'au ").append(f.jour(d.contenu().echeance()));
        }
        if (d.type() == TypeDocument.FACTURE && d.contenu().echeance() != null && d.resteAPayer() > 0) {
            t.append(", à régler au plus tard le ").append(f.jour(d.contenu().echeance()));
        }
        t.append(".\n");
        if (d.type() == TypeDocument.FACTURE && e.banque().renseignees() && d.resteAPayer() > 0) {
            t.append("\nRèglement par virement : ").append(e.banque().ligne()).append("\n");
        }
        t.append(signature(a, e));
        String sujet = d.intitule() + " · " + e.raisonSociale();
        return new Envoi("Envoyer " + d.intitule().toLowerCase(Locale.FRENCH), PAGE + "/documents/" + d.id(),
                PAGE + "/documents/" + d.id() + "/envoyer", d.client().email(), sujet, t.toString(),
                d.intitule() + " (PDF)", contexteWeb + PAGE + "/documents/" + d.id() + ".pdf", courriels.disponible(),
                mailto(d.client().email(), sujet, t.toString()));
    }

    private Envoi envoiRelance(Acces a, UUID clientId, String contexteWeb) {
        var e = entreprises.consulter(a.tenantId());
        FormatsVentes f = new FormatsVentes(e.devise());
        Client c = ventes.client(a, clientId);
        List<DocumentVente> dues = ventes.documents(a, new Ports.Filtre(TypeDocument.FACTURE, clientId, null, null,
                        null, true, 0)).stream().filter(d -> d.resteAPayer() > 0)
                .sorted(Comparator.comparing(d -> d.contenu().date())).toList();
        if (dues.isEmpty()) {
            throw new ErreurMetier("Ce client n'a aucune facture à régler.");
        }
        LocalDate jour = ventes.aujourdHui();
        long total = 0;
        StringBuilder t = new StringBuilder("Bonjour")
                .append(c.etat().contact().isEmpty() ? "" : " " + c.etat().contact()).append(",\n\n")
                .append("Sauf erreur de notre part, les factures suivantes restent à régler :\n\n");
        for (DocumentVente d : dues) {
            total += d.resteAPayer();
            t.append("- ").append(d.numero()).append(" du ").append(f.jour(d.contenu().date()));
            if (d.contenu().echeance() != null) {
                long retard = ChronoUnit.DAYS.between(d.contenu().echeance(), jour);
                t.append(", échéance ").append(f.jour(d.contenu().echeance()))
                        .append(retard > 0 ? " (en retard de " + retard + " jour" + (retard > 1 ? "s" : "") + ")" : "");
            }
            t.append(" : ").append(f.montant(d.resteAPayer())).append("\n");
        }
        t.append("\nTotal à régler : ").append(f.montant(total)).append(".\n")
                .append("Vous trouverez ci-joint votre relevé de compte.\n");
        if (e.banque().renseignees()) {
            t.append("\nRèglement par virement : ").append(e.banque().ligne()).append("\n");
        }
        t.append("\nSi votre paiement est déjà parti, merci de ne pas tenir compte de ce message.\n").append(signature(a, e));
        String sujet = "Factures en attente de règlement · " + e.raisonSociale();
        return new Envoi("Relancer " + c.nom(), PAGE + "/clients/" + clientId, PAGE + "/clients/" + clientId + "/relance",
                c.email(), sujet, t.toString(), "Relevé de compte (PDF)",
                contexteWeb + PAGE + "/clients/" + clientId + "/releve.pdf?du=" + debutRelance(a, clientId),
                courriels.disponible(), mailto(c.email(), sujet, t.toString()));
    }

    /** Le relevé joint à une relance part de la plus ancienne facture impayée. */
    private LocalDate debutRelance(Acces a, UUID clientId) {
        return ventes.documents(a, new Ports.Filtre(TypeDocument.FACTURE, clientId, null, null, null, true, 0)).stream()
                .filter(d -> d.resteAPayer() > 0).map(d -> d.contenu().date()).min(Comparator.naturalOrder())
                .orElse(ventes.aujourdHui().withDayOfYear(1));
    }

    private static String signature(Acces a, Entreprise e) {
        StringBuilder s = new StringBuilder("\nNous restons à votre disposition.\n\nCordialement,\n")
                .append(a.nomMembre()).append("\n").append(e.raisonSociale());
        if (!e.telephone().isEmpty()) {
            s.append("\n").append(e.telephone());
        }
        return s.toString();
    }

    /** Un lien « mailto: » pour ouvrir l'email dans la messagerie de la personne (texte coupé s'il est très long). */
    static String mailto(String destinataire, String sujet, String texte) {
        String corps = texte.length() > 1500 ? texte.substring(0, 1500) + "…" : texte;
        return "mailto:" + (destinataire == null ? "" : destinataire) + "?subject="
                + UriUtils.encodeQueryParam(sujet, StandardCharsets.UTF_8)
                + "&body=" + UriUtils.encodeQueryParam(corps,
                StandardCharsets.UTF_8);
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
