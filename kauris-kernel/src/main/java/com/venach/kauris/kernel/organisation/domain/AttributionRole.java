package com.venach.kauris.kernel.organisation.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.Objects;
import java.util.UUID;

/**
 * Un rôle donné à un membre, pour toute l'organisation ({@code etablissementId} nul) ou pour un seul
 * établissement.
 */
public record AttributionRole(Role role, UUID etablissementId) {

    public AttributionRole {
        Objects.requireNonNull(role, "rôle requis");
        if (role.organisationEntiere() && etablissementId != null) {
            throw new ErreurMetier("Le rôle « " + role.libelle() + " » vaut pour toute l'organisation.");
        }
    }

    public static AttributionRole pourTouteLOrganisation(Role role) {
        return new AttributionRole(role, null);
    }

    public static AttributionRole pourEtablissement(Role role, UUID etablissementId) {
        return new AttributionRole(role, Objects.requireNonNull(etablissementId, "établissement requis"));
    }

    /** Vrai si ce rôle s'applique à l'établissement donné. */
    public boolean couvre(UUID etablissement) {
        return etablissementId == null || etablissementId.equals(etablissement);
    }
}
