package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ce qui change un mois pour un salarié : absences non payées, heures supplémentaires, primes et retenues du mois.
 *
 * @param joursAbsence jours d'absence non payés, retirés des 30 jours du mois
 */
public record Variables(UUID tenantId, UUID periodeId, UUID salarieId, int joursAbsence, List<Heures> heures,
                        List<Element> elements) {

    public static final int ELEMENTS_MAX = 20;

    /** Des heures supplémentaires d'une même majoration. */
    public record Heures(String libelle, BigDecimal majoration, BigDecimal nombre) {

        public Heures {
            libelle = Textes.obligatoire(libelle, "Heures supplémentaires", 60);
            if (majoration == null || majoration.signum() < 0 || majoration.compareTo(BigDecimal.valueOf(300)) > 0) {
                throw new ErreurMetier(libelle + " : majoration entre 0 et 300 %.");
            }
            if (nombre == null || nombre.signum() <= 0 || nombre.compareTo(BigDecimal.valueOf(300)) > 0) {
                throw new ErreurMetier(libelle + " : un nombre d'heures entre 0 et 300.");
            }
            majoration = majoration.stripTrailingZeros();
            nombre = nombre.stripTrailingZeros();
        }
    }

    public Variables {
        if (joursAbsence < 0 || joursAbsence > CalculPaie.JOURS_DU_MOIS) {
            throw new ErreurMetier("Jours d'absence : entre 0 et " + CalculPaie.JOURS_DU_MOIS + ".");
        }
        heures = List.copyOf(heures == null ? List.of() : heures);
        elements = List.copyOf(elements == null ? List.of() : elements);
        if (elements.size() > ELEMENTS_MAX || heures.size() > 10) {
            throw new ErreurMetier("Au plus " + ELEMENTS_MAX + " éléments et 10 lignes d'heures par mois.");
        }
    }

    public static Variables aucune(UUID tenantId, UUID periodeId, UUID salarieId) {
        return new Variables(tenantId, periodeId, salarieId, 0, List.of(), List.of());
    }

    public boolean vides() {
        return joursAbsence == 0 && elements.isEmpty() && heures.isEmpty();
    }
}
