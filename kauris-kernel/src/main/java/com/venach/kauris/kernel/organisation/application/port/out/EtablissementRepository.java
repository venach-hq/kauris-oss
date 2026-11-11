package com.venach.kauris.kernel.organisation.application.port.out;

import com.venach.kauris.kernel.organisation.domain.Etablissement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toutes les lectures sont limitées à une organisation : on ne lit jamais un établissement sans son tenant. */
public interface EtablissementRepository {

    List<Etablissement> lister(UUID tenantId);

    Optional<Etablissement> trouver(UUID tenantId, UUID id);

    void sauvegarder(Etablissement etablissement);
}
