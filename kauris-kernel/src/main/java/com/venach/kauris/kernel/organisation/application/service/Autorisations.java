package com.venach.kauris.kernel.organisation.application.service;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.domain.Membre;

import java.util.UUID;

/** Contrôle commun aux services du noyau : qui a le droit d'administrer l'organisation. */
final class Autorisations {

    private Autorisations() {
    }

    /** Le membre qui agit, s'il est actif dans cette organisation et propriétaire ou administrateur. */
    static Membre administrateur(MembreRepository membres, UUID tenantId, UUID acteurId) {
        return membres.trouver(tenantId, acteurId)
                .filter(Membre::estActif)
                .filter(Membre::administre)
                .orElseThrow(() -> new AccesRefuse(
                        "Cette action est réservée aux propriétaires et administrateurs de l'organisation."));
    }
}
