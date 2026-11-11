package com.venach.kauris.kernel.notification.adapter.out;

import com.venach.kauris.kernel.notification.application.port.in.RelayerBoiteDEnvoiUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Passe le relais de la boîte d'envoi à intervalle régulier, sur un fil à lui. Démarre et s'arrête avec
 * l'application ; une erreur (base injoignable…) est notée et le passage suivant réessaie.
 */
public class PlanificateurRelais implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PlanificateurRelais.class);

    private final RelayerBoiteDEnvoiUseCase relais;
    private final Duration intervalle;
    private ScheduledExecutorService fil;
    private boolean enPanne;

    public PlanificateurRelais(RelayerBoiteDEnvoiUseCase relais, Duration intervalle) {
        this.relais = relais;
        this.intervalle = intervalle;
    }

    @Override
    public synchronized void start() {
        if (fil != null) {
            return;
        }
        fil = Executors.newSingleThreadScheduledExecutor(tache -> {
            Thread thread = new Thread(tache, "kauris-relais");
            thread.setDaemon(true);
            return thread;
        });
        fil.scheduleWithFixedDelay(this::passer, intervalle.toMillis(), intervalle.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void passer() {
        try {
            relais.relayer();
            if (enPanne) {
                log.info("Relais de la boîte d'envoi : de nouveau en marche");
                enPanne = false;
            }
        } catch (RuntimeException echec) {
            // Une seule ligne par panne, pas une toutes les deux secondes.
            if (!enPanne) {
                log.warn("Relais de la boîte d'envoi : passage en échec, nouvel essai dans {}", intervalle, echec);
                enPanne = true;
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (fil != null) {
            fil.shutdownNow();
            fil = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return fil != null;
    }
}
