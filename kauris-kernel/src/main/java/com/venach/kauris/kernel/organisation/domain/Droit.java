package com.venach.kauris.kernel.organisation.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Une permission qu'un membre détient, pour toute l'organisation ({@code etablissementId} nul) ou pour un
 * seul établissement.
 */
public record Droit(String permission, UUID etablissementId) {

    public Droit {
        Objects.requireNonNull(permission, "permission requise");
    }

    public boolean couvre(UUID etablissement) {
        return etablissementId == null || etablissementId.equals(etablissement);
    }
}
