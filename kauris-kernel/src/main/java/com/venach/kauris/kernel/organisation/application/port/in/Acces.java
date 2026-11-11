package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Role;

import java.util.Set;
import java.util.UUID;

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
}
