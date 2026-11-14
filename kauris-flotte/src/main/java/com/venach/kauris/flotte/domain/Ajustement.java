package com.venach.kauris.flotte.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Une somme en plus ou en moins sur le compte du conducteur, hors versements journaliers : une avance qu'on lui
 * a faite, une amende ou un dommage à sa charge (il les doit), une remise qu'on lui accorde (en moins).
 */
public record Ajustement(UUID id, UUID tenantId, UUID contratId, LocalDate date, Type type, long montant, String motif,
                         String saisiPar, Instant saisiLe, boolean annule, String motifAnnulation) {

    public enum Type {
        AVANCE("Avance au conducteur", true),
        AMENDE("Amende à sa charge", true),
        DOMMAGE("Dommage à sa charge", true),
        REMISE("Remise accordée", false);

        private final String libelle;
        private final boolean du;

        Type(String libelle, boolean du) {
            this.libelle = libelle;
            this.du = du;
        }

        public String libelle() {
            return libelle;
        }

        /** Vrai : le conducteur le doit ; faux : ça réduit ce qu'il doit. */
        public boolean du() {
            return du;
        }
    }

    public Ajustement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(contratId);
        Objects.requireNonNull(date);
        if (type == null) {
            throw new ErreurMetier("Choisissez le type.");
        }
        Textes.montant(montant, "Montant", false);
        motif = Textes.propre(motif, "Motif", 200, true);
        saisiPar = Textes.propre(saisiPar, "Saisi par", 150, false);
        motifAnnulation = Textes.propre(motifAnnulation, "Motif", 200, false);
    }

    public Ajustement annuler(String motifAnnul) {
        if (annule) {
            throw new ErreurMetier("Déjà annulé.");
        }
        if (motifAnnul == null || motifAnnul.isBlank()) {
            throw new ErreurMetier("Indiquez pourquoi.");
        }
        return new Ajustement(id, tenantId, contratId, date, type, montant, motif, saisiPar, saisiLe, true, motifAnnul);
    }
}
