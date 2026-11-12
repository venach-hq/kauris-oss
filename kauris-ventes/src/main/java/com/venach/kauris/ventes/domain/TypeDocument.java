package com.venach.kauris.ventes.domain;

/** Les documents de vente, chacun avec sa numérotation par année : DE-2026-0001, FA-2026-0001, AV-2026-0001. */
public enum TypeDocument {
    DEVIS("Devis", "DE"),
    FACTURE("Facture", "FA"),
    AVOIR("Avoir", "AV");

    private final String libelle;
    private final String prefixe;

    TypeDocument(String libelle, String prefixe) {
        this.libelle = libelle;
        this.prefixe = prefixe;
    }

    public String libelle() {
        return libelle;
    }

    public String prefixe() {
        return prefixe;
    }

    /** « FA-2026-0012 ». */
    public String numero(int annee, long rang) {
        return prefixe + "-" + annee + "-" + String.format("%04d", rang);
    }
}
