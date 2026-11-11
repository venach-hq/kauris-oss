package com.venach.kauris.kernel.organisation.application.service;

import java.util.stream.Stream;
import java.util.Optional;
import java.util.Objects;
import java.util.HashSet;
import com.venach.kauris.kernel.organisation.domain.RolePersonnalise;
import com.venach.kauris.kernel.organisation.domain.Droit;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.application.port.out.RolePersonnaliseRepository;
import com.venach.kauris.kernel.organisation.application.port.out.CataloguePermissions;
import com.venach.kauris.kernel.module.Permission;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.Saisie;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.AvertirMembres;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Organisation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AccesService implements ResoudreAccesUseCase {

    private final MembreRepository membres;
    private final OrganisationRepository organisations;
    private final UniteDeTravail transaction;
    private final AvertirMembres avertir;
    private final CataloguePermissions catalogue;
    private final RolePersonnaliseRepository rolesPersonnalises;

    public AccesService(MembreRepository membres, OrganisationRepository organisations,
                        UniteDeTravail transaction) {
        this(membres, organisations, transaction, AvertirMembres.PERSONNE);
    }

    public AccesService(MembreRepository membres, OrganisationRepository organisations,
                        UniteDeTravail transaction, AvertirMembres avertir) {
        this(membres, organisations, transaction, avertir, CataloguePermissions.VIDE,
                RolePersonnaliseRepository.AUCUN);
    }

    public AccesService(MembreRepository membres, OrganisationRepository organisations,
                        UniteDeTravail transaction, AvertirMembres avertir, CataloguePermissions catalogue,
                        RolePersonnaliseRepository rolesPersonnalises) {
        this.membres = membres;
        this.organisations = organisations;
        this.transaction = transaction;
        this.avertir = avertir;
        this.catalogue = catalogue;
        this.rolesPersonnalises = rolesPersonnalises;
    }

    @Override
    public List<Acces> accesDe(Identite identite) {
        List<Membre> actifs = membres.parIdentite(identite).stream().filter(Membre::estActif).toList();
        if (actifs.isEmpty()) {
            return List.of();
        }
        Map<UUID, Organisation> parId = organisations
                .trouverToutes(actifs.stream().map(Membre::tenantId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Organisation::id, Function.identity()));
        List<Permission> permissions = catalogue.toutes();
        Map<UUID, RolePersonnalise> personnalises = rolesPersonnalises.parIds(actifs.stream()
                        .flatMap(m -> m.roles().stream())
                        .map(AttributionRole::rolePersonnaliseId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(RolePersonnalise::id, Function.identity()));
        return actifs.stream()
                .filter(m -> parId.containsKey(m.tenantId()))
                .map(m -> {
                    Organisation o = parId.get(m.tenantId());
                    return new Acces(o.id(), o.nom(), o.estActive(), m.id(), m.nom(), m.email(), m.roles(),
                            droits(m, permissions, personnalises), noms(m, personnalises));
                })
                .sorted(Comparator.comparing(Acces::organisation, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Les permissions que ses rôles donnent au membre. Un rôle de base donne celles de son niveau ; un rôle
     * personnalisé, celles que le propriétaire y a cochées et qui sont encore déclarées. Un rôle personnalisé
     * d'une autre organisation ne donne rien.
     */
    private static Set<Droit> droits(Membre membre, List<Permission> permissions,
                                     Map<UUID, RolePersonnalise> personnalises) {
        Set<Droit> droits = new HashSet<>();
        Set<String> declarees = permissions.stream().map(Permission::code).collect(Collectors.toSet());
        for (AttributionRole attribution : membre.roles()) {
            Stream<String> codes = switch (attribution.role()) {
                case PROPRIETAIRE, ADMINISTRATEUR -> declarees.stream();
                case GESTIONNAIRE -> permissions.stream()
                        .filter(p -> p.niveau() != Permission.Niveau.ADMINISTRER).map(Permission::code);
                case LECTEUR -> permissions.stream()
                        .filter(p -> p.niveau() == Permission.Niveau.CONSULTER).map(Permission::code);
                case PERSONNALISE -> Optional.ofNullable(personnalises.get(attribution.rolePersonnaliseId()))
                        .filter(r -> r.tenantId().equals(membre.tenantId()))
                        .map(r -> r.permissions().stream().filter(declarees::contains))
                        .orElseGet(Stream::empty);
            };
            codes.forEach(code -> droits.add(new Droit(code, attribution.etablissementId())));
        }
        return droits;
    }

    private static List<String> noms(Membre membre, Map<UUID, RolePersonnalise> personnalises) {
        return membre.roles().stream()
                .map(AttributionRole::rolePersonnaliseId)
                .filter(Objects::nonNull)
                .distinct()
                .map(personnalises::get)
                .filter(Objects::nonNull)
                .filter(r -> r.tenantId().equals(membre.tenantId()))
                .map(RolePersonnalise::nom)
                .toList();
    }

    @Override
    public int rattacherInvitations(IdentiteConnectee personne) {
        if (!personne.emailVerifie() || personne.email() == null || personne.email().isBlank()) {
            return 0;
        }
        String email;
        try {
            email = Saisie.email(personne.email());
        } catch (ErreurMetier adresseInutilisable) {
            return 0;
        }
        return transaction.executer(() -> {
            Set<UUID> dejaMembre = membres.parIdentite(personne.identite()).stream()
                    .map(Membre::tenantId)
                    .collect(Collectors.toSet());
            int rejointes = 0;
            for (Membre invitation : membres.invitationsEnAttente(email)) {
                if (dejaMembre.add(invitation.tenantId())) {
                    invitation.rattacher(personne.identite());
                    membres.sauvegarder(invitation);
                    avertir.membreARejoint(invitation);
                    rejointes++;
                }
            }
            return rejointes;
        });
    }
}
