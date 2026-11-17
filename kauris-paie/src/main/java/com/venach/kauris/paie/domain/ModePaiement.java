package com.venach.kauris.paie.domain;

/** Comment le salarié reçoit son salaire. */
public enum ModePaiement {

    VIREMENT("Virement bancaire"),
    MOBILE_MONEY("Mobile Money"),
    ESPECES("Espèces"),
    CHEQUE("Chèque");

    private final String libelle;

    ModePaiement(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai si le salaire part dans le fichier de virement (banque ou Mobile Money). */
    public boolean parVirement() {
        return this == VIREMENT || this == MOBILE_MONEY;
    }
}
