package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;

/**
 * Ce que la famille du salarié change à sa paie.
 *
 * @param enfants          enfants à charge (mineurs, ou moins de 27 ans qui étudient, sans revenu propre)
 * @param conjointACharge  conjoint sans revenu, compté parmi les personnes à charge (Togo) et couvert par la CMU
 */
public record Foyer(SituationFamiliale situation, int enfants, boolean conjointACharge) {

    public static final int ENFANTS_MAX = 20;

    public Foyer {
        if (situation == null) {
            throw new ErreurMetier("Indiquez la situation de famille.");
        }
        if (enfants < 0 || enfants > ENFANTS_MAX) {
            throw new ErreurMetier("Enfants à charge : entre 0 et " + ENFANTS_MAX + ".");
        }
        if (conjointACharge && situation != SituationFamiliale.MARIE) {
            conjointACharge = false;
        }
    }

    public static Foyer seul() {
        return new Foyer(SituationFamiliale.CELIBATAIRE, 0, false);
    }

    /** Togo : personnes à charge pour l'IRPP (conjoint sans revenu et enfants), avant le plafond. */
    public int personnesACharge() {
        return enfants + (conjointACharge ? 1 : 0);
    }

    /**
     * Côte d'Ivoire : nombre de parts pour la réduction d'impôt (article 120 du CGI). Une part pour une personne
     * seule sans enfant ; deux pour un couple marié ; une demi-part par enfant ; une personne seule avec enfants
     * part d'une part et demie, un veuf ou une veuve avec enfants est compté comme marié. Cinq parts au plus.
     */
    public BigDecimal parts() {
        BigDecimal demi = new BigDecimal("0.5");
        BigDecimal base;
        if (situation == SituationFamiliale.MARIE || (situation == SituationFamiliale.VEUF && enfants > 0)) {
            base = BigDecimal.valueOf(2);
        } else if (enfants > 0) {
            base = new BigDecimal("1.5");
        } else {
            base = BigDecimal.ONE;
        }
        BigDecimal parts = base.add(demi.multiply(BigDecimal.valueOf(enfants)));
        BigDecimal max = BigDecimal.valueOf(5);
        return (parts.compareTo(max) > 0 ? max : parts).stripTrailingZeros();
    }

    /** Côte d'Ivoire : personnes couvertes par la CMU (le salarié, son conjoint, ses enfants), avant le plafond. */
    public int personnesCmu() {
        return 1 + (situation == SituationFamiliale.MARIE ? 1 : 0) + enfants;
    }
}
