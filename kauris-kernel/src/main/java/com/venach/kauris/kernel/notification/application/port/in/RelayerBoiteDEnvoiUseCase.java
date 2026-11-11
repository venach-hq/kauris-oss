package com.venach.kauris.kernel.notification.application.port.in;

/** Envoie ce qui attend dans la boîte d'envoi. Appelé à intervalle régulier ; peut l'être à la main. */
public interface RelayerBoiteDEnvoiUseCase {

    /** @return le nombre d'avis envoyés pendant ce passage */
    int relayer();
}
