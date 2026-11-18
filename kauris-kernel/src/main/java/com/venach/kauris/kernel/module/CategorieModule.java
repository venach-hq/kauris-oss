package com.venach.kauris.kernel.module;

/**
 * La famille d'un module, qui range ses grands boutons sur une même ligne du tableau de bord. L'ordre des valeurs
 * est l'ordre des lignes.
 */
public enum CategorieModule {
    VENTES("Ventes et clients"),
    ACHATS_STOCK("Achats et stock"),
    FINANCES("Finances et comptabilité"),
    RESSOURCES_HUMAINES("Ressources humaines"),
    ACTIVITES("Activités"),
    ORGANISATION("Organisation et compte"),
    AUTRES("Autres");

    private final String libelle;

    CategorieModule(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
