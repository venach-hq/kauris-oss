package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/** Un élément de paie : une prime fixe du contrat, ou une prime ou une retenue d'un mois. */
public record Element(String libelle, TypeElement type, long montant) {

    public static final long MONTANT_MAX = 1_000_000_000L;

    public Element {
        libelle = libelle == null ? "" : libelle.strip().replaceAll("\\s+", " ");
        if (libelle.length() < 2 || libelle.length() > 80) {
            throw new ErreurMetier("Libellé de l'élément de paie : entre 2 et 80 caractères.");
        }
        if (type == null) {
            throw new ErreurMetier(libelle + " : choisissez le type.");
        }
        if (montant <= 0 || montant > MONTANT_MAX) {
            throw new ErreurMetier(libelle + " : un montant positif.");
        }
    }
}
