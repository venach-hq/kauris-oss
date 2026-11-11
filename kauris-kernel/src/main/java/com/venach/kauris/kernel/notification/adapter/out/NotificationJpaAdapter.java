package com.venach.kauris.kernel.notification.adapter.out;

import com.venach.kauris.kernel.notification.application.port.out.NotificationRepository;
import com.venach.kauris.kernel.notification.domain.Notification;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toutes les requêtes portent le tenant et le membre. */
public class NotificationJpaAdapter implements NotificationRepository {

    private final EntityManager em;

    public NotificationJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public void sauvegarder(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.id = n.id();
        e.tenantId = n.tenantId();
        e.membreId = n.membreId();
        e.titre = n.titre();
        e.message = n.message();
        e.lien = n.lien().orElse(null);
        e.creeLe = n.creeLe();
        e.lueLe = n.lueLe().orElse(null);
        em.merge(e);
    }

    @Override
    public List<Notification> recentes(UUID tenantId, UUID membreId, int max) {
        return em.createQuery("select n from NotificationEntity n where n.tenantId = :tenant "
                        + "and n.membreId = :membre order by n.creeLe desc, n.id", NotificationEntity.class)
                .setParameter("tenant", tenantId)
                .setParameter("membre", membreId)
                .setMaxResults(max)
                .getResultList().stream().map(NotificationJpaAdapter::versDomaine).toList();
    }

    @Override
    public long compterNonLues(UUID tenantId, UUID membreId) {
        return em.createQuery("select count(n) from NotificationEntity n where n.tenantId = :tenant "
                        + "and n.membreId = :membre and n.lueLe is null", Long.class)
                .setParameter("tenant", tenantId)
                .setParameter("membre", membreId)
                .getSingleResult();
    }

    @Override
    public Optional<Notification> trouver(UUID tenantId, UUID membreId, UUID id) {
        return em.createQuery("select n from NotificationEntity n where n.tenantId = :tenant "
                        + "and n.membreId = :membre and n.id = :id", NotificationEntity.class)
                .setParameter("tenant", tenantId)
                .setParameter("membre", membreId)
                .setParameter("id", id)
                .getResultList().stream().findFirst().map(NotificationJpaAdapter::versDomaine);
    }

    @Override
    public void marquerToutesLues(UUID tenantId, UUID membreId, Instant maintenant) {
        em.createQuery("update NotificationEntity n set n.lueLe = :maintenant where n.tenantId = :tenant "
                        + "and n.membreId = :membre and n.lueLe is null")
                .setParameter("maintenant", maintenant)
                .setParameter("tenant", tenantId)
                .setParameter("membre", membreId)
                .executeUpdate();
    }

    private static Notification versDomaine(NotificationEntity e) {
        return Notification.reconstituer(e.id, e.tenantId, e.membreId, e.titre, e.message, e.lien, e.creeLe,
                e.lueLe);
    }
}
