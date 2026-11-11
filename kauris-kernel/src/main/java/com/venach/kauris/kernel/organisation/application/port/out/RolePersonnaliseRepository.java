package com.venach.kauris.kernel.organisation.application.port.out;

import com.venach.kauris.kernel.organisation.domain.RolePersonnalise;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePersonnaliseRepository {

    List<RolePersonnalise> lister(UUID tenantId);

    Optional<RolePersonnalise> trouver(UUID tenantId, UUID id);

    /** Pour calculer les droits d'une personne membre de plusieurs organisations, en une lecture. */
    List<RolePersonnalise> parIds(Collection<UUID> ids);

    void sauvegarder(RolePersonnalise role);

    void supprimer(UUID tenantId, UUID id);

    /** Aucun rôle personnalisé : pour les applications et les tests qui n'en ont pas. */
    RolePersonnaliseRepository AUCUN = new RolePersonnaliseRepository() {
        @Override
        public List<RolePersonnalise> lister(UUID tenantId) {
            return List.of();
        }

        @Override
        public Optional<RolePersonnalise> trouver(UUID tenantId, UUID id) {
            return Optional.empty();
        }

        @Override
        public List<RolePersonnalise> parIds(Collection<UUID> ids) {
            return List.of();
        }

        @Override
        public void sauvegarder(RolePersonnalise role) {
            throw new UnsupportedOperationException("Pas de stockage des rôles personnalisés");
        }

        @Override
        public void supprimer(UUID tenantId, UUID id) {
        }
    };
}
