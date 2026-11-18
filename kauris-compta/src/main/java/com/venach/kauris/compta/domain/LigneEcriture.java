package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/**
 * Une ligne d'écriture : un compte, au débit ou au crédit.
 *
 * @param tiers    identifiant du tiers (« CLI:… », « FRS:… », « SAL:… ») pour les comptes de tiers ; vide sinon
 * @param lettrage code de lettrage (« A », « AB »…) ; vide si la ligne n'est pas lettrée
 */
public record LigneEcriture(String compte, String tiers, String tiersNom, String libelle, long debit, long credit,
                            String lettrage) {

    public static final long MONTANT_MAX = 1_000_000_000_000L;

    public LigneEcriture {
        compte = Compte.numero(compte);
        tiers = Textes.facultatif(tiers, "Tiers", 60);
        tiersNom = Textes.facultatif(tiersNom, "Nom du tiers", 120);
        libelle = Textes.facultatif(libelle, "Libellé de la ligne", 160);
        lettrage = lettrage == null ? "" : lettrage.strip();
        if (debit < 0 || credit < 0 || debit > MONTANT_MAX || credit > MONTANT_MAX) {
            throw new ErreurMetier("Compte " + compte + " : un montant positif.");
        }
        if ((debit > 0) == (credit > 0)) {
            throw new ErreurMetier("Compte " + compte + " : un montant au débit ou au crédit, pas les deux.");
        }
    }

    public static LigneEcriture debit(String compte, long montant, String libelle) {
        return new LigneEcriture(compte, "", "", libelle, montant, 0, "");
    }

    public static LigneEcriture credit(String compte, long montant, String libelle) {
        return new LigneEcriture(compte, "", "", libelle, 0, montant, "");
    }

    /** Débit − crédit. */
    public long solde() {
        return debit - credit;
    }

    public LigneEcriture lettree(String code) {
        return new LigneEcriture(compte, tiers, tiersNom, libelle, debit, credit, code == null ? "" : code);
    }

    /** La ligne inverse (contre-passation), sans lettrage. */
    public LigneEcriture inverse() {
        return new LigneEcriture(compte, tiers, tiersNom, libelle, credit, debit, "");
    }

    public LigneEcriture pour(String nouveauTiers, String nom) {
        return new LigneEcriture(compte, nouveauTiers, nom, libelle, debit, credit, lettrage);
    }
}
