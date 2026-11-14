package com.venach.kauris.flotte.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un paiement du conducteur, de n'importe quel montant : il rembourse ce qui est dû, du plus ancien au plus récent
 * (voir {@link Compte}). Il ne s'efface pas : une erreur s'annule avec un motif et reste visible.
 *
 * @param numero reçu « VR-2026-000123 », sans trou
 */
public record Versement(UUID id, UUID tenantId, UUID contratId, String numero, LocalDate date, long montant,
                        ModeVersement mode, String reference, String saisiPar, Instant saisiLe, boolean annule,
                        String motifAnnulation, String annulePar, Instant annuleLe) {

    public Versement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(contratId);
        Objects.requireNonNull(numero);
        Objects.requireNonNull(date);
        Textes.montant(montant, "Montant", false);
        if (mode == null) {
            throw new ErreurMetier("Choisissez le mode de paiement.");
        }
        reference = Textes.propre(reference, "Référence", 80, false);
        if ((mode == ModeVersement.MOBILE_MONEY || mode == ModeVersement.PISPI) && reference.isEmpty()) {
            throw new ErreurMetier("Indiquez la référence de la transaction " + mode.libelle() + ".");
        }
        saisiPar = Textes.propre(saisiPar, "Saisi par", 150, false);
        motifAnnulation = Textes.propre(motifAnnulation, "Motif", 200, false);
        annulePar = Textes.propre(annulePar, "Annulé par", 150, false);
    }

    public Versement annuler(String motif, String parQui, Instant quand) {
        if (annule) {
            throw new ErreurMetier("Ce versement est déjà annulé.");
        }
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("Indiquez pourquoi ce versement est annulé.");
        }
        return new Versement(id, tenantId, contratId, numero, date, montant, mode, reference, saisiPar, saisiLe, true,
                motif, parQui, quand);
    }
}
