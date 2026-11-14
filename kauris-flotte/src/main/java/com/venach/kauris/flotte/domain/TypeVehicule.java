package com.venach.kauris.flotte.domain;

/** Les véhicules et engins d'une flotte d'Afrique de l'Ouest. */
public enum TypeVehicule {
    MOTO("Moto (zémidjan, moto-taxi)"),
    TRICYCLE("Tricycle"),
    VOITURE("Voiture (taxi, VTC)"),
    MINIBUS("Minibus"),
    BUS("Bus, car"),
    CAMIONNETTE("Camionnette, pick-up"),
    CAMION("Camion"),
    ENGIN("Engin (grue, chargeuse…)"),
    AUTRE("Autre");

    private final String libelle;

    TypeVehicule(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
