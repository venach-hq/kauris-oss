package com.venach.kauris.kernel.organisation.application.port.in;

import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Organisation;

import java.util.UUID;

public interface GererOrganisationUseCase {

    /**
     * @param nomEtablissement nom du premier établissement ; vide = « Siège »
     * @param identite         la personne qui ouvre l'organisation : elle en devient propriétaire
     */
    record Ouverture(String nomOrganisation, String nomEtablissement, Identite identite, String email,
                     String nomMembre) {
    }

    record OrganisationOuverte(UUID tenantId, UUID etablissementId, UUID membreId) {
    }

    /** Crée l'organisation, son établissement principal et son premier membre (propriétaire). */
    OrganisationOuverte ouvrir(Ouverture ouverture);

    Organisation consulter(UUID tenantId);

    void renommer(UUID tenantId, UUID acteurId, String nom);

    /** Vrai s'il existe au moins une organisation (sert au premier démarrage du mode autonome). */
    boolean auMoinsUneOrganisation();
}
