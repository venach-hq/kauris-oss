package com.venach.kauris.ventes.stock.domain;

/** Ce qui fait bouger le stock. {@code entree} : la quantité s'ajoute ; sinon elle se retire. */
public enum TypeMouvement {
    STOCK_INITIAL("Stock initial", true, true),
    ENTREE("Entrée", true, true),
    RECEPTION_ACHAT("Réception fournisseur", true, false),
    RETOUR_VENTE("Retour client (avoir)", true, false),
    AJUSTEMENT_PLUS("Inventaire : en plus", true, false),
    TRANSFERT_ENTREE("Transfert reçu", true, false),
    SORTIE("Sortie", false, true),
    VENTE("Vente (facture)", false, false),
    AJUSTEMENT_MOINS("Inventaire : en moins", false, false),
    TRANSFERT_SORTIE("Transfert envoyé", false, false);

    private final String libelle;
    private final boolean entree;
    /** Saisissable à la main dans l'écran « Entrée ou sortie ». */
    private final boolean manuel;

    TypeMouvement(String libelle, boolean entree, boolean manuel) {
        this.libelle = libelle;
        this.entree = entree;
        this.manuel = manuel;
    }

    public String libelle() {
        return libelle;
    }

    public boolean entree() {
        return entree;
    }

    public boolean manuel() {
        return manuel;
    }
}
