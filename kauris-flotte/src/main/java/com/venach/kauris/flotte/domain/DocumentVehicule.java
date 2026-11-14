package com.venach.kauris.flotte.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Un papier du véhicule (assurance, visite technique…) ; son expiration déclenche les alertes. */
public record DocumentVehicule(UUID id, UUID tenantId, UUID vehiculeId, TypeDocument type, String reference,
                               LocalDate emisLe, LocalDate expireLe, Long cout, String notes) {

    /** Prévenir à partir de 30 jours avant l'expiration. */
    public static final int PREAVIS_JOURS = 30;

    public DocumentVehicule {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(vehiculeId);
        if (type == null) {
            throw new ErreurMetier("Choisissez le type de document.");
        }
        reference = Textes.propre(reference, "Référence", 60, false);
        notes = Textes.propre(notes, "Notes", 300, false);
        if (emisLe != null && expireLe != null && expireLe.isBefore(emisLe)) {
            throw new ErreurMetier("L'expiration est avant la date d'émission.");
        }
        if (cout != null) {
            Textes.montant(cout, "Coût", true);
        }
    }

    /** Ce que l'écran saisit. */
    public record Saisie(UUID vehiculeId, TypeDocument type, String reference, LocalDate emisLe, LocalDate expireLe,
                         Long cout, String notes) {
    }

    public static DocumentVehicule de(UUID id, UUID tenantId, Saisie s) {
        return new DocumentVehicule(id, tenantId, s.vehiculeId(), s.type(), s.reference(), s.emisLe(), s.expireLe(),
                s.cout(), s.notes());
    }

    public boolean expire(LocalDate jour) {
        return expireLe != null && expireLe.isBefore(jour);
    }

    public boolean aRenouveler(LocalDate jour) {
        return expireLe != null && !expireLe.isAfter(jour.plusDays(PREAVIS_JOURS));
    }

    /** Jours avant l'expiration (négatif : expiré depuis). */
    public long joursRestants(LocalDate jour) {
        return expireLe == null ? Long.MAX_VALUE : java.time.temporal.ChronoUnit.DAYS.between(jour, expireLe);
    }
}
