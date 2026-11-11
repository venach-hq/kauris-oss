package com.venach.kauris.kernel.commun;

import java.util.function.Supplier;

/**
 * Exécute une action dans une seule transaction : tout est enregistré, ou rien.
 * Les services métier passent par ce port pour ne pas dépendre de Spring.
 */
public interface UniteDeTravail {

    <T> T executer(Supplier<T> action);

    default void executer(Runnable action) {
        executer(() -> {
            action.run();
            return null;
        });
    }

    /** Sans transaction : pour les tests du domaine et des services en mémoire. */
    UniteDeTravail DIRECTE = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }
    };
}
