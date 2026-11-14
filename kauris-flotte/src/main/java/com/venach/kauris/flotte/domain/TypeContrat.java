package com.venach.kauris.flotte.domain;

public enum TypeContrat {
    /** Le conducteur verse un montant par jour travaillé, sans fin prévue. */
    LOCATION("Location (versement journalier)", true),
    /** Versements jusqu'au prix total : le véhicule devient alors au conducteur (« campé »). */
    LOCATION_VENTE("Location-vente", true),
    /** Le conducteur est salarié : pas de versement. */
    SALARIE("Salarié (sans versement)", false);

    private final String libelle;
    private final boolean versements;

    TypeContrat(String libelle, boolean versements) {
        this.libelle = libelle;
        this.versements = versements;
    }

    public String libelle() {
        return libelle;
    }

    public boolean versements() {
        return versements;
    }
}
