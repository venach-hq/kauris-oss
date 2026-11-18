package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Une avance sur salaire, un acompte ou un prêt au salarié, remboursé par retenues mensuelles à partir d'un mois.
 * Chaque mois, la retenue vaut la mensualité, sans dépasser ce qui reste dû ni le net disponible : le net ne devient
 * jamais négatif, le reste attend le mois suivant. Ce qui a été retenu se lit sur les bulletins clos.
 */
public record Avance(UUID id, UUID tenantId, UUID salarieId, Type type, String libelle, long montant,
                     long mensualite, YearMonth premierMois, LocalDate accordeeLe, boolean annulee, String note,
                     Instant creeLe, String creePar) {

    public enum Type {
        AVANCE("Avance sur salaire"),
        ACOMPTE("Acompte"),
        PRET("Prêt");

        private final String libelle;

        Type(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Préfixe du code des lignes de bulletin qui remboursent une avance : « AV:uuid ». */
    public static final String PREFIXE = "AV:";

    public Avance {
        if (type == null) {
            throw new ErreurMetier("Choisissez le type : avance, acompte ou prêt.");
        }
        libelle = Textes.obligatoire(libelle, "Libellé", 80);
        if (montant <= 0 || montant > Element.MONTANT_MAX) {
            throw new ErreurMetier("Montant : un montant positif.");
        }
        if (mensualite <= 0 || mensualite > montant) {
            throw new ErreurMetier("Retenue mensuelle : un montant positif, au plus le montant total.");
        }
        if (premierMois == null || accordeeLe == null) {
            throw new ErreurMetier("Indiquez la date et le premier mois de retenue.");
        }
        note = Textes.facultatif(note, "Note", 300);
        creePar = creePar == null ? "" : creePar;
    }

    public String code() {
        return PREFIXE + id;
    }

    public Avance annuler() {
        return new Avance(id, tenantId, salarieId, type, libelle, montant, mensualite, premierMois, accordeeLe, true,
                note, creeLe, creePar);
    }

    /** Ce qu'il faudrait retenir ce mois-ci, connaissant ce qui est déjà remboursé (avant le plafond du net). */
    public long aRetenir(YearMonth mois, long dejaRembourse) {
        if (annulee || mois.isBefore(premierMois)) {
            return 0;
        }
        return Math.max(0, Math.min(mensualite, montant - dejaRembourse));
    }
}
