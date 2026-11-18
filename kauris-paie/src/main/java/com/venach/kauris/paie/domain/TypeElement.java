package com.venach.kauris.paie.domain;

/** Ce qu'est un élément de paie, et donc ce qu'il subit. */
public enum TypeElement {

    /** Prime, indemnité, ancienneté, congés payés : soumise aux cotisations et à l'impôt, payée chaque mois. */
    GAIN_SOUMIS("Prime ou indemnité soumise", true, true),
    /** Prime non mensuelle (13e mois, bonus annuel) : soumise, imposée par différence quand les règles le prévoient. */
    PRIME_EXCEPTIONNELLE("Prime exceptionnelle (non mensuelle)", true, false),
    /** Logement, véhicule, nourriture fournis : soumis aux cotisations et à l'impôt, jamais payés en argent. */
    AVANTAGE_NATURE("Avantage en nature", true, true),
    /** Allocation pour frais d'emploi : payée, ni cotisée ni imposée, sauf pour un dirigeant ou un gérant. */
    FRAIS_EMPLOI("Frais d'emploi (non soumis, sauf dirigeant)", true, true),
    /** Remboursement de frais, indemnité exonérée : payé, sans cotisation ni impôt. */
    GAIN_NON_SOUMIS("Indemnité non soumise", true, true),
    /** Retenue disciplinaire, cotisation syndicale, saisie : retenue sur le net. */
    RETENUE("Retenue sur le net", false, false);

    private final String libelle;
    private final boolean gain;
    private final boolean dansLeContrat;

    TypeElement(String libelle, boolean gain, boolean dansLeContrat) {
        this.libelle = libelle;
        this.gain = gain;
        this.dansLeContrat = dansLeContrat;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai pour ce qui s'ajoute au brut ; faux pour une retenue. */
    public boolean gain() {
        return gain;
    }

    /** Vrai si l'élément peut être versé chaque mois par le contrat (pas une prime exceptionnelle ni une retenue). */
    public boolean dansLeContrat() {
        return dansLeContrat;
    }
}
