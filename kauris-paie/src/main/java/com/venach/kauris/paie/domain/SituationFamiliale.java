package com.venach.kauris.paie.domain;

/** La situation de famille, qui compte pour l'impôt (parts en Côte d'Ivoire, personnes à charge au Togo). */
public enum SituationFamiliale {

    CELIBATAIRE("Célibataire"),
    MARIE("Marié(e)"),
    DIVORCE("Divorcé(e)"),
    VEUF("Veuf ou veuve");

    private final String libelle;

    SituationFamiliale(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
