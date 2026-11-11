package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Droit;
import com.venach.kauris.kernel.organisation.domain.Role;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ce qu'une personne connectée peut faire dans une organisation dont elle est membre actif.
 *
 * @param droits             les permissions qu'elle détient, par ses rôles de base et ses rôles personnalisés
 * @param rolesPersonnalises les noms de ses rôles personnalisés, pour l'affichage
 */
public record Acces(UUID tenantId, String organisation, boolean organisationActive, UUID membreId,
                    String nomMembre, String email, Set<AttributionRole> roles, Set<Droit> droits,
                    List<String> rolesPersonnalises) {

    public Acces {
        roles = Set.copyOf(roles);
        droits = Set.copyOf(droits);
        rolesPersonnalises = List.copyOf(rolesPersonnalises);
    }

    /** Sans détail des permissions : seuls le propriétaire et l'administrateur en ont alors (toutes). */
    public Acces(UUID tenantId, String organisation, boolean organisationActive, UUID membreId, String nomMembre,
                 String email, Set<AttributionRole> roles) {
        this(tenantId, organisation, organisationActive, membreId, nomMembre, email, roles, Set.of(), List.of());
    }

    public boolean a(Role role) {
        return roles.stream().anyMatch(r -> r.role() == role);
    }

    /** Propriétaire ou administrateur. */
    public boolean administre() {
        return roles.stream().anyMatch(r -> r.role().administre());
    }

    /**
     * Vrai si la personne a cette permission, pour toute l'organisation ou pour au moins un établissement.
     * Le propriétaire et l'administrateur ont toutes les permissions. C'est ce que les modules interrogent.
     */
    public boolean peut(String permission) {
        return administre() || droits.stream().anyMatch(d -> d.permission().equals(permission));
    }

    /** Vrai si la personne a cette permission pour cet établissement. */
    public boolean peut(String permission, UUID etablissementId) {
        return administre()
                || droits.stream().anyMatch(d -> d.permission().equals(permission) && d.couvre(etablissementId));
    }

    public boolean peutModifier(UUID etablissementId) {
        return roles.stream().anyMatch(r -> r.role().peutModifier() && r.couvre(etablissementId));
    }

    public boolean peutConsulter(UUID etablissementId) {
        return roles.stream().anyMatch(r -> r.couvre(etablissementId));
    }

    /**
     * Les rôles en une phrase courte, sans répétition : « Propriétaire », « Gestionnaire (2 établissements) »,
     * « Administrateur, Lecteur (1 établissement) ». Un rôle donné pour toute l'organisation l'emporte sur le
     * même rôle limité à des établissements. Les rôles personnalisés suivent, par leur nom.
     */
    public String resumeRoles() {
        Stream<String> deBase = Arrays.stream(Role.values())
                .filter(Role::deBase)
                .filter(this::a)
                .map(role -> {
                    boolean partout = roles.stream().anyMatch(r -> r.role() == role && r.etablissementId() == null);
                    long limites = roles.stream().filter(r -> r.role() == role && r.etablissementId() != null)
                            .map(AttributionRole::etablissementId).distinct().count();
                    return partout ? role.libelle()
                            : role.libelle() + " (" + limites + (limites > 1 ? " établissements)" : " établissement)");
                });
        return Stream.concat(deBase, rolesPersonnalises.stream().sorted(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.joining(", "));
    }
}
