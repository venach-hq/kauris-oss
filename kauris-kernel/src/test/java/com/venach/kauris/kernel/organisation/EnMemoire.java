package com.venach.kauris.kernel.organisation;

import com.venach.kauris.kernel.entreprise.application.port.out.EntrepriseRepository;
import com.venach.kauris.kernel.entreprise.domain.Entreprise;
import com.venach.kauris.kernel.entreprise.domain.Logo;
import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.application.port.out.RolePersonnaliseRepository;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Organisation;
import com.venach.kauris.kernel.organisation.domain.RolePersonnalise;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doubles de test en mémoire (aucun Spring, aucune base). Comme une vraie base, ils rendent des copies :
 * un objet modifié puis refusé par une règle n'est pas enregistré.
 */
public final class EnMemoire {

    public static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-11T08:00:00Z"), ZoneOffset.UTC);

    private EnMemoire() {
    }

    public static Organisation copie(Organisation o) {
        return Organisation.reconstituer(o.id(), o.nom(), o.statut(), o.creeLe());
    }

    public static Etablissement copie(Etablissement e) {
        return Etablissement.reconstituer(e.id(), e.tenantId(), e.nom(), e.estPrincipal(), e.estOuvert(),
                e.creeLe());
    }

    public static Membre copie(Membre m) {
        return Membre.reconstituer(new Membre.Etat(m.id(), m.tenantId(), m.identite().orElse(null), m.email(),
                m.nom(), m.statut(), m.roles(), m.creeLe()));
    }

    public static final class Organisations implements OrganisationRepository {

        private final Map<UUID, Organisation> parId = new LinkedHashMap<>();

        @Override
        public Optional<Organisation> trouver(UUID id) {
            return Optional.ofNullable(parId.get(id)).map(EnMemoire::copie);
        }

        @Override
        public List<Organisation> trouverToutes(Collection<UUID> ids) {
            return parId.values().stream().filter(o -> ids.contains(o.id())).map(EnMemoire::copie).toList();
        }

        @Override
        public boolean auMoinsUne() {
            return !parId.isEmpty();
        }

        @Override
        public void sauvegarder(Organisation organisation) {
            parId.put(organisation.id(), copie(organisation));
        }
    }

    public static final class Etablissements implements EtablissementRepository {

        private final Map<UUID, Etablissement> parId = new LinkedHashMap<>();

        @Override
        public List<Etablissement> lister(UUID tenantId) {
            return parId.values().stream().filter(e -> e.tenantId().equals(tenantId)).map(EnMemoire::copie)
                    .toList();
        }

        @Override
        public Optional<Etablissement> trouver(UUID tenantId, UUID id) {
            return Optional.ofNullable(parId.get(id)).filter(e -> e.tenantId().equals(tenantId))
                    .map(EnMemoire::copie);
        }

        @Override
        public void sauvegarder(Etablissement etablissement) {
            parId.put(etablissement.id(), copie(etablissement));
        }
    }

    public static final class Membres implements MembreRepository {

        private final Map<UUID, Membre> parId = new LinkedHashMap<>();

        @Override
        public List<Membre> lister(UUID tenantId) {
            return parId.values().stream().filter(m -> m.tenantId().equals(tenantId)).map(EnMemoire::copie)
                    .toList();
        }

        @Override
        public Optional<Membre> trouver(UUID tenantId, UUID id) {
            return Optional.ofNullable(parId.get(id)).filter(m -> m.tenantId().equals(tenantId))
                    .map(EnMemoire::copie);
        }

        @Override
        public Optional<Membre> parEmail(UUID tenantId, String email) {
            return parId.values().stream()
                    .filter(m -> m.tenantId().equals(tenantId) && m.email().equals(email))
                    .map(EnMemoire::copie).findFirst();
        }

        @Override
        public List<Membre> parIdentite(Identite identite) {
            return parId.values().stream()
                    .filter(m -> m.identite().filter(identite::equals).isPresent())
                    .map(EnMemoire::copie).toList();
        }

        @Override
        public List<Membre> invitationsEnAttente(String email) {
            return parId.values().stream()
                    .filter(m -> m.statut() == StatutMembre.INVITE && m.email().equals(email))
                    .map(EnMemoire::copie).toList();
        }

        @Override
        public void sauvegarder(Membre membre) {
            parId.put(membre.id(), copie(membre));
        }
    }

    public static RolePersonnalise copie(RolePersonnalise r) {
        return RolePersonnalise.reconstituer(r.id(), r.tenantId(), r.nom(), r.description(), r.permissions(),
                r.creeLe());
    }

    public static final class RolesPersonnalises implements RolePersonnaliseRepository {

        private final Map<UUID, RolePersonnalise> parId = new LinkedHashMap<>();

        @Override
        public List<RolePersonnalise> lister(UUID tenantId) {
            return parId.values().stream().filter(r -> r.tenantId().equals(tenantId)).map(EnMemoire::copie)
                    .toList();
        }

        @Override
        public Optional<RolePersonnalise> trouver(UUID tenantId, UUID id) {
            return Optional.ofNullable(parId.get(id)).filter(r -> r.tenantId().equals(tenantId))
                    .map(EnMemoire::copie);
        }

        @Override
        public List<RolePersonnalise> parIds(Collection<UUID> ids) {
            return parId.values().stream().filter(r -> ids.contains(r.id())).map(EnMemoire::copie).toList();
        }

        @Override
        public void sauvegarder(RolePersonnalise role) {
            parId.put(role.id(), copie(role));
        }

        @Override
        public void supprimer(UUID tenantId, UUID id) {
            parId.values().removeIf(r -> r.id().equals(id) && r.tenantId().equals(tenantId));
        }
    }

    public static final class Entreprises implements EntrepriseRepository {

        private final Map<UUID, Entreprise> parTenant = new LinkedHashMap<>();
        private final Map<UUID, Logo> logos = new LinkedHashMap<>();

        @Override
        public Optional<Entreprise> trouver(UUID tenantId) {
            return Optional.ofNullable(parTenant.get(tenantId)).map(e -> e.avecLogo(logos.containsKey(tenantId)));
        }

        @Override
        public void sauvegarder(Entreprise entreprise) {
            parTenant.put(entreprise.tenantId(), entreprise);
        }

        @Override
        public Optional<Logo> logo(UUID tenantId) {
            return Optional.ofNullable(logos.get(tenantId));
        }

        @Override
        public void enregistrerLogo(UUID tenantId, Logo logo) {
            logos.put(tenantId, logo);
        }

        @Override
        public void retirerLogo(UUID tenantId) {
            logos.remove(tenantId);
        }
    }
}
