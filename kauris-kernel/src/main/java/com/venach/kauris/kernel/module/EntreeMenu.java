package com.venach.kauris.kernel.module;

/**
 * Une entrée du menu de l'interface commune.
 *
 * @param chemin     adresse dans l'application, qui commence par « / »
 * @param permission code de la permission qu'il faut pour voir l'entrée ; nul = tous les membres
 */
public record EntreeMenu(String libelle, String chemin, String permission) {

    public EntreeMenu {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Libellé du menu requis");
        }
        if (chemin == null || !chemin.startsWith("/") || chemin.startsWith("//")) {
            throw new IllegalArgumentException("Chemin de menu invalide : " + chemin);
        }
    }

    /** Une entrée visible de tous les membres. */
    public EntreeMenu(String libelle, String chemin) {
        this(libelle, chemin, null);
    }
}
