package com.venach.kauris.kernel.organisation.application.service;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.organisation.application.port.in.GererRolesUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.CataloguePermissions;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.RolePersonnaliseRepository;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.RolePersonnalise;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RolesService implements GererRolesUseCase {

    static final int MAX_PAR_ORGANISATION = 50;

    private final RolePersonnaliseRepository roles;
    private final MembreRepository membres;
    private final CataloguePermissions catalogue;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public RolesService(RolePersonnaliseRepository roles, MembreRepository membres, CataloguePermissions catalogue,
                        UniteDeTravail transaction, Clock horloge) {
        this.roles = roles;
        this.membres = membres;
        this.catalogue = catalogue;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    @Override
    public List<RolePersonnalise> lister(UUID tenantId) {
        return roles.lister(tenantId).stream()
                .sorted(Comparator.comparing(RolePersonnalise::nom, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public List<Permission> permissions() {
        return catalogue.toutes();
    }

    @Override
    public RolePersonnalise creer(UUID tenantId, UUID acteurId, String nom, String description,
                                  Set<String> permissions) {
        return transaction.executer(() -> {
            exigerProprietaire(tenantId, acteurId);
            List<RolePersonnalise> existants = roles.lister(tenantId);
            if (existants.size() >= MAX_PAR_ORGANISATION) {
                throw new ErreurMetier("Une organisation a " + MAX_PAR_ORGANISATION + " rôles personnalisés au plus.");
            }
            RolePersonnalise role = RolePersonnalise.creer(tenantId, nom, description, connues(permissions),
                    horloge.instant());
            refuserDoublon(existants, role);
            roles.sauvegarder(role);
            return role;
        });
    }

    @Override
    public void modifier(UUID tenantId, UUID acteurId, UUID roleId, String nom, String description,
                         Set<String> permissions) {
        transaction.executer(() -> {
            exigerProprietaire(tenantId, acteurId);
            RolePersonnalise role = trouver(tenantId, roleId);
            role.definir(nom, description, connues(permissions));
            refuserDoublon(roles.lister(tenantId), role);
            roles.sauvegarder(role);
        });
    }

    @Override
    public void supprimer(UUID tenantId, UUID acteurId, UUID roleId) {
        transaction.executer(() -> {
            exigerProprietaire(tenantId, acteurId);
            RolePersonnalise role = trouver(tenantId, roleId);
            long titulaires = membres.lister(tenantId).stream()
                    .filter(m -> m.roles().stream().anyMatch(r -> roleId.equals(r.rolePersonnaliseId())))
                    .count();
            if (titulaires > 0) {
                throw new ErreurMetier("Le rôle « " + role.nom() + " » est encore donné à " + titulaires
                        + " membre(s). Changez d'abord leurs rôles.");
            }
            roles.supprimer(tenantId, roleId);
        });
    }

    private RolePersonnalise trouver(UUID tenantId, UUID roleId) {
        return roles.trouver(tenantId, roleId).orElseThrow(() -> new ElementIntrouvable("Rôle introuvable."));
    }

    private void exigerProprietaire(UUID tenantId, UUID acteurId) {
        membres.trouver(tenantId, acteurId)
                .filter(Membre::estActif)
                .filter(Membre::estProprietaire)
                .orElseThrow(() -> new AccesRefuse("Seul un propriétaire définit les rôles et leurs permissions."));
    }

    /** Une permission inconnue (module retiré, formulaire trafiqué) est refusée, pas ignorée en silence. */
    private Set<String> connues(Set<String> demandees) {
        if (demandees == null || demandees.isEmpty()) {
            return Set.of();
        }
        Set<String> declarees = catalogue.toutes().stream().map(Permission::code).collect(Collectors.toSet());
        if (!declarees.containsAll(demandees)) {
            throw new ErreurMetier("Permission inconnue.");
        }
        return demandees;
    }

    private static void refuserDoublon(List<RolePersonnalise> existants, RolePersonnalise candidat) {
        boolean pris = existants.stream()
                .anyMatch(r -> !r.id().equals(candidat.id()) && r.nom().equalsIgnoreCase(candidat.nom()));
        if (pris) {
            throw new ErreurMetier("Un rôle porte déjà le nom « " + candidat.nom() + " ».");
        }
    }
}
