package com.venach.kauris.kernel.connexion.local.adapter.out;

import com.venach.kauris.kernel.connexion.local.application.port.out.CompteLocalRepository;
import com.venach.kauris.kernel.connexion.local.domain.CompteLocal;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

public class CompteLocalJpaAdapter implements CompteLocalRepository {

    private final EntityManager em;

    public CompteLocalJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<CompteLocal> parEmail(String email) {
        return em.createQuery("select c from CompteLocalEntity c where c.email = :email", CompteLocalEntity.class)
                .setParameter("email", email)
                .getResultList().stream().findFirst().map(CompteLocalJpaAdapter::versDomaine);
    }

    @Override
    public Optional<CompteLocal> trouver(UUID id) {
        return Optional.ofNullable(em.find(CompteLocalEntity.class, id)).map(CompteLocalJpaAdapter::versDomaine);
    }

    @Override
    public void sauvegarder(CompteLocal compte) {
        CompteLocalEntity e = new CompteLocalEntity();
        e.id = compte.id();
        e.tenantId = compte.tenantId();
        e.email = compte.email();
        e.empreinteMotDePasse = compte.empreinteMotDePasse();
        e.creeLe = compte.creeLe();
        em.merge(e);
    }

    private static CompteLocal versDomaine(CompteLocalEntity e) {
        return CompteLocal.reconstituer(e.id, e.tenantId, e.email, e.empreinteMotDePasse, e.creeLe);
    }
}
