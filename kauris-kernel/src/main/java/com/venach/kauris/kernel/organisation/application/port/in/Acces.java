package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Role;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ce qu'une personne connectée peut faire dans une organisation dont elle est membre actif. */
public record Acces(UUID tenantId, String organisation, boolean organisationActive, UUID membreId,
                    String nomMembre, String email, Set<AttributionRole> roles) {

    public Acces {
        roles = Set.copyOf(roles);
    }

    public boolean a(Role role) {
        return roles.stream().anyMatch(r -> r.role() == role);
    }

    /** Propriétaire ou administrateur. */
    public boolean administre() {
        return roles.stream().anyMatch(r -> r.role().administre());
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
     * même rôle limité à des établissements.
     */
    public String resumeRoles() {
        return Arrays.stream(Role.values())
                .filter(this::a)
                .map(role -> {
                    boolean partout = roles.stream().anyMatch(r -> r.role() == role && r.etablissementId() == null);
                    long limites = roles.stream().filter(r -> r.role() == role && r.etablissementId() != null)
                            .map(AttributionRole::etablissementId).distinct().count();
                    return partout ? role.libelle()
                            : role.libelle() + " (" + limites + (limites > 1 ? " établissements)" : " établissement)");
                })
                .collect(Collectors.joining(", "));
    }
}
