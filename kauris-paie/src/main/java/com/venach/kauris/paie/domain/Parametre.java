package com.venach.kauris.paie.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Les taux, plafonds et montants de la paie, avec leur valeur par défaut. Les sources publiques ne s'accordent pas
 * toujours et les lois de finances les changent : chaque entreprise peut donc en corriger un à partir d'une date,
 * dans les réglages de la paie, sans attendre une nouvelle version de Kauris. Les tranches de l'impôt, elles, sont
 * dans le code ({@link Bareme}) : elles changent par une loi, et une nouvelle version les suit.
 * <p>
 * Valeurs par défaut vérifiées en novembre 2026 : CNSS Togo (régime général 4 % salarié, 17,5 % employeur), décret
 * n° 2023-096/PR (AMU, 10 % dont au moins la moitié à l'employeur), CGI Togo (abattement de 28 %) ; CNPS Côte
 * d'Ivoire (taux au 1er janvier 2025), réforme de l'ITS (ordonnance de 2023, en vigueur au 1er janvier 2024).
 */
public enum Parametre {

    // ------------------------------------------------------------------ Togo
    TG_CNSS_SALARIE(PaysPaie.TOGO, Unite.TAUX, "4", "CNSS : part du salarié (pension)"),
    TG_CNSS_PENSION_EMPLOYEUR(PaysPaie.TOGO, Unite.TAUX, "12.5", "CNSS : pension, part de l'employeur"),
    TG_CNSS_FAMILLE_EMPLOYEUR(PaysPaie.TOGO, Unite.TAUX, "3", "CNSS : prestations familiales (employeur)"),
    TG_CNSS_RISQUES_EMPLOYEUR(PaysPaie.TOGO, Unite.TAUX, "2", "CNSS : risques professionnels (employeur)"),
    TG_CNSS_PLAFOND(PaysPaie.TOGO, Unite.MONTANT, "0", "CNSS : plafond mensuel du salaire soumis (0 = aucun)"),
    TG_AMU_SALARIE(PaysPaie.TOGO, Unite.TAUX, "5", "AMU : part du salarié"),
    TG_AMU_EMPLOYEUR(PaysPaie.TOGO, Unite.TAUX, "5", "AMU : part de l'employeur"),
    TG_IRPP_ABATTEMENT(PaysPaie.TOGO, Unite.TAUX, "28", "IRPP : abattement pour frais professionnels"),
    TG_IRPP_ABATTEMENT_LIMITE(PaysPaie.TOGO, Unite.MONTANT, "10000000",
            "IRPP : revenu annuel au-delà duquel l'abattement ne s'applique plus"),
    TG_IRPP_CHARGE_FAMILLE(PaysPaie.TOGO, Unite.MONTANT, "10000",
            "IRPP : déduction par personne à charge et par mois"),
    TG_IRPP_CHARGES_MAX(PaysPaie.TOGO, Unite.NOMBRE, "6", "IRPP : nombre maximum de personnes à charge"),

    // ------------------------------------------------------------------ Côte d'Ivoire
    CI_RETRAITE_SALARIE(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "6.3", "CNPS : retraite, part du salarié"),
    CI_RETRAITE_EMPLOYEUR(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "7.7", "CNPS : retraite, part de l'employeur"),
    CI_RETRAITE_PLAFOND(PaysPaie.COTE_D_IVOIRE, Unite.MONTANT, "3375000", "CNPS : plafond mensuel de la retraite"),
    CI_FAMILLE_EMPLOYEUR(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "5", "CNPS : prestations familiales (employeur)"),
    CI_MATERNITE_EMPLOYEUR(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "0.75", "CNPS : assurance maternité (employeur)"),
    CI_ACCIDENT_EMPLOYEUR(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "2",
            "CNPS : accidents du travail (employeur, de 2 à 5 % selon le secteur)"),
    CI_FAMILLE_PLAFOND(PaysPaie.COTE_D_IVOIRE, Unite.MONTANT, "70000",
            "CNPS : plafond mensuel des prestations familiales, maternité et accidents"),
    CI_CMU_PAR_PERSONNE(PaysPaie.COTE_D_IVOIRE, Unite.MONTANT, "1000", "CMU : cotisation mensuelle par personne"),
    CI_CMU_PART_EMPLOYEUR(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "50", "CMU : part payée par l'employeur"),
    CI_CMU_PERSONNES_MAX(PaysPaie.COTE_D_IVOIRE, Unite.NOMBRE, "8",
            "CMU : personnes couvertes au plus (salarié, conjoint, enfants)"),
    CI_ITS_RICF_DEMI_PART(PaysPaie.COTE_D_IVOIRE, Unite.MONTANT, "5500",
            "ITS : réduction pour charges de famille, par demi-part au-delà d'une part"),
    CI_IMPOT_PATRONAL_LOCAL(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "1.2",
            "Impôt patronal sur les salaires : personnel local"),
    CI_IMPOT_PATRONAL_EXPATRIE(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "10.4",
            "Impôt patronal sur les salaires : personnel expatrié"),
    CI_TAXE_APPRENTISSAGE(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "0.4", "FDFP : taxe d'apprentissage"),
    CI_FPC(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "0.6", "FDFP : formation professionnelle continue"),
    CI_FPC_COMPLEMENTAIRE(PaysPaie.COTE_D_IVOIRE, Unite.TAUX, "0.6",
            "FDFP : formation professionnelle continue, taxe additionnelle");

    /** Comment se lit la valeur. */
    public enum Unite {
        /** Pourcentage : « 12.5 » veut dire 12,5 %. */
        TAUX,
        /** Montant en francs CFA. */
        MONTANT,
        /** Un nombre entier (de personnes, de parts). */
        NOMBRE
    }

    private final PaysPaie pays;
    private final Unite unite;
    private final BigDecimal parDefaut;
    private final String libelle;

    Parametre(PaysPaie pays, Unite unite, String parDefaut, String libelle) {
        this.pays = pays;
        this.unite = unite;
        this.parDefaut = new BigDecimal(parDefaut);
        this.libelle = libelle;
    }

    public PaysPaie pays() {
        return pays;
    }

    public Unite unite() {
        return unite;
    }

    public BigDecimal parDefaut() {
        return parDefaut;
    }

    public String libelle() {
        return libelle;
    }

    public static List<Parametre> de(PaysPaie pays) {
        return Arrays.stream(values()).filter(p -> p.pays == pays).toList();
    }

    public static Optional<Parametre> parCode(String code) {
        try {
            return Optional.of(valueOf(code));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
