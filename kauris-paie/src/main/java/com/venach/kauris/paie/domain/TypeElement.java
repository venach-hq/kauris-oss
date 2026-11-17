package com.venach.kauris.paie.domain;

/** Ce qu'est un élément de paie, et donc ce qu'il subit. */
public enum TypeElement {

    /** Prime, indemnité ou heures supplémentaires : soumise aux cotisations et à l'impôt, payée au salarié. */
    GAIN_SOUMIS("Prime ou indemnité soumise", true),
    /** Logement, véhicule, nourriture fournis : soumis aux cotisations et à l'impôt, mais pas payés en argent. */
    AVANTAGE_NATURE("Avantage en nature", true),
    /** Remboursement de frais, indemnité exonérée : payé au salarié, sans cotisation ni impôt. */
    GAIN_NON_SOUMIS("Indemnité non soumise", true),
    /** Avance, acompte, remboursement de prêt : retenu sur le net à payer. */
    RETENUE("Retenue sur le net", false);

    private final String libelle;
    private final boolean gain;

    TypeElement(String libelle, boolean gain) {
        this.libelle = libelle;
        this.gain = gain;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai pour ce qui s'ajoute au brut ; faux pour une retenue. */
    public boolean gain() {
        return gain;
    }
}
