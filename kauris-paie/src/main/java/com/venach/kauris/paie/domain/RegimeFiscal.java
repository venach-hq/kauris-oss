package com.venach.kauris.paie.domain;

/** Le régime d'imposition du salarié ; ce qu'il change se règle dans les règles de l'impôt. */
public enum RegimeFiscal {

    NORMAL("Salarié"),
    DIRIGEANT("Dirigeant ou cadre assimilé (frais d'emploi imposables)"),
    GERANT("Gérant ou associé majoritaire (frais forfaitaires)"),
    APPRENTI("Apprenti");

    private final String libelle;

    RegimeFiscal(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
