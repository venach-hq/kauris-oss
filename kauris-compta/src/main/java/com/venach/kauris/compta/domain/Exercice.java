package com.venach.kauris.compta.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un exercice comptable : l'année civile (SYSCOHADA, art. 7 de l'Acte uniforme). Ouvert, il reçoit des écritures ;
 * clos, plus aucune : ses soldes de bilan et son résultat sont repris en à-nouveaux dans l'exercice suivant.
 */
public record Exercice(UUID tenantId, int annee, boolean clos, Instant closLe, String closPar) {

    public Exercice {
        closPar = closPar == null ? "" : closPar;
    }

    public static Exercice ouvert(UUID tenantId, int annee) {
        return new Exercice(tenantId, annee, false, null, "");
    }

    public LocalDate debut() {
        return LocalDate.of(annee, 1, 1);
    }

    public LocalDate fin() {
        return LocalDate.of(annee, 12, 31);
    }

    public boolean contient(LocalDate date) {
        return date.getYear() == annee;
    }

    public Exercice cloture(Instant quand, String par) {
        return new Exercice(tenantId, annee, true, quand, par);
    }

    public Exercice rouvert() {
        return new Exercice(tenantId, annee, false, null, "");
    }
}
