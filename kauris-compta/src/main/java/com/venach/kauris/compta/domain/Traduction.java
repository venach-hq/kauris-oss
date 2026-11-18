package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.comptabilite.OperationComptable;

import java.util.ArrayList;
import java.util.List;

/** Traduit une opération reçue d'un module en lignes d'écriture, selon les imputations de l'entreprise. */
public final class Traduction {

    private Traduction() {
    }

    public static List<LigneEcriture> lignes(OperationComptable o, List<Imputation> imputations) {
        List<LigneEcriture> lignes = new ArrayList<>();
        for (OperationComptable.Ligne l : o.lignes()) {
            String compte = l.role() == OperationComptable.Role.COMPTE ? l.compte()
                    : Imputation.compte(imputations, l.role(), l.precision()).orElseThrow(() -> new ErreurMetier(
                    "Aucun compte pour « " + l.role().libelle() + (l.precision().isEmpty() ? "" : " : "
                            + Imputation.PRECISIONS.getOrDefault(l.precision(), l.precision()))
                            + " » : complétez les imputations."));
            String libelle = l.libelle().isEmpty() ? o.libelle() : l.libelle();
            lignes.add(new LigneEcriture(compte, l.tiers(), l.tiersNom(),
                    libelle.length() > 160 ? libelle.substring(0, 160) : libelle, l.debit(), l.credit(), ""));
        }
        return lignes;
    }
}
