package com.venach.kauris.kernel.organisation.adapter.out.persistence;

import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toutes les requêtes portent le tenant : un identifiant d'une autre organisation ne trouve rien. */
public class EtablissementJpaAdapter implements EtablissementRepository {

    private final EntityManager em;

    public EtablissementJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Etablissement> lister(UUID tenantId) {
        return em.createQuery("select e from EtablissementEntity e where e.tenantId = :tenant",
                        EtablissementEntity.class)
                .setParameter("tenant", tenantId)
                .getResultList().stream().map(EtablissementJpaAdapter::versDomaine).toList();
    }

    @Override
    public Optional<Etablissement> trouver(UUID tenantId, UUID id) {
        return em.createQuery("select e from EtablissementEntity e where e.tenantId = :tenant and e.id = :id",
                        EtablissementEntity.class)
                .setParameter("tenant", tenantId)
                .setParameter("id", id)
                .getResultList().stream().findFirst().map(EtablissementJpaAdapter::versDomaine);
    }

    @Override
    public void sauvegarder(Etablissement etablissement) {
        EtablissementEntity e = new EtablissementEntity();
        e.id = etablissement.id();
        e.tenantId = etablissement.tenantId();
        e.nom = etablissement.nom();
        e.principal = etablissement.estPrincipal();
        e.ouvert = etablissement.estOuvert();
        e.creeLe = etablissement.creeLe();
        em.merge(e);
    }

    private static Etablissement versDomaine(EtablissementEntity e) {
        return Etablissement.reconstituer(e.id, e.tenantId, e.nom, e.principal, e.ouvert, e.creeLe);
    }
}
