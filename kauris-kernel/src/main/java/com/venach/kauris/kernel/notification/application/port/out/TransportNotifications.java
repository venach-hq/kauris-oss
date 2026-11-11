package com.venach.kauris.kernel.notification.application.port.out;

import com.venach.kauris.kernel.notification.domain.Avis;

/**
 * Fait partir un avis vers les écrans ouverts : directement dans l'application (mode autonome) ou par
 * RabbitMQ (Kauris Cloud). Une exception signale un échec : l'avis sera retenté plus tard.
 */
public interface TransportNotifications {

    void publier(Avis avis);
}
