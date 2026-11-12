package com.venach.kauris.ventes.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Un paiement reçu pour une facture. Il ne se modifie pas ; une erreur s'annule (il reste au journal). */
public record Reglement(UUID id, UUID tenantId, UUID factureId, LocalDate date, long montant, ModeReglement mode,
                        String reference, boolean annule, String motifAnnulation, UUID creePar, Instant creeLe) {

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
                                    String reference, UUID creePar, Instant maintenant) {
        return new Reglement(UUID.randomUUID(), tenantId, factureId, date, montant, mode, reference, false, "",
                creePar, maintenant);
    }

    public Reglement annuler(String motif) {
        if (annule) {
            throw new ErreurMetier("Ce règlement est déjà annulé.");
        }
        return new Reglement(id, tenantId, factureId, date, montant, mode, reference, true,
                Client.texte(motif, "Motif", 200), creePar, creeLe);
    }
}
