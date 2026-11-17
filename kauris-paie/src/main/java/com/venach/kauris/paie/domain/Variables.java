package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.util.List;
import java.util.UUID;

/**
 * Ce qui change un mois pour un salarié : absences non payées, primes du mois, avances et autres retenues.
 *
 * @param joursAbsence jours d'absence non payés, retirés des 30 jours du mois
 */
public record Variables(UUID tenantId, UUID periodeId, UUID salarieId, int joursAbsence, List<Element> elements) {

    public static final int ELEMENTS_MAX = 20;

    public Variables {
        if (joursAbsence < 0 || joursAbsence > CalculPaie.JOURS_DU_MOIS) {
            throw new ErreurMetier("Jours d'absence : entre 0 et " + CalculPaie.JOURS_DU_MOIS + ".");
        }
        elements = List.copyOf(elements == null ? List.of() : elements);
        if (elements.size() > ELEMENTS_MAX) {
            throw new ErreurMetier("Au plus " + ELEMENTS_MAX + " éléments par mois.");
        }
    }

    public static Variables aucune(UUID tenantId, UUID periodeId, UUID salarieId) {
        return new Variables(tenantId, periodeId, salarieId, 0, List.of());
    }

    public boolean vides() {
        return joursAbsence == 0 && elements.isEmpty();
    }
}
