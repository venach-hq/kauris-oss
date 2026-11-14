package com.venach.kauris.flotte.adapter.web;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.document.Cartouche;
import com.venach.kauris.kernel.document.Habillage;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.entreprise.EntrepriseController;
import com.venach.kauris.flotte.application.FlotteService;
import com.venach.kauris.flotte.application.PermissionsFlotte;
import com.venach.kauris.flotte.domain.Compte;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.ModeVersement;
import com.venach.kauris.flotte.domain.Vehicule;
import com.venach.kauris.flotte.domain.Versement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.venach.kauris.flotte.application.PermissionsFlotte.CONSULTER;

/**
 * La caisse de la flotte, pensée pour la caissière : qui doit quoi aujourd'hui, encaisser en trois gestes, le reçu,
 * la demande de paiement aux conducteurs en retard, le journal des versements, les jours fériés et les exports.
 */
@Controller
@RequestMapping(FlotteController.PAGE)
public class FlotteController {

    public static final String PAGE = "/flotte";

    private final ContexteCourant contexte;
    private final FlotteService flotte;
    private final GererEntrepriseUseCase entreprises;
    private final ProducteurDocuments producteur;

    public FlotteController(ContexteCourant contexte, FlotteService flotte, GererEntrepriseUseCase entreprises,
                            ProducteurDocuments producteur) {
        this.contexte = contexte;
        this.flotte = flotte;
        this.entreprises = entreprises;
        this.producteur = producteur;
    }

    /** Ce que tous les écrans de la flotte reçoivent. */
    static FormatsFlotte commun(Model model, Acces a, GererEntrepriseUseCase entreprises, FlotteService flotte,
                                String onglet) {
        FormatsFlotte f = new FormatsFlotte(entreprises.consulter(a.tenantId()).devise());
        model.addAttribute("f", f);
        model.addAttribute("onglet", onglet);
        model.addAttribute("droits", PermissionsFlotte.Droits.de(a));
        model.addAttribute("alertes", flotte.alertes(a));
        return f;
    }

    /** Une erreur prévue devient un message à l'écran ; les autres (accès, introuvable) suivent leur cours. */
    static String agir(RedirectAttributes flash, Supplier<String> action, String retourEnErreur) {
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

    // ================================================================== caisse du jour

    @GetMapping
    public String caisse(@RequestParam(defaultValue = "") String voir, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "caisse");
        List<FlotteService.LigneCaisse> lignes = flotte.caisse(a);
        LocalDate jour = flotte.aujourdHui();
        long encaisse = flotte.journal(a, jour, jour).stream().filter(v -> !v.annule()).mapToLong(Versement::montant)
                .sum();
        model.addAttribute("lignes", "retard".equals(voir) ? lignes.stream().filter(l -> l.compte().enRetard())
                .toList() : lignes);
        model.addAttribute("voir", voir);
        model.addAttribute("aujourdhui", jour);
        model.addAttribute("encaisseAujourdhui", encaisse);
        model.addAttribute("aEncaisser", lignes.stream().mapToLong(l -> l.compte().aEncaisser()).sum());
        model.addAttribute("enRetard", lignes.stream().filter(l -> l.compte().enRetard()).count());
        return "flotte/caisse";
    }

