package com.venach.kauris.ventes.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un paiement reçu pour une facture. Il ne se modifie pas ; une erreur s'annule (il reste au journal).
 *
 * @param compteId le compte de trésorerie où l'argent est arrivé, si l'application en tient (Kauris Cloud) ;
 *                 null sinon
 */
public record Reglement(UUID id, UUID tenantId, UUID factureId, LocalDate date, long montant, ModeReglement mode,
                        String reference, UUID compteId, boolean annule, String motifAnnulation, UUID creePar,
                        Instant creeLe) {

    public Reglement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(factureId);
        Objects.requireNonNull(date);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(creeLe);
        if (montant <= 0) {
            throw new ErreurMetier("Montant du règlement : un montant positif.");
        }
        reference = Client.texte(reference, "Référence du règlement", 80);
        motifAnnulation = motifAnnulation == null ? "" : motifAnnulation;
    }

    public static Reglement nouveau(UUID tenantId, UUID factureId, LocalDate date, long montant, ModeReglement mode,
                                    String reference, UUID compteId, UUID creePar, Instant maintenant) {
        return new Reglement(UUID.randomUUID(), tenantId, factureId, date, montant, mode, reference, compteId, false,
                "", creePar, maintenant);
    }

    public Reglement annuler(String motif) {
        if (annule) {
            throw new ErreurMetier("Ce règlement est déjà annulé.");
        }
        return new Reglement(id, tenantId, factureId, date, montant, mode, reference, compteId, true,
                Client.texte(motif, "Motif", 200), creePar, creeLe);
    }
}
