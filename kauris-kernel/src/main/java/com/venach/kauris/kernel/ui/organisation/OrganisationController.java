package com.venach.kauris.kernel.ui.organisation;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;
import com.venach.kauris.kernel.ui.ContexteCourant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Écrans du noyau : l'organisation, ses établissements, ses membres et leurs rôles.
 * Tout membre peut consulter ; les modifications sont contrôlées par les services du noyau
 * (propriétaire ou administrateur), pas par cet écran.
 */
@Controller
@RequestMapping("/organisation")
public class OrganisationController {

    private static final String PAGE = "/organisation";
    private static final String PAGE_MEMBRES = "/organisation/membres";

    /** Une ligne de la liste des membres, prête à afficher. */
    public record LigneMembre(UUID id, String nom, String email, String statut, String roles, Role role,
                              Set<UUID> etablissements, boolean invite, boolean desactive) {
    }

    private final ContexteCourant contexte;
    private final GererOrganisationUseCase organisations;
    private final GererEtablissementsUseCase etablissements;
    private final GererMembresUseCase membres;

    public OrganisationController(ContexteCourant contexte, GererOrganisationUseCase organisations,
                                  GererEtablissementsUseCase etablissements, GererMembresUseCase membres) {
        this.contexte = contexte;
        this.organisations = organisations;
        this.etablissements = etablissements;
        this.membres = membres;
    }

    // ------------------------------------------------------------------ organisation et établissements

    @GetMapping
    public String organisation(HttpServletRequest requete, Model model) {
        Acces acces = contexte.exiger(requete);
        model.addAttribute("organisation", organisations.consulter(acces.tenantId()));
        model.addAttribute("etablissements", etablissements.lister(acces.tenantId()));
        return "kauris/organisation";
    }

    @PostMapping("/nom")
    public String renommer(@RequestParam String nom, HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE, "Organisation renommée.",
                a -> organisations.renommer(a.tenantId(), a.membreId(), nom));
    }

    @PostMapping("/etablissements")
    public String ajouterEtablissement(@RequestParam String nom, HttpServletRequest requete,
                                       RedirectAttributes flash) {
        return agir(requete, flash, PAGE, "Établissement ajouté.",
                a -> etablissements.ajouter(a.tenantId(), a.membreId(), nom));
    }

    @PostMapping("/etablissements/{id}/nom")
    public String renommerEtablissement(@PathVariable UUID id, @RequestParam String nom,
                                        HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE, "Établissement renommé.",
                a -> etablissements.renommer(a.tenantId(), a.membreId(), id, nom));
    }

    @PostMapping("/etablissements/{id}/fermer")
    public String fermerEtablissement(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE, "Établissement fermé.",
                a -> etablissements.fermer(a.tenantId(), a.membreId(), id));
    }

    @PostMapping("/etablissements/{id}/rouvrir")
    public String rouvrirEtablissement(@PathVariable UUID id, HttpServletRequest requete,
                                       RedirectAttributes flash) {
        return agir(requete, flash, PAGE, "Établissement rouvert.",
                a -> etablissements.rouvrir(a.tenantId(), a.membreId(), id));
    }

    @PostMapping("/etablissements/{id}/principal")
    public String definirPrincipal(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE, "Établissement principal changé.",
                a -> etablissements.definirPrincipal(a.tenantId(), a.membreId(), id));
    }

    // ------------------------------------------------------------------ membres et rôles

    @GetMapping("/membres")
    public String membres(HttpServletRequest requete, Model model) {
        Acces acces = contexte.exiger(requete);
        List<Etablissement> tous = etablissements.lister(acces.tenantId());
        Map<UUID, String> noms = tous.stream().collect(Collectors.toMap(Etablissement::id, Etablissement::nom));
        model.addAttribute("membres", membres.lister(acces.tenantId()).stream()
                .map(m -> ligne(m, noms)).toList());
        model.addAttribute("etablissements", tous.stream().filter(Etablissement::estOuvert).toList());
        model.addAttribute("roles", Role.values());
        return "kauris/membres";
    }

    @PostMapping("/membres")
    public String inviter(@RequestParam String email, @RequestParam String nom, @RequestParam String role,
                          @RequestParam(required = false) List<UUID> etablissements,
                          HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE_MEMBRES, "Invitation enregistrée.",
                a -> membres.inviter(a.tenantId(), a.membreId(), email, nom, attributions(role, etablissements)));
    }

    @PostMapping("/membres/{id}/roles")
    public String definirRoles(@PathVariable UUID id, @RequestParam String role,
                               @RequestParam(required = false) List<UUID> etablissements,
                               HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE_MEMBRES, "Rôles enregistrés.",
                a -> membres.definirRoles(a.tenantId(), a.membreId(), id, attributions(role, etablissements)));
    }

    @PostMapping("/membres/{id}/desactiver")
    public String desactiver(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE_MEMBRES, "Membre désactivé.",
                a -> membres.desactiver(a.tenantId(), a.membreId(), id));
    }

    @PostMapping("/membres/{id}/reactiver")
    public String reactiver(@PathVariable UUID id, HttpServletRequest requete, RedirectAttributes flash) {
        return agir(requete, flash, PAGE_MEMBRES, "Membre réactivé.",
                a -> membres.reactiver(a.tenantId(), a.membreId(), id));
    }

    // ------------------------------------------------------------------ outils

    /** Exécute l'action puis revient à la page ; un refus du noyau devient un message, pas une page d'erreur. */
    private String agir(HttpServletRequest requete, RedirectAttributes flash, String retour, String succes,
                        Consumer<Acces> action) {
        Acces acces = contexte.exiger(requete);
        try {
            action.accept(acces);
            flash.addFlashAttribute("succes", succes);
        } catch (ErreurMetier refus) {
            flash.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:" + retour;
    }

    /**
     * Un rôle et, pour les rôles qui peuvent l'être, les établissements auxquels il est limité
     * (aucun = toute l'organisation).
     */
    public static Set<AttributionRole> attributions(String codeRole, List<UUID> etablissements) {
        Role role;
        try {
            role = Role.valueOf(codeRole);
        } catch (IllegalArgumentException inconnu) {
            throw new ErreurMetier("Rôle inconnu.");
        }
        if (role.organisationEntiere() || etablissements == null || etablissements.isEmpty()) {
            return Set.of(AttributionRole.pourTouteLOrganisation(role));
        }
        return etablissements.stream().map(e -> AttributionRole.pourEtablissement(role, e))
                .collect(Collectors.toSet());
    }

    static LigneMembre ligne(Membre m, Map<UUID, String> nomsEtablissements) {
        Function<AttributionRole, String> texte = r -> r.etablissementId() == null
                ? r.role().libelle()
                : r.role().libelle() + " (" + nomsEtablissements.getOrDefault(r.etablissementId(), "?") + ")";
        String roles = m.roles().stream().map(texte).sorted().collect(Collectors.joining(", "));
        Role principal = m.roles().stream().map(AttributionRole::role).min(Comparator.naturalOrder()).orElseThrow();
        Set<UUID> limites = m.roles().stream().map(AttributionRole::etablissementId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        String statut = switch (m.statut()) {
            case INVITE -> "Invité";
            case ACTIF -> "Actif";
            case DESACTIVE -> "Désactivé";
        };
        return new LigneMembre(m.id(), m.nom(), m.email(), statut, roles, principal, limites,
                m.statut() == StatutMembre.INVITE, m.statut() == StatutMembre.DESACTIVE);
    }
}
