package com.venach.kauris.kernel.notification.application.port.out;

import com.venach.kauris.kernel.notification.domain.Avis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Boîte d'envoi (« outbox ») : les avis à faire partir, écrits dans la transaction du changement métier. */
public interface BoiteDEnvoi {

    /** Un avis en attente. */
    record Depot(UUID id, Avis avis, int tentatives) {
    }

    void deposer(Avis avis, Instant maintenant);

    /**
     * Les plus anciens dépôts à envoyer maintenant, réservés à la transaction en cours : un autre relais
     * qui passe au même moment ne les voit pas.
     */
    List<Depot> aEnvoyer(Instant maintenant, int max);

    void marquerEnvoye(UUID depotId, Instant maintenant);

    void marquerEchec(UUID depotId, String erreur, Instant prochainEssai);

    /** Supprime les dépôts envoyés avant cette date. */
    int purgerEnvoyesAvant(Instant limite);
}
