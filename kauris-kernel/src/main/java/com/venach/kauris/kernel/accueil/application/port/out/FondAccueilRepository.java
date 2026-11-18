package com.venach.kauris.kernel.accueil.application.port.out;

import com.venach.kauris.kernel.accueil.domain.FondAccueil;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FondAccueilRepository {

    Optional<FondAccueil> trouver(UUID tenantId, UUID membreId);

    /** Quand l'image a été changée pour la dernière fois ; vide s'il n'y en a pas. */
    Optional<Instant> modifieLe(UUID tenantId, UUID membreId);

    void enregistrer(UUID tenantId, UUID membreId, FondAccueil fond, Instant quand);

    void retirer(UUID tenantId, UUID membreId);
}
