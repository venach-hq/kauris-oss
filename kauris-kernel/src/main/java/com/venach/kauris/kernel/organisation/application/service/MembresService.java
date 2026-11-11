package com.venach.kauris.kernel.organisation.application.service;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.AvertirMembres;
import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.application.port.out.LimitesOrganisation;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.RolePersonnaliseRepository;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MembresService implements GererMembresUseCase {

    private final MembreRepository membres;
    private final EtablissementRepository etablissements;
    private final UniteDeTravail transaction;
    private final Clock horloge;
    private final AvertirMembres avertir;
    private final LimitesOrganisation limites;
    private final RolePersonnaliseRepository rolesPersonnalises;

    public MembresService(MembreRepository membres, EtablissementRepository etablissements,
                          UniteDeTravail transaction, Clock horloge) {
        this(membres, etablissements, transaction, horloge, AvertirMembres.PERSONNE);
    }

    public MembresService(MembreRepository membres, EtablissementRepository etablissements,
                          UniteDeTravail transaction, Clock horloge, AvertirMembres avertir) {
        this(membres, etablissements, transaction, horloge, avertir, LimitesOrganisation.AUCUNE);
    }

    public MembresService(MembreRepository membres, EtablissementRepository etablissements,
                          UniteDeTravail transaction, Clock horloge, AvertirMembres avertir,
                          LimitesOrganisation limites) {
        this(membres, etablissements, transaction, horloge, avertir, limites, RolePersonnaliseRepository.AUCUN);
    }

    public MembresService(MembreRepository membres, EtablissementRepository etablissements,
                          UniteDeTravail transaction, Clock horloge, AvertirMembres avertir,
                          LimitesOrganisation limites, RolePersonnaliseRepository rolesPersonnalises) {
        this.rolesPersonnalises = rolesPersonnalises;
        this.membres = membres;
        this.etablissements = etablissements;
        this.transaction = transaction;
        this.horloge = horloge;
        this.avertir = avertir;
        this.limites = limites;
    }

    @Override
    public List<Membre> lister(UUID tenantId) {
        return membres.lister(tenantId).stream()
                .sorted(Comparator.comparing(Membre::nom, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Membre::email))
                .toList();
    }

    @Override
    public Membre inviter(UUID tenantId, UUID acteurId, String email, String nom, Set<AttributionRole> roles) {
        return transaction.executer(() -> {
            Membre acteur = Autorisations.administrateur(membres, tenantId, acteurId);
            Membre invite = Membre.inviter(tenantId, email, nom, roles, horloge.instant());
            exigerProprietairePourToucherAuRoleProprietaire(acteur, Set.of(), invite.roles());
            verifierEtablissements(tenantId, invite.roles());
            if (membres.parEmail(tenantId, Saisie.email(email)).isPresent()) {
                throw new ErreurMetier("Cette adresse est déjà celle d'un membre de l'organisation.");
            }
            refuserAuDelaDeLaLimite(tenantId);
            membres.sauvegarder(invite);
            return invite;
        });
    }

    @Override
    public void definirRoles(UUID tenantId, UUID acteurId, UUID membreId, Set<AttributionRole> roles) {
        transaction.executer(() -> {
            Membre acteur = Autorisations.administrateur(membres, tenantId, acteurId);
            Membre membre = trouver(tenantId, membreId);
            Set<AttributionRole> avant = membre.roles();
            membre.definirRoles(roles);
            exigerProprietairePourToucherAuRoleProprietaire(acteur, avant, membre.roles());
            verifierEtablissements(tenantId, membre.roles());
            garderUnProprietaire(tenantId, membre);
            membres.sauvegarder(membre);
            if (!avant.equals(membre.roles())) {
                avertir.rolesChanges(membre);
            }
        });
    }

    @Override
    public void desactiver(UUID tenantId, UUID acteurId, UUID membreId) {
        transaction.executer(() -> {
            Membre acteur = Autorisations.administrateur(membres, tenantId, acteurId);
            if (acteur.id().equals(membreId)) {
                throw new ErreurMetier("Vous ne pouvez pas vous désactiver vous-même.");
            }
            Membre membre = trouver(tenantId, membreId);
            exigerProprietairePourToucherAuRoleProprietaire(acteur, membre.roles(), Set.of());
            membre.desactiver();
            garderUnProprietaire(tenantId, membre);
            membres.sauvegarder(membre);
        });
    }

    @Override
    public void reactiver(UUID tenantId, UUID acteurId, UUID membreId) {
        transaction.executer(() -> {
            Membre acteur = Autorisations.administrateur(membres, tenantId, acteurId);
            Membre membre = trouver(tenantId, membreId);
            exigerProprietairePourToucherAuRoleProprietaire(acteur, Set.of(), membre.roles());
            if (membre.statut() == StatutMembre.DESACTIVE) {
                refuserAuDelaDeLaLimite(tenantId);
            }
            membre.reactiver();
            membres.sauvegarder(membre);
        });
    }

    @Override
    public void changerSonNom(UUID tenantId, UUID membreId, String nom) {
        transaction.executer(() -> {
            Membre membre = trouver(tenantId, membreId);
            if (!membre.estActif()) {
                throw new AccesRefuse("Seul un membre actif peut changer son nom.");
            }
            membre.renommer(nom);
            membres.sauvegarder(membre);
        });
    }

    /** À appeler avant d'ajouter un membre : invités et actifs comptent, pas les désactivés. */
    private void refuserAuDelaDeLaLimite(UUID tenantId) {
        int max = limites.de(tenantId).membres();
        long comptes = membres.lister(tenantId).stream()
                .filter(m -> m.statut() != StatutMembre.DESACTIVE).count();
        if (comptes >= max) {
            throw new ErreurMetier("Votre formule permet " + max + (max > 1 ? " membres." : " membre.")
                    + " Désactivez-en un ou changez de formule.");
        }
    }

    private Membre trouver(UUID tenantId, UUID membreId) {
        return membres.trouver(tenantId, membreId)
                .orElseThrow(() -> new ElementIntrouvable("Membre introuvable."));
    }

    /** Donner ou retirer le rôle de propriétaire est réservé aux propriétaires. */
    private static void exigerProprietairePourToucherAuRoleProprietaire(Membre acteur, Set<AttributionRole> avant,
                                                                        Set<AttributionRole> apres) {
        boolean etait = avant.stream().anyMatch(r -> r.role() == Role.PROPRIETAIRE);
        boolean sera = apres.stream().anyMatch(r -> r.role() == Role.PROPRIETAIRE);
        if ((etait || sera) && !acteur.estProprietaire()) {
            throw new AccesRefuse("Seul un propriétaire peut donner, retirer ou modifier le rôle de propriétaire.");
        }
    }

    /** Un rôle limité à un établissement doit viser un établissement ouvert de la même organisation. */
    private void verifierEtablissements(UUID tenantId, Set<AttributionRole> roles) {
        for (AttributionRole role : roles) {
            if (role.etablissementId() != null) {
                boolean ouvert = etablissements.trouver(tenantId, role.etablissementId())
                        .filter(Etablissement::estOuvert)
                        .isPresent();
                if (!ouvert) {
                    throw new ErreurMetier("Établissement inconnu ou fermé.");
                }
            }
            // Un rôle personnalisé doit être un rôle de cette organisation, pas d'une autre.
            if (role.rolePersonnaliseId() != null
                    && rolesPersonnalises.trouver(tenantId, role.rolePersonnaliseId()).isEmpty()) {
                throw new ErreurMetier("Rôle inconnu.");
            }
        }
    }

    /** Après la modification de ce membre, l'organisation doit garder un propriétaire actif. */
    private void garderUnProprietaire(UUID tenantId, Membre modifie) {
        boolean reste = membres.lister(tenantId).stream()
                .map(m -> m.id().equals(modifie.id()) ? modifie : m)
                .anyMatch(m -> m.estActif() && m.estProprietaire());
        if (!reste) {
            throw new ErreurMetier("L'organisation doit garder au moins un propriétaire actif.");
        }
    }
}
