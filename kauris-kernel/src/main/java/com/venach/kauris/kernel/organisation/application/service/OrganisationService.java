package com.venach.kauris.kernel.organisation.application.service;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Organisation;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class OrganisationService implements GererOrganisationUseCase {

    private final OrganisationRepository organisations;
    private final EtablissementRepository etablissements;
    private final MembreRepository membres;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public OrganisationService(OrganisationRepository organisations, EtablissementRepository etablissements,
                               MembreRepository membres, UniteDeTravail transaction, Clock horloge) {
        this.organisations = organisations;
        this.etablissements = etablissements;
        this.membres = membres;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    @Override
    public OrganisationOuverte ouvrir(Ouverture o) {
        return transaction.executer(() -> {
            Instant maintenant = horloge.instant();
            Organisation organisation = Organisation.creer(o.nomOrganisation(), maintenant);
            String nomEtablissement = o.nomEtablissement() == null || o.nomEtablissement().isBlank()
                    ? Etablissement.NOM_PAR_DEFAUT : o.nomEtablissement();
            Etablissement principal = Etablissement.creer(organisation.id(), nomEtablissement, true, maintenant);
            Membre fondateur = Membre.fondateur(organisation.id(), o.identite(), o.email(), o.nomMembre(),
                    maintenant);
            organisations.sauvegarder(organisation);
            etablissements.sauvegarder(principal);
            membres.sauvegarder(fondateur);
            return new OrganisationOuverte(organisation.id(), principal.id(), fondateur.id());
        });
    }

    @Override
    public Organisation consulter(UUID tenantId) {
        return organisations.trouver(tenantId)
                .orElseThrow(() -> new ElementIntrouvable("Organisation introuvable."));
    }

    @Override
    public void renommer(UUID tenantId, UUID acteurId, String nom) {
        transaction.executer(() -> {
            Autorisations.administrateur(membres, tenantId, acteurId);
            Organisation organisation = consulter(tenantId);
            organisation.renommer(nom);
            organisations.sauvegarder(organisation);
        });
    }

    @Override
    public boolean auMoinsUneOrganisation() {
        return organisations.auMoinsUne();
    }
}
