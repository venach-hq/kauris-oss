package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.entreprise.domain.Pays;

import java.util.Optional;

/**
 * Les pays dont la paie sait calculer les cotisations et l'impôt. Le pays vient de la fiche de l'entreprise : une
 * entreprise paie selon les règles du pays où elle est installée.
 */
public enum PaysPaie {

    TOGO(Pays.TOGO, "CNSS", "AMU", "IRPP", "Office togolais des recettes (OTR)", "cnss"),
    COTE_D_IVOIRE(Pays.COTE_D_IVOIRE, "CNPS", "CMU", "ITS", "Direction générale des impôts (DGI)", "cnps");

    private final Pays pays;
    private final String caisseSociale;
    private final String assuranceMaladie;
    private final String impot;
    private final String administrationFiscale;
    private final String champEmployeur;

    PaysPaie(Pays pays, String caisseSociale, String assuranceMaladie, String impot, String administrationFiscale,
             String champEmployeur) {
        this.pays = pays;
        this.caisseSociale = caisseSociale;
        this.assuranceMaladie = assuranceMaladie;
        this.impot = impot;
        this.administrationFiscale = administrationFiscale;
        this.champEmployeur = champEmployeur;
    }

    public Pays pays() {
        return pays;
    }

    public String nom() {
        return pays.nom();
    }

    /** CNSS au Togo, CNPS en Côte d'Ivoire. */
    public String caisseSociale() {
        return caisseSociale;
    }

    /** AMU au Togo, CMU en Côte d'Ivoire. */
    public String assuranceMaladie() {
        return assuranceMaladie;
    }

    /** IRPP au Togo, ITS en Côte d'Ivoire. */
    public String impot() {
        return impot;
    }

    public String administrationFiscale() {
        return administrationFiscale;
    }

    /** Le champ de la fiche entreprise qui porte le numéro d'employeur à la caisse sociale. */
    public String champEmployeur() {
        return champEmployeur;
    }

    public static Optional<PaysPaie> de(Pays pays) {
        for (PaysPaie p : values()) {
            if (p.pays == pays) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
