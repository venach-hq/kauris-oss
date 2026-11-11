package com.venach.kauris.kernel.module;

import java.util.List;

/**
 * Un module Kauris (paie, comptabilité, flotte…). Chaque module en déclare un bean : il apparaît alors dans le
 * registre des modules, que l'application tourne seule ou dans Kauris Cloud.
 * <p>
 * Le code est stable et sert de clé partout (abonnements, droits, navigation) : on ne le change jamais.
 */
public interface ModuleKauris {

    /** Identifiant stable, en minuscules : « rh-paie-tg », « compta-syscohada »… */
    String code();

    /** Nom affiché. */
    String nom();

    /** Version du module. */
    String version();

    default String description() {
        return "";
    }

    /** Entrées que le module ajoute au menu de l'interface commune, dans l'ordre. */
    default List<EntreeMenu> menu() {
        return List.of();
    }
}
