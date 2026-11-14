package com.venach.kauris.flotte.domain;

public enum ModeVersement {
    ESPECES("Espèces"),
    MOBILE_MONEY("Mobile Money"),
    PISPI("PI-SPI"),
    VIREMENT("Virement"),
    AUTRE("Autre");

    private final String libelle;

    ModeVersement(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
