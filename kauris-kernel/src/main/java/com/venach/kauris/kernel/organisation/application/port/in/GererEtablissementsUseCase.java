package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.Etablissement;

import java.util.List;
import java.util.UUID;

/** Établissements d'une organisation. Les modifications demandent un propriétaire ou un administrateur. */
public interface GererEtablissementsUseCase {

    /** Le principal d'abord, puis par nom. */
    List<Etablissement> lister(UUID tenantId);

    Etablissement ajouter(UUID tenantId, UUID acteurId, String nom);

    void renommer(UUID tenantId, UUID acteurId, UUID etablissementId, String nom);

    void fermer(UUID tenantId, UUID acteurId, UUID etablissementId);

    void rouvrir(UUID tenantId, UUID acteurId, UUID etablissementId);

    void definirPrincipal(UUID tenantId, UUID acteurId, UUID etablissementId);
}
