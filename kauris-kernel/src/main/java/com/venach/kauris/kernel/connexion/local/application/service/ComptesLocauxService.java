package com.venach.kauris.kernel.connexion.local.application.service;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import com.venach.kauris.kernel.connexion.local.application.port.out.CompteLocalRepository;
import com.venach.kauris.kernel.connexion.local.application.port.out.EmpreinteMotDePasse;
import com.venach.kauris.kernel.connexion.local.domain.CompteLocal;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public class ComptesLocauxService implements GererComptesLocauxUseCase {

    private final CompteLocalRepository comptes;
    private final EmpreinteMotDePasse empreinte;
    private final GererOrganisationUseCase organisations;
    private final MembreRepository membres;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public ComptesLocauxService(CompteLocalRepository comptes, EmpreinteMotDePasse empreinte,
                                GererOrganisationUseCase organisations, MembreRepository membres,
                                UniteDeTravail transaction, Clock horloge) {
        this.comptes = comptes;
        this.empreinte = empreinte;
        this.organisations = organisations;
        this.membres = membres;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    @Override
    public Optional<CompteConnectable> pourConnexion(String email) {
        String propre;
        try {
            propre = Saisie.email(email);
        } catch (ErreurMetier adresseMalFormee) {
            return Optional.empty();
        }
        return comptes.parEmail(propre).map(compte -> new CompteConnectable(compte.id(), compte.email(),
                compte.empreinteMotDePasse(),
                membres.parIdentite(compte.identite()).stream().anyMatch(Membre::estActif)));
    }

    @Override
    public boolean installationAFaire() {
        return !organisations.auMoinsUneOrganisation();
    }

    @Override
    public OrganisationOuverte installer(Installation i) {
        return transaction.executer(() -> {
            if (!installationAFaire()) {
                throw new AccesRefuse("Kauris est déjà installé : connectez-vous.");
            }
            String email = Saisie.email(i.email());
            CompteLocal.verifierMotDePasse(i.motDePasse(), email);
            UUID compteId = UUID.randomUUID();
            OrganisationOuverte ouverte = organisations.ouvrir(new Ouverture(i.nomOrganisation(),
                    i.nomEtablissement(), Identite.locale(compteId.toString()), email, i.nom()));
            comptes.sauvegarder(CompteLocal.creer(compteId, ouverte.tenantId(), email,
                    empreinte.calculer(i.motDePasse()), horloge.instant()));
            return ouverte;
        });
    }

    @Override
    public void creerComptePourMembre(UUID tenantId, UUID acteurId, UUID membreId, String motDePasse) {
        transaction.executer(() -> {
            Membre acteur = membres.trouver(tenantId, acteurId)
                    .filter(Membre::estActif)
                    .filter(Membre::administre)
                    .orElseThrow(() -> new AccesRefuse(
                            "Cette action est réservée aux propriétaires et administrateurs de l'organisation."));
            Membre membre = membres.trouver(tenantId, membreId)
                    .orElseThrow(() -> new ElementIntrouvable("Membre introuvable."));
            if (membre.estProprietaire() && !acteur.estProprietaire()) {
                throw new AccesRefuse("Seul un propriétaire peut créer le compte d'un propriétaire.");
            }
            if (membre.statut() != StatutMembre.INVITE || membre.identite().isPresent()) {
                throw new ErreurMetier("Ce membre a déjà un compte.");
            }
            if (comptes.parEmail(membre.email()).isPresent()) {
                throw new ErreurMetier("Un compte existe déjà pour cette adresse.");
            }
            CompteLocal.verifierMotDePasse(motDePasse, membre.email());
            CompteLocal compte = CompteLocal.creer(UUID.randomUUID(), tenantId, membre.email(),
                    empreinte.calculer(motDePasse), horloge.instant());
            comptes.sauvegarder(compte);
            membre.rattacher(compte.identite());
            membres.sauvegarder(membre);
        });
    }

    @Override
    public void changerMotDePasse(UUID compteId, String actuel, String nouveau) {
        transaction.executer(() -> {
            CompteLocal compte = comptes.trouver(compteId)
                    .orElseThrow(() -> new ElementIntrouvable("Compte introuvable."));
            if (actuel == null || !empreinte.correspond(actuel, compte.empreinteMotDePasse())) {
                throw new ErreurMetier("Le mot de passe actuel est incorrect.");
            }
            CompteLocal.verifierMotDePasse(nouveau, compte.email());
            compte.changerEmpreinte(empreinte.calculer(nouveau));
            comptes.sauvegarder(compte);
        });
    }
}
