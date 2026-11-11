package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;

import java.util.Optional;

/**
 * Fourni par l'application : qui est connecté. Kauris Cloud répond avec le compte venach, le lanceur
 * autonome avec le compte local. Le noyau n'a pas besoin d'en savoir plus.
 */
public interface SessionKauris {

    /** La personne connectée pour la requête en cours ; vide si personne n'est connecté. */
    Optional<IdentiteConnectee> personne();

    /** Vrai si les membres se connectent avec un compte local, que l'organisation crée elle-même. */
    default boolean comptesLocaux() {
        return false;
    }
}
