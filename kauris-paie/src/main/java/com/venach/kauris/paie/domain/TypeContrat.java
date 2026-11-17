package com.venach.kauris.paie.domain;

/** Les contrats de travail. */
public enum TypeContrat {

    CDI("Contrat à durée indéterminée (CDI)"),
    CDD("Contrat à durée déterminée (CDD)"),
    TEMPORAIRE("Travail temporaire ou occasionnel"),
    APPRENTISSAGE("Apprentissage");

    private final String libelle;

    TypeContrat(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /** Un CDD a une date de fin prévue. */
    public boolean aUneFinPrevue() {
        return this == CDD || this == TEMPORAIRE;
    }
}
