package com.venach.kauris.kernel.organisation.adapter.out.persistence;

import com.venach.kauris.kernel.organisation.application.port.out.OrganisationRepository;
import com.venach.kauris.kernel.organisation.domain.Organisation;
import com.venach.kauris.kernel.organisation.domain.StatutOrganisation;
import jakarta.persistence.EntityManager;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrganisationJpaAdapter implements OrganisationRepository {

    private final EntityManager em;

    public OrganisationJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Organisation> trouver(UUID id) {
        return Optional.ofNullable(em.find(OrganisationEntity.class, id)).map(OrganisationJpaAdapter::versDomaine);
    }

    @Override
    public List<Organisation> trouverToutes(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select o from OrganisationEntity o where o.id in :ids", OrganisationEntity.class)
                .setParameter("ids", ids)
                .getResultList().stream().map(OrganisationJpaAdapter::versDomaine).toList();
    }

    @Override
    public boolean auMoinsUne() {
        return !em.createQuery("select o.id from OrganisationEntity o", UUID.class)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    @Override
    public void sauvegarder(Organisation organisation) {
        OrganisationEntity e = new OrganisationEntity();
        e.id = organisation.id();
        e.tenantId = organisation.tenantId();
        e.nom = organisation.nom();
        e.statut = organisation.statut().name();
        e.creeLe = organisation.creeLe();
        em.merge(e);
    }

    private static Organisation versDomaine(OrganisationEntity e) {
        return Organisation.reconstituer(e.id, e.nom, StatutOrganisation.valueOf(e.statut), e.creeLe);
    }
}
