package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

/**
 * Ce que la famille du salarié change à sa paie. Les règles du pays disent comment elle compte (personnes à charge,
 * parts, personnes couvertes par l'assurance maladie).
 *
 * @param enfants          enfants à charge au sens de la loi du pays (âge, études, sans revenu propre)
 * @param conjointACharge  conjoint sans revenu
 * @param autresPersonnes  autres personnes à charge effective (ascendants, collatéraux), quand la loi les compte
 */
public record Foyer(SituationFamiliale situation, int enfants, boolean conjointACharge, int autresPersonnes) {

    public static final int MAX = 20;

    public Foyer {
        if (situation == null) {
            throw new ErreurMetier("Indiquez la situation de famille.");
        }
        if (enfants < 0 || enfants > MAX || autresPersonnes < 0 || autresPersonnes > MAX) {
            throw new ErreurMetier("Personnes à charge : entre 0 et " + MAX + ".");
        }
        if (conjointACharge && situation != SituationFamiliale.MARIE) {
            conjointACharge = false;
        }
    }

    public static Foyer seul() {
        return new Foyer(SituationFamiliale.CELIBATAIRE, 0, false, 0);
    }

    /** Personnes à charge pour l'impôt : conjoint sans revenu, enfants et autres personnes, avant le plafond. */
    public int personnesACharge() {
        return enfants + (conjointACharge ? 1 : 0) + autresPersonnes;
    }

    /** Personnes couvertes par une cotisation par personne (assurance maladie) : le salarié, son conjoint, ses enfants. */
    public int personnesCouvertes() {
        return 1 + (situation == SituationFamiliale.MARIE ? 1 : 0) + enfants;
    }
}
