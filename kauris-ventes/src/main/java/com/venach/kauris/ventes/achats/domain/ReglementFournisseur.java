package com.venach.kauris.ventes.achats.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.ventes.domain.ModeReglement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un paiement fait à un fournisseur pour une facture. Il ne se modifie pas ; une erreur s'annule (il reste, barré).
 *
 * @param compteId le compte de trésorerie d'où l'argent est parti (Kauris Cloud) ; null sinon
 */
public record ReglementFournisseur(UUID id, UUID tenantId, UUID factureId, LocalDate date, long montant,
                                   ModeReglement mode, String reference, UUID compteId, boolean annule,
                                   String motifAnnulation, UUID creePar, Instant creeLe) {

    public ReglementFournisseur {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(factureId);
        Objects.requireNonNull(date);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(creeLe);
        if (montant <= 0) {
            throw new ErreurMetier("Montant du règlement : un montant positif.");
        }
        reference = Textes.texte(reference, "Référence du règlement", 80);
        motifAnnulation = motifAnnulation == null ? "" : motifAnnulation;
    }

    public ReglementFournisseur annuler(String motif) {
        if (annule) {
            throw new ErreurMetier("Ce règlement est déjà annulé.");
        }
        return new ReglementFournisseur(id, tenantId, factureId, date, montant, mode, reference, compteId, true,
                Textes.texte(motif, "Motif", 200), creePar, creeLe);
    }
}
