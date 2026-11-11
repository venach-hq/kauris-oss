package com.venach.kauris.kernel.notification.adapter.out;

import com.venach.kauris.kernel.notification.application.port.out.BoiteDEnvoi;
import com.venach.kauris.kernel.notification.domain.Avis;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BoiteDEnvoiJpaAdapter implements BoiteDEnvoi {

    static final String TYPE_NOTIFICATION = "notification";
    /** Valeur JPA de « ne pas attendre les lignes verrouillées, les sauter » (SELECT … FOR UPDATE SKIP LOCKED). */
    private static final int SAUTER_LES_LIGNES_VERROUILLEES = -2;

    private final EntityManager em;

    public BoiteDEnvoiJpaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public void deposer(Avis avis, Instant maintenant) {
        BoiteEnvoiEntity e = new BoiteEnvoiEntity();
        e.id = UUID.randomUUID();
        e.tenantId = avis.tenantId();
        e.type = TYPE_NOTIFICATION;
        e.contenu = AvisJson.ecrire(avis);
        e.creeLe = maintenant;
        e.prochainEssaiLe = maintenant;
        e.tentatives = 0;
        em.persist(e);
    }

    @Override
    public List<Depot> aEnvoyer(Instant maintenant, int max) {
        List<BoiteEnvoiEntity> lignes = em.createQuery("select b from BoiteEnvoiEntity b where b.envoyeLe is null "
                        + "and b.type = :type and b.prochainEssaiLe <= :maintenant order by b.creeLe",
                        BoiteEnvoiEntity.class)
                .setParameter("type", TYPE_NOTIFICATION)
                .setParameter("maintenant", maintenant)
                .setMaxResults(max)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", SAUTER_LES_LIGNES_VERROUILLEES)
                .getResultList();
        List<Depot> depots = new ArrayList<>();
        for (BoiteEnvoiEntity b : lignes) {
            try {
                depots.add(new Depot(b.id, AvisJson.lire(b.contenu), b.tentatives));
            } catch (IllegalArgumentException illisible) {
                // Une ligne illisible le restera : on la classe au lieu de bloquer le relais à chaque passage.
                b.envoyeLe = maintenant;
                b.derniereErreur = "Contenu illisible : jamais envoyé";
            }
        }
        return depots;
    }

    @Override
    public void marquerEnvoye(UUID depotId, Instant maintenant) {
        BoiteEnvoiEntity b = em.find(BoiteEnvoiEntity.class, depotId);
        if (b != null) {
            b.envoyeLe = maintenant;
            b.tentatives++;
            b.derniereErreur = null;
        }
    }

    @Override
    public void marquerEchec(UUID depotId, String erreur, Instant prochainEssai) {
        BoiteEnvoiEntity b = em.find(BoiteEnvoiEntity.class, depotId);
        if (b != null) {
            b.tentatives++;
            b.derniereErreur = erreur;
            b.prochainEssaiLe = prochainEssai;
        }
    }

    @Override
    public int purgerEnvoyesAvant(Instant limite) {
        return em.createQuery("delete from BoiteEnvoiEntity b where b.envoyeLe is not null and b.envoyeLe < :limite")
                .setParameter("limite", limite)
                .executeUpdate();
    }
}
