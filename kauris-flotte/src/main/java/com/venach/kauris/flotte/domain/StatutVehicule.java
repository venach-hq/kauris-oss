package com.venach.kauris.flotte.domain;

public enum StatutVehicule {
    EN_SERVICE("En service"),
    AU_GARAGE("Au garage"),
    HORS_SERVICE("Hors service"),
    CEDE("Cédé (vendu ou transféré)");

    private final String libelle;

    StatutVehicule(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
