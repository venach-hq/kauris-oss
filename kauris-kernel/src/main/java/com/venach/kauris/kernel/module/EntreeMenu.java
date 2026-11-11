package com.venach.kauris.kernel.module;

/**
 * Une entrée du menu de l'interface commune.
 *
 * @param chemin adresse dans l'application, qui commence par « / »
 */
public record EntreeMenu(String libelle, String chemin) {

    public EntreeMenu {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Libellé du menu requis");
        }
        if (chemin == null || !chemin.startsWith("/") || chemin.startsWith("//")) {
            throw new IllegalArgumentException("Chemin de menu invalide : " + chemin);
        }
    }
}
