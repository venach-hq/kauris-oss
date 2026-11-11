package com.venach.kauris.kernel.organisation.domain;

/**
 * Rôles du noyau. Les deux premiers valent pour toute l'organisation ; les deux autres peuvent être
 * limités à un établissement. Les modules s'appuient dessus pour autoriser leurs actions.
 */
public enum Role {

    /** Tout, y compris nommer d'autres propriétaires. Il en reste toujours au moins un. */
    PROPRIETAIRE("Propriétaire", true),
    /** Gère les établissements, les membres et leurs rôles. */
    ADMINISTRATEUR("Administrateur", true),
    /** Saisit et modifie les données des modules. */
    GESTIONNAIRE("Gestionnaire", false),
    /** Consulte sans modifier. */
    LECTEUR("Lecteur", false);

    private final String libelle;
    private final boolean organisationEntiere;

    Role(String libelle, boolean organisationEntiere) {
        this.libelle = libelle;
        this.organisationEntiere = organisationEntiere;
    }

    public String libelle() {
        return libelle;
    }

    /** Vrai si le rôle ne peut pas être limité à un établissement. */
    public boolean organisationEntiere() {
        return organisationEntiere;
    }

    public boolean administre() {
        return this == PROPRIETAIRE || this == ADMINISTRATEUR;
    }

    public boolean peutModifier() {
        return this != LECTEUR;
    }
}
