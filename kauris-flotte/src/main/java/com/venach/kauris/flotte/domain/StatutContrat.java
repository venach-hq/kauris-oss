package com.venach.kauris.flotte.domain;

public enum StatutContrat {
    EN_COURS("En cours"),
    TERMINE("Terminé"),
    RESILIE("Résilié");

    private final String libelle;

    StatutContrat(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
