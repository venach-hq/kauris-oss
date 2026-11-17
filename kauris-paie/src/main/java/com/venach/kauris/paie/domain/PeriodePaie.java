package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;

/**
 * Un mois de paie. Ouvert, ses bulletins se recalculent à chaque lecture (on corrige un contrat ou une prime, le
 * bulletin suit) ; clos, ils sont figés et numérotés, et servent aux déclarations et au virement.
 *
 * @param payeLe jour où les salaires ont été versés (marqué après le virement)
 */
public record PeriodePaie(UUID id, UUID tenantId, YearMonth mois, PaysPaie pays, boolean close, Instant closeLe,
                          String closePar, LocalDate payeLe, Instant creeLe) {

    public PeriodePaie {
        if (mois == null || pays == null) {
            throw new IllegalArgumentException("Mois et pays requis");
        }
        closePar = closePar == null ? "" : closePar;
    }

    public static PeriodePaie ouvrir(UUID tenantId, YearMonth mois, PaysPaie pays, Instant maintenant) {
        return new PeriodePaie(UUID.randomUUID(), tenantId, mois, pays, false, null, "", null, maintenant);
    }

    public PeriodePaie clore(String par, Instant quand) {
        if (close) {
            throw new ErreurMetier("Ce mois est déjà clos.");
        }
        return new PeriodePaie(id, tenantId, mois, pays, true, quand, par, null, creeLe);
    }

    /** Rouvre le mois (avant le paiement) : ses bulletins redeviennent des brouillons. */
    public PeriodePaie rouvrir() {
        if (!close) {
            throw new ErreurMetier("Ce mois est déjà ouvert.");
        }
        if (payeLe != null) {
            throw new ErreurMetier("Les salaires de ce mois sont marqués comme payés : il ne se rouvre plus.");
        }
        return new PeriodePaie(id, tenantId, mois, pays, false, null, "", null, creeLe);
    }

    public PeriodePaie payer(LocalDate jour) {
        if (!close) {
            throw new ErreurMetier("Clôturez le mois avant de marquer les salaires comme payés.");
        }
        if (payeLe != null) {
            throw new ErreurMetier("Les salaires de ce mois sont déjà marqués comme payés.");
        }
        return new PeriodePaie(id, tenantId, mois, pays, close, closeLe, closePar, jour, creeLe);
    }

    /** « novembre 2026 ». */
    public String libelle() {
        return libelle(mois);
    }

    public static String libelle(YearMonth mois) {
        return mois.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + mois.getYear();
    }
}
