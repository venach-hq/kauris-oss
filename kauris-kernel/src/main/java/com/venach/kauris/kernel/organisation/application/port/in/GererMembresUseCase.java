package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Membre;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Membres d'une organisation et leurs rôles. Les modifications demandent un propriétaire ou un
 * administrateur ; seul un propriétaire touche au rôle de propriétaire.
 */
public interface GererMembresUseCase {

    /** Par nom. */
    List<Membre> lister(UUID tenantId);

    /** Invite une personne par son email ; elle rejoindra l'organisation à sa première connexion. */
    Membre inviter(UUID tenantId, UUID acteurId, String email, String nom, Set<AttributionRole> roles);

    void definirRoles(UUID tenantId, UUID acteurId, UUID membreId, Set<AttributionRole> roles);

    void desactiver(UUID tenantId, UUID acteurId, UUID membreId);

    void reactiver(UUID tenantId, UUID acteurId, UUID membreId);
}
