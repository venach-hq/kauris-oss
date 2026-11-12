package com.venach.kauris.ventes.domain;

/** Comment le client a payé. */
public enum ModeReglement {
    ESPECES("Espèces"),
    VIREMENT("Virement bancaire"),
    CHEQUE("Chèque"),
    MOBILE_MONEY("Mobile Money"),
    PISPI("Paiement instantané PI-SPI"),
    CARTE("Carte bancaire"),
    AUTRE("Autre");

    private final String libelle;

    ModeReglement(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
