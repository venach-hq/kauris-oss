package com.venach.kauris.kernel.organisation.application.service;

import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.application.port.out.LimitesOrganisation;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.domain.Etablissement;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class EtablissementsService implements GererEtablissementsUseCase {

    private final EtablissementRepository etablissements;
    private final MembreRepository membres;
    private final UniteDeTravail transaction;
    private final Clock horloge;
    private final LimitesOrganisation limites;

    public EtablissementsService(EtablissementRepository etablissements, MembreRepository membres,
                                 UniteDeTravail transaction, Clock horloge) {
        this(etablissements, membres, transaction, horloge, LimitesOrganisation.AUCUNE);
    }

    public EtablissementsService(EtablissementRepository etablissements, MembreRepository membres,
                                 UniteDeTravail transaction, Clock horloge, LimitesOrganisation limites) {
        this.etablissements = etablissements;
        this.membres = membres;
        this.transaction = transaction;
        this.horloge = horloge;
        this.limites = limites;
    }

    @Override
    public List<Etablissement> lister(UUID tenantId) {
        return etablissements.lister(tenantId).stream()
                .sorted(Comparator.comparing((Etablissement e) -> !e.estPrincipal())
                        .thenComparing(Etablissement::nom, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Etablissement ajouter(UUID tenantId, UUID acteurId, String nom) {
        return transaction.executer(() -> {
            Autorisations.administrateur(membres, tenantId, acteurId);
            Etablissement nouveau = Etablissement.creer(tenantId, nom, false, horloge.instant());
            refuserAuDelaDeLaLimite(tenantId);
            refuserDoublon(tenantId, nouveau);
            etablissements.sauvegarder(nouveau);
            return nouveau;
        });
    }

    @Override
    public void renommer(UUID tenantId, UUID acteurId, UUID etablissementId, String nom) {
        transaction.executer(() -> {
            Autorisations.administrateur(membres, tenantId, acteurId);
            Etablissement etablissement = trouver(tenantId, etablissementId);
            etablissement.renommer(nom);
            refuserDoublon(tenantId, etablissement);
            etablissements.sauvegarder(etablissement);
        });
    }

    @Override
    public void fermer(UUID tenantId, UUID acteurId, UUID etablissementId) {
        transaction.executer(() -> {
            Autorisations.administrateur(membres, tenantId, acteurId);
            Etablissement etablissement = trouver(tenantId, etablissementId);
            etablissement.fermer();
            etablissements.sauvegarder(etablissement);
        });
    }

    @Override
    public void rouvrir(UUID tenantId, UUID acteurId, UUID etablissementId) {
        transaction.executer(() -> {
            Autorisations.administrateur(membres, tenantId, acteurId);
            Etablissement etablissement = trouver(tenantId, etablissementId);
            if (!etablissement.estOuvert()) {
                refuserAuDelaDeLaLimite(tenantId);
            }
            etablissement.rouvrir();
            etablissements.sauvegarder(etablissement);
        });
    }

    @Override
    public void definirPrincipal(UUID tenantId, UUID acteurId, UUID etablissementId) {
        transaction.executer(() -> {
            Autorisations.administrateur(membres, tenantId, acteurId);
            Etablissement nouveau = trouver(tenantId, etablissementId);
            nouveau.devenirPrincipal();
            for (Etablissement autre : etablissements.lister(tenantId)) {
                if (autre.estPrincipal() && !autre.id().equals(nouveau.id())) {
                    autre.cesserDEtrePrincipal();
                    etablissements.sauvegarder(autre);
                }
            }
            etablissements.sauvegarder(nouveau);
        });
    }

    private Etablissement trouver(UUID tenantId, UUID id) {
        return etablissements.trouver(tenantId, id)
                .orElseThrow(() -> new ElementIntrouvable("Établissement introuvable."));
    }

    /** À appeler avant d'ouvrir un établissement de plus. */
    private void refuserAuDelaDeLaLimite(UUID tenantId) {
        int max = limites.de(tenantId).etablissementsOuverts();
        long ouverts = etablissements.lister(tenantId).stream().filter(Etablissement::estOuvert).count();
        if (ouverts >= max) {
            throw new ErreurMetier("Votre formule permet " + max + (max > 1 ? " établissements ouverts." :
                    " établissement ouvert.") + " Fermez-en un ou changez de formule.");
        }
    }

    private void refuserDoublon(UUID tenantId, Etablissement candidat) {
        boolean pris = etablissements.lister(tenantId).stream()
                .anyMatch(e -> !e.id().equals(candidat.id()) && e.nom().equalsIgnoreCase(candidat.nom()));
        if (pris) {
            throw new ErreurMetier("Un établissement porte déjà le nom « " + candidat.nom() + " ».");
        }
    }
}
