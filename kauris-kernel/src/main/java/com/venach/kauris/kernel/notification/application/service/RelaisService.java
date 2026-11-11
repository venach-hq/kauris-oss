package com.venach.kauris.kernel.notification.application.service;

import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.notification.application.port.in.RelayerBoiteDEnvoiUseCase;
import com.venach.kauris.kernel.notification.application.port.out.BoiteDEnvoi;
import com.venach.kauris.kernel.notification.application.port.out.BoiteDEnvoi.Depot;
import com.venach.kauris.kernel.notification.application.port.out.TransportNotifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Vide la boîte d'envoi. Un avis qui ne part pas est retenté, de plus en plus tard (1 s, 2 s, 4 s… jusqu'à
 * 5 minutes) et sans limite : une panne du transport ne perd rien. En contrepartie un avis peut partir deux
 * fois (envoyé, puis l'enregistrement échoue) : celui qui le reçoit doit le tolérer.
 */
public class RelaisService implements RelayerBoiteDEnvoiUseCase {

    static final int LOT = 50;
    static final Duration ATTENTE_MAX = Duration.ofMinutes(5);
    static final Duration GARDE_DES_ENVOYES = Duration.ofDays(7);
    private static final int ERREUR_MAX = 500;

    private final BoiteDEnvoi boite;
    private final TransportNotifications transport;
    private final UniteDeTravail transaction;
    private final Clock horloge;

    public RelaisService(BoiteDEnvoi boite, TransportNotifications transport, UniteDeTravail transaction,
                         Clock horloge) {
        this.boite = boite;
        this.transport = transport;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    @Override
    public int relayer() {
        return transaction.executer(() -> {
            Instant maintenant = horloge.instant();
            int envoyes = 0;
            for (Depot depot : boite.aEnvoyer(maintenant, LOT)) {
                try {
                    transport.publier(depot.avis());
                    boite.marquerEnvoye(depot.id(), maintenant);
                    envoyes++;
                } catch (RuntimeException echec) {
                    boite.marquerEchec(depot.id(), resume(echec), maintenant.plus(attente(depot.tentatives())));
                }
            }
            boite.purgerEnvoyesAvant(maintenant.minus(GARDE_DES_ENVOYES));
            return envoyes;
        });
    }

    /** 1 s après le premier échec, puis le double à chaque fois, plafonné. */
    public static Duration attente(int tentativesDejaFaites) {
        long secondes = 1L << Math.min(Math.max(tentativesDejaFaites, 0), 20);
        Duration attente = Duration.ofSeconds(secondes);
        return attente.compareTo(ATTENTE_MAX) > 0 ? ATTENTE_MAX : attente;
    }

    private static String resume(RuntimeException echec) {
        String texte = echec.getClass().getSimpleName() + (echec.getMessage() == null ? "" : " : " + echec.getMessage());
        return texte.length() > ERREUR_MAX ? texte.substring(0, ERREUR_MAX) : texte;
    }
}
