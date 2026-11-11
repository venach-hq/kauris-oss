package com.venach.kauris.kernel.organisation.adapter.out.persistence;

import com.venach.kauris.kernel.organisation.application.port.out.RolePersonnaliseRepository;
import com.venach.kauris.kernel.organisation.domain.RolePersonnalise;
import jakarta.persistence.EntityManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RolePersonnaliseJpaAdapter implements RolePersonnaliseRepository {

    private final EntityManager em;

    public RolePersonnaliseJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<RolePersonnalise> lister(UUID tenantId) {
        return em.createQuery("select r from RolePersonnaliseEntity r where r.tenantId = :tenant",
                        RolePersonnaliseEntity.class)
                .setParameter("tenant", tenantId)
                .getResultList().stream().map(RolePersonnaliseJpaAdapter::versDomaine).toList();
    }

    @Override
    public Optional<RolePersonnalise> trouver(UUID tenantId, UUID id) {
        return em.createQuery("select r from RolePersonnaliseEntity r where r.tenantId = :tenant and r.id = :id",
                        RolePersonnaliseEntity.class)
                .setParameter("tenant", tenantId)
                .setParameter("id", id)
                .getResultList().stream().findFirst().map(RolePersonnaliseJpaAdapter::versDomaine);
    }

    @Override
    public List<RolePersonnalise> parIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select r from RolePersonnaliseEntity r where r.id in :ids",
                        RolePersonnaliseEntity.class)
                .setParameter("ids", ids)
                .getResultList().stream().map(RolePersonnaliseJpaAdapter::versDomaine).toList();
    }

    @Override
    public void sauvegarder(RolePersonnalise role) {
        RolePersonnaliseEntity e = new RolePersonnaliseEntity();
        e.id = role.id();
        e.tenantId = role.tenantId();
        e.nom = role.nom();
        e.description = role.description();
        e.permissions = role.permissions().stream().sorted().collect(Collectors.joining(","));
        e.creeLe = role.creeLe();
        em.merge(e);
    }

    @Override
    public void supprimer(UUID tenantId, UUID id) {
        em.createQuery("delete from RolePersonnaliseEntity r where r.tenantId = :tenant and r.id = :id")
                .setParameter("tenant", tenantId)
                .setParameter("id", id)
                .executeUpdate();
    }

    private static RolePersonnalise versDomaine(RolePersonnaliseEntity e) {
        Set<String> permissions = Arrays.stream(e.permissions.split(",")).map(String::strip)
                .filter(code -> !code.isEmpty()).collect(Collectors.toSet());
        return RolePersonnalise.reconstituer(e.id, e.tenantId, e.nom, e.description, permissions, e.creeLe);
    }
}
