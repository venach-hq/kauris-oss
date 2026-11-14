package com.venach.kauris.flotte.domain;

public enum TypeDocument {
    ASSURANCE("Assurance"),
    VISITE_TECHNIQUE("Visite technique"),
    CARTE_GRISE("Carte grise"),
    TAXE("Taxe, vignette"),
    AUTORISATION("Autorisation de transport, licence"),
    AUTRE("Autre");

    private final String libelle;

    TypeDocument(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
