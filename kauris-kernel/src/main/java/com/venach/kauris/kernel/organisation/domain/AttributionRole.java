package com.venach.kauris.kernel.organisation.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.Objects;
import java.util.UUID;

/**
 * Un rôle donné à un membre, pour toute l'organisation ({@code etablissementId} nul) ou pour un seul
 * établissement.
 *
 * @param rolePersonnaliseId le rôle créé par le propriétaire, si {@code role} est {@link Role#PERSONNALISE} ;
 *                           nul pour un rôle de base
 */
public record AttributionRole(Role role, UUID etablissementId, UUID rolePersonnaliseId) {

    public AttributionRole {
        Objects.requireNonNull(role, "rôle requis");
        if (role.organisationEntiere() && etablissementId != null) {
            throw new ErreurMetier("Le rôle « " + role.libelle() + " » vaut pour toute l'organisation.");
        }
        if ((role == Role.PERSONNALISE) != (rolePersonnaliseId != null)) {
            throw new IllegalArgumentException("Un rôle personnalisé, et lui seul, porte son identifiant");
        }
    }

    /** Un rôle de base. */
    public AttributionRole(Role role, UUID etablissementId) {
        this(role, etablissementId, null);
    }

    public static AttributionRole pourTouteLOrganisation(Role role) {
        return new AttributionRole(role, null, null);
    }

    public static AttributionRole pourEtablissement(Role role, UUID etablissementId) {
        return new AttributionRole(role, Objects.requireNonNull(etablissementId, "établissement requis"), null);
    }

    /** @param etablissementId nul = toute l'organisation */
    public static AttributionRole personnalise(UUID rolePersonnaliseId, UUID etablissementId) {
        return new AttributionRole(Role.PERSONNALISE, etablissementId,
                Objects.requireNonNull(rolePersonnaliseId, "rôle personnalisé requis"));
    }

    /** Vrai si ce rôle s'applique à l'établissement donné. */
    public boolean couvre(UUID etablissement) {
        return etablissementId == null || etablissementId.equals(etablissement);
    }
}
