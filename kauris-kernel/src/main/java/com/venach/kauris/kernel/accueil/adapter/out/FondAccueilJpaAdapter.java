package com.venach.kauris.kernel.accueil.adapter.out;

import com.venach.kauris.kernel.accueil.application.port.out.FondAccueilRepository;
import com.venach.kauris.kernel.accueil.domain.FondAccueil;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** L'image est un simple bloc d'octets : lue et écrite en SQL direct, hors des entités. Les heures sont en UTC. */
public class FondAccueilJpaAdapter implements FondAccueilRepository {

    private final EntityManager em;

    public FondAccueilJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<FondAccueil> trouver(UUID tenantId, UUID membreId) {
        List<?> lignes = em.createNativeQuery("select type_mime, contenu from kernel_fond_accueil "
                        + "where tenant_id = :tenant and membre_id = :membre")
                .setParameter("tenant", tenantId.toString())
                .setParameter("membre", membreId.toString())
                .getResultList();
        if (lignes.isEmpty()) {
            return Optional.empty();
        }
        Object[] ligne = (Object[]) lignes.get(0);
        return Optional.of(new FondAccueil((byte[]) ligne[1], (String) ligne[0]));
    }

    @Override
    public Optional<Instant> modifieLe(UUID tenantId, UUID membreId) {
        List<?> lignes = em.createNativeQuery("select modifie_le from kernel_fond_accueil "
                        + "where tenant_id = :tenant and membre_id = :membre")
                .setParameter("tenant", tenantId.toString())
                .setParameter("membre", membreId.toString())
                .getResultList();
        if (lignes.isEmpty() || lignes.get(0) == null) {
            return Optional.empty();
        }
        Object valeur = lignes.get(0);
        if (valeur instanceof Timestamp t) {
            return Optional.of(t.toLocalDateTime().toInstant(ZoneOffset.UTC));
        }
        if (valeur instanceof LocalDateTime t) {
            return Optional.of(t.toInstant(ZoneOffset.UTC));
        }
        if (valeur instanceof Instant t) {
            return Optional.of(t);
        }
        return Optional.of(Instant.EPOCH);
    }

    @Override
    public void enregistrer(UUID tenantId, UUID membreId, FondAccueil fond, Instant quand) {
        retirer(tenantId, membreId);
        em.createNativeQuery("insert into kernel_fond_accueil (tenant_id, membre_id, type_mime, contenu, modifie_le) "
                        + "values (:tenant, :membre, :type, :contenu, :quand)")
                .setParameter("tenant", tenantId.toString())
                .setParameter("membre", membreId.toString())
                .setParameter("type", fond.typeMime())
                .setParameter("contenu", fond.contenu())
                .setParameter("quand", LocalDateTime.ofInstant(quand, ZoneOffset.UTC))
                .executeUpdate();
    }

    @Override
    public void retirer(UUID tenantId, UUID membreId) {
        em.createNativeQuery("delete from kernel_fond_accueil where tenant_id = :tenant and membre_id = :membre")
                .setParameter("tenant", tenantId.toString())
                .setParameter("membre", membreId.toString())
                .executeUpdate();
    }
}