    @GetMapping("/contrats/{id}/encaisser")
    public String preparerEncaissement(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.ENCAISSER);
        commun(model, a, entreprises, flotte, "caisse");
        Contrat c = flotte.contrat(a, id);
        model.addAttribute("c", c);
        model.addAttribute("compte", flotte.compte(a, id));
        model.addAttribute("vehicule", flotte.vehicule(a, c.vehiculeId()));
        model.addAttribute("conducteur", flotte.conducteur(a, c.conducteurId()));
        model.addAttribute("modes", ModeVersement.values());
        model.addAttribute("comptes", flotte.comptesEncaissement(a));
        model.addAttribute("aujourdhui", flotte.aujourdHui());
        return "flotte/encaisser";
    }

    @PostMapping("/contrats/{id}/encaisser")
    public String encaisser(@PathVariable UUID id, @RequestParam Map<String, String> c, HttpServletRequest requete,
                            RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.ENCAISSER);
        return agir(flash, () -> {
            Versement v = flotte.encaisser(a, id, FormatsFlotte.lireMontant(c.get("montant"), "Montant"),
                    FormatsFlotte.lireChoix(ModeVersement.class, c.get("mode"), "Mode de paiement"),
                    c.getOrDefault("reference", ""), FormatsFlotte.lireDate(c.get("date"), "Date"),
                    FormatsFlotte.lireId(c.get("compte")));
            return "redirect:" + PAGE + "/versements/" + v.id();
        }, PAGE + "/contrats/" + id + "/encaisser");
    }

    /** Le reçu du versement, au format ticket (imprimante 80 mm) ou à partager. */
    @GetMapping("/versements/{id}")
    public String recu(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Versement v = flotte.versement(a, id);
        Contrat c = flotte.contrat(a, v.contratId());
        Entreprise e = entreprises.consulter(a.tenantId());
        FormatsFlotte f = new FormatsFlotte(e.devise());
        Compte.Situation s = flotte.compte(a, c.id());
        Vehicule vehicule = flotte.vehicule(a, c.vehiculeId());
        Conducteur conducteur = flotte.conducteur(a, c.conducteurId());
        model.addAttribute("v", v);
        model.addAttribute("c", c);
        model.addAttribute("compte", s);
        model.addAttribute("vehicule", vehicule);
        model.addAttribute("conducteur", conducteur);
        model.addAttribute("entreprise", e);
        model.addAttribute("f", f);
        model.addAttribute("droits", PermissionsFlotte.Droits.de(a));
        model.addAttribute("whatsapp", whatsapp(e, conducteur.telephone(), texteRecu(e, f, v, c, vehicule, s)));
        return "flotte/recu";
    }

    private static String texteRecu(Entreprise e, FormatsFlotte f, Versement v, Contrat c, Vehicule vehicule,
                                    Compte.Situation s) {
        return e.denomination() + "\nReçu " + v.numero() + " du " + f.jour(v.date()) + "\n" + vehicule.immatriculation()
                + " : " + f.montant(v.montant()) + " reçus (" + v.mode().libelle() + ").\n"
                + etat(f, s) + "\nMerci.";
    }

    /** « Payé jusqu'au jeudi 20/11 », « En retard de 3 jours : 15 000 F CFA »… */
    static String etat(FormatsFlotte f, Compte.Situation s) {
        return switch (s.etat()) {
            case EN_RETARD -> "En retard de " + s.joursEnRetard() + " jour" + (s.joursEnRetard() > 1 ? "s" : "")
                    + " : " + f.montant(s.montantEnRetard()) + " à payer.";
            case SOLDE -> "Véhicule entièrement payé.";
            case SANS_VERSEMENT -> "";
            default -> s.payeJusquAu() == null ? "Aucun jour payé." : "Payé jusqu'au " + f.jourCourt(s.payeJusquAu())
                    + "." + (s.resteAujourdhui() > 0 ? " Reste du jour : " + f.montant(s.resteAujourdhui()) + "." : "");
        };
    }

    // ================================================================== demande de paiement

    @PostMapping("/contrats/{id}/demander")
    public String demander(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.ENCAISSER);
        if (!flotte.demandeDisponible(a, id)) {
            return "redirect:" + PAGE + "/contrats/" + id + "/message";
        }
        return agir(flash, () -> {
            flash.addFlashAttribute("succes", flotte.demanderPaiement(a, id));
            return "redirect:" + PAGE;
        }, PAGE);
    }

    /** Sans demande de paiement branchée : le message tout prêt, à envoyer par WhatsApp ou SMS. */
    @GetMapping("/contrats/{id}/message")
    public String message(@PathVariable UUID id, HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.ENCAISSER);
        FormatsFlotte f = commun(model, a, entreprises, flotte, "caisse");
        Contrat c = flotte.contrat(a, id);
        Compte.Situation s = flotte.compte(a, id);
        Conducteur d = flotte.conducteur(a, c.conducteurId());
        Vehicule v = flotte.vehicule(a, c.vehiculeId());
        Entreprise e = entreprises.consulter(a.tenantId());
        String texte = "Bonjour " + d.nom() + ",\n" + e.denomination() + " : pour le véhicule "
                + v.immatriculation() + ", " + minuscule(etat(f, s))
                + (s.aEncaisser() > 0 ? "\nÀ verser aujourd'hui : " + f.montant(s.aEncaisser()) + "." : "")
                + (v.referencePaiement().isEmpty() ? "" : "\nPaiement : " + v.referencePaiement() + ".")
                + "\nMerci.";
        model.addAttribute("c", c);
        model.addAttribute("conducteur", d);
        model.addAttribute("vehicule", v);
        model.addAttribute("compte", s);
        model.addAttribute("texte", texte);
        model.addAttribute("whatsapp", whatsapp(e, d.telephone(), texte));
        model.addAttribute("sms", d.telephone().isEmpty() ? null
                : "sms:" + d.telephone() + "?body=" + UriUtils.encodeQueryParam(texte, StandardCharsets.UTF_8));
        return "flotte/message";
    }

    private static String minuscule(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Lien WhatsApp : numéro international sans « + » ; un numéro local reçoit l'indicatif du pays. */
    static String whatsapp(Entreprise e, String telephone, String texte) {
        if (telephone == null || telephone.isEmpty()) {
            return null;
        }
        String chiffres = telephone.replace("+", "");
        if (!telephone.startsWith("+") && e.pays() != null && chiffres.length() <= 10) {
            chiffres = e.pays().indicatif().replace("+", "") + chiffres.replaceFirst("^0+", "");
        }
        return "https://wa.me/" + chiffres + "?text=" + UriUtils.encodeQueryParam(texte, StandardCharsets.UTF_8);
    }

    // ================================================================== journal des versements

    @GetMapping("/versements")
    public String journal(@RequestParam(required = false) String du, @RequestParam(required = false) String au,
                          HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "versements");
        LocalDate fin = au == null || au.isBlank() ? flotte.aujourdHui() : FormatsFlotte.lireDate(au, "Au");
        LocalDate debut = du == null || du.isBlank() ? fin : FormatsFlotte.lireDate(du, "Du");
        List<Versement> liste = flotte.journal(a, debut, fin);
        model.addAttribute("du", debut);
        model.addAttribute("au", fin);
        model.addAttribute("versements", liste);
        model.addAttribute("libelles", libellesContrats(a));
        Map<ModeVersement, Long> parMode = new EnumMap<>(ModeVersement.class);
        liste.stream().filter(v -> !v.annule()).forEach(v -> parMode.merge(v.mode(), v.montant(), Long::sum));
        model.addAttribute("parMode", parMode);
        model.addAttribute("total", parMode.values().stream().mapToLong(Long::longValue).sum());
        return "flotte/versements";
    }

    @PostMapping("/versements/{id}/annuler")
    public String annuler(@PathVariable UUID id, @RequestParam(defaultValue = "") String motif,
                          HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, CONSULTER);
        return agir(flash, () -> {
            Versement v = flotte.annulerVersement(a, id, motif);
            flash.addFlashAttribute("succes", "Versement " + v.numero() + " annulé.");
            return "redirect:" + PAGE + "/versements/" + id;
        }, PAGE + "/versements/" + id);
    }

    /** « CT-2026-0001 · AB-1234 · Koffi » pour chaque contrat (journal, exports). */
    private Map<UUID, String> libellesContrats(Acces a) {
        Map<UUID, String> v = new HashMap<>();
        flotte.vehicules(a).forEach(x -> v.put(x.id(), x.immatriculation()));
        Map<UUID, String> d = new HashMap<>();
        flotte.conducteurs(a).forEach(x -> d.put(x.id(), x.nom()));
        Map<UUID, String> l = new HashMap<>();
        flotte.contrats(a).forEach(c -> l.put(c.id(), v.getOrDefault(c.vehiculeId(), "?") + " · "
                + d.getOrDefault(c.conducteurId(), "?")));
        return l;
    }

    // ================================================================== jours fériés

    @GetMapping("/feries")
    public String feries(HttpServletRequest requete, Model model) {
        Acces a = contexte.exiger(requete, CONSULTER);
        commun(model, a, entreprises, flotte, "feries");
        model.addAttribute("feries", flotte.feries(a));
        model.addAttribute("aujourdhui", flotte.aujourdHui());
        return "flotte/feries";
    }

    @PostMapping("/feries")
    public String declarerFerie(@RequestParam Map<String, String> c, HttpServletRequest requete,
                                RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.ENCAISSER);
        return agir(flash, () -> {
            LocalDate d = FormatsFlotte.lireDate(c.get("date"), "Date");
            flotte.declarerFerie(a, d, c.getOrDefault("libelle", ""));
            flash.addFlashAttribute("succes", "Jour férié enregistré : aucun versement n'est dû ce jour-là.");
            return "redirect:" + PAGE + "/feries";
        }, PAGE + "/feries");
    }

    @PostMapping("/feries/retirer")
    public String retirerFerie(@RequestParam String date, HttpServletRequest requete, RedirectAttributes flash) {
        Acces a = contexte.exiger(requete, PermissionsFlotte.ENCAISSER);
        return agir(flash, () -> {
            flotte.retirerFerie(a, FormatsFlotte.lireDate(date, "Date"));
            flash.addFlashAttribute("succes", "Jour férié retiré : les versements de ce jour sont de nouveau dus.");
            return "redirect:" + PAGE + "/feries";
        }, PAGE + "/feries");
    }

    // ================================================================== exports

    /** Exporte un tableau : {@code quoi} = situation, versements, releve, vehicules, conducteurs, papiers. */
    @GetMapping("/export/{quoi}.{format}")
    public ResponseEntity<byte[]> exporter(@PathVariable String quoi, @PathVariable String format,
                                           @RequestParam Map<String, String> p, HttpServletRequest requete) {
        Acces a = contexte.exiger(requete, CONSULTER);
        Entreprise e = entreprises.consulter(a.tenantId());
        FormatsFlotte f = new FormatsFlotte(e.devise());
        Tableau t = switch (quoi) {
            case "situation" -> Exports.situation(flotte.caisse(a), f, flotte.aujourdHui());
            case "versements" -> {
                LocalDate au = p.get("au") == null || p.get("au").isBlank() ? flotte.aujourdHui()
                        : FormatsFlotte.lireDate(p.get("au"), "Au");
                LocalDate du = p.get("du") == null || p.get("du").isBlank() ? au
                        : FormatsFlotte.lireDate(p.get("du"), "Du");
                yield Exports.versements(flotte.journal(a, du, au), libellesContrats(a), f, du, au);
            }
            case "releve" -> {
                UUID id = FormatsFlotte.lireId(p.get("contrat"));
                Contrat c = flotte.contrat(a, id);
                yield Exports.releve(c, flotte.vehicule(a, c.vehiculeId()), flotte.conducteur(a, c.conducteurId()),
                        flotte.compte(a, id), f);
            }
            case "vehicules" -> Exports.vehicules(flotte.vehicules(a), f);
            case "conducteurs" -> Exports.conducteurs(flotte.conducteurs(a), f);
            case "papiers" -> Exports.papiers(flotte.documents(a), flotte.vehicules(a), f, flotte.aujourdHui());
            default -> throw new com.venach.kauris.kernel.commun.ElementIntrouvable("Export inconnu.");
        };
        String nom = ProducteurDocuments.nomDeFichier(t.titre(), t.precision());
        return switch (format) {
            case "csv" -> fichier(t.csv(), nom.replaceAll("\\.pdf$", "") + ".csv", "text/csv; charset=UTF-8");
            case "xlsx" -> fichier(t.xlsx(), nom.replaceAll("\\.pdf$", "") + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "pdf" -> EntrepriseController.enPdf(pdf(a, t), nom);
            default -> throw new com.venach.kauris.kernel.commun.ElementIntrouvable("Format inconnu.");
        };
    }

    private byte[] pdf(Acces a, Tableau t) {
        Habillage h = producteur.habillage(a.tenantId(), t.titre(), t.precision(), null, true);
        Map<String, Object> v = new HashMap<>();
        v.put("habillage", h);
        v.put("t", t);
        v.put("numeriques", numeriques(t));
        v.put("cartouche", Cartouche.de(Cartouche.c("Édité le", new FormatsFlotte(h.entreprise().devise())
                .jour(flotte.aujourdHui())), Cartouche.c("Lignes", String.valueOf(t.lignes().size()))));
        v.put("etabliPar", a.nomMembre());
        v.put("visas", List.of("Visa du responsable"));
        FormatsFlotte f = new FormatsFlotte(h.entreprise().devise());
        v.put("lignes", t.lignes().stream().map(l -> textes(l, f)).toList());
        v.put("total", t.total() == null ? null : textes(t.total(), f));
        return producteur.pdf("flotte/doc-tableau", v);
    }

    /** Les cases d'une ligne, mises en forme pour le PDF (« 25 000 »). */
    private static List<String> textes(List<Object> cases, FormatsFlotte f) {
        return cases.stream().map(x -> x == null ? "" : x instanceof Number n ? f.nombre(n.longValue())
                : x.toString()).toList();
    }

    /** Les colonnes à aligner à droite (celles dont les cellules sont des nombres). */
    private static List<Boolean> numeriques(Tableau t) {
        List<Boolean> n = new ArrayList<>();
        for (int i = 0; i < t.colonnes().size(); i++) {
            int col = i;
            n.add(t.lignes().stream().anyMatch(l -> l.size() > col && l.get(col) instanceof Number));
        }
        return n;
    }

    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, String type) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"").body(contenu);
    }
}
