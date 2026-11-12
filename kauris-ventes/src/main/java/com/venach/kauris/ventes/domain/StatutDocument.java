package com.venach.kauris.ventes.domain;

/** Le cycle de vie d'un document de vente. Seul un brouillon se modifie. */
public enum StatutDocument {
    /** En préparation : modifiable, sans numéro. */
    BROUILLON("Brouillon"),
    /** Émis : numéroté, figé. Pour une facture, c'est le point de départ des règlements. */
    EMIS("Émis"),
    /** Devis accepté par le client. */
    ACCEPTE("Accepté"),
    /** Devis refusé par le client. */
    REFUSE("Refusé"),
    /** Devis transformé en facture. */
    FACTURE("Facturé"),
    /** Devis abandonné (une facture émise ne s'annule pas : on fait un avoir). */
    ANNULE("Annulé");

    private final String libelle;

    StatutDocument(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
