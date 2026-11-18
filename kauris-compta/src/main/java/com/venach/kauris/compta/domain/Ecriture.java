package com.venach.kauris.compta.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Une écriture comptable. Elle naît au <strong>brouillard</strong>, où elle se corrige ou se supprime ; validée, elle
 * reçoit son numéro (sans trou, par journal et par année) et ne change plus : une erreur se corrige par une
 * contre-passation (l'écriture inverse), puis une nouvelle écriture.
 *
 * @param journal  code du journal (VE, AC, TR, PA, OD, AN…)
 * @param numero   « VE-2026-00012 » une fois validée ; vide au brouillard
 * @param origine  module qui l'a produite (« ventes », « paie »…) ; « saisie » pour une écriture saisie à la main
 * @param reference identifiant de l'opération d'origine ; vide pour une saisie
 * @param inverseDe écriture contre-passée par celle-ci ; null sinon
 */
public record Ecriture(UUID id, UUID tenantId, String journal, String numero, LocalDate date, String piece,
                       String libelle, List<LigneEcriture> lignes, Statut statut, String origine, String reference,
                       UUID inverseDe, Instant creeLe, String creePar, Instant valideeLe, String valideePar) {

    public static final String SAISIE = "saisie";
    public static final int LIGNES_MAX = 300;

    public enum Statut {
        BROUILLARD("Brouillard"),
        VALIDEE("Validée");

        private final String libelle;

        Statut(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    public Ecriture {
        if (id == null || tenantId == null || date == null || statut == null || creeLe == null) {
            throw new ErreurMetier("Écriture incomplète.");
        }
        journal = journal == null ? "" : journal.strip().toUpperCase();
        if (!journal.matches("[A-Z][A-Z0-9]{1,3}")) {
            throw new ErreurMetier("Choisissez le journal.");
        }
        numero = numero == null ? "" : numero;
        piece = Textes.facultatif(piece, "Pièce", 40);
        libelle = Textes.facultatif(libelle, "Libellé", 160);
        if (libelle.isEmpty()) {
            throw new ErreurMetier("Indiquez le libellé de l'écriture.");
        }
        lignes = List.copyOf(lignes == null ? List.of() : lignes);
        if (lignes.size() < 2) {
            throw new ErreurMetier("Une écriture a au moins deux lignes.");
        }
        if (lignes.size() > LIGNES_MAX) {
            throw new ErreurMetier("Une écriture a au plus " + LIGNES_MAX + " lignes.");
        }
        long d = lignes.stream().mapToLong(LigneEcriture::debit).sum();
        long c = lignes.stream().mapToLong(LigneEcriture::credit).sum();
        if (d != c) {
            throw new ErreurMetier("L'écriture n'est pas équilibrée : " + d + " au débit, " + c + " au crédit (écart "
                    + Math.abs(d - c) + ").");
        }
        origine = origine == null || origine.isBlank() ? SAISIE : origine.strip();
        reference = reference == null ? "" : reference.strip();
        creePar = creePar == null ? "" : creePar;
        valideePar = valideePar == null ? "" : valideePar;
        if (statut == Statut.VALIDEE && numero.isEmpty()) {
            throw new ErreurMetier("Une écriture validée a un numéro.");
        }
    }

    public static Ecriture brouillard(UUID tenantId, String journal, LocalDate date, String piece, String libelle,
                                      List<LigneEcriture> lignes, String origine, String reference, UUID inverseDe,
                                      Instant maintenant, String par) {
        return new Ecriture(UUID.randomUUID(), tenantId, journal, "", date, piece, libelle, lignes,
                Statut.BROUILLARD, origine, reference, inverseDe, maintenant, par, null, "");
    }

    public boolean validee() {
        return statut == Statut.VALIDEE;
    }

    public boolean saisie() {
        return SAISIE.equals(origine);
    }

    public long total() {
        return lignes.stream().mapToLong(LigneEcriture::debit).sum();
    }

    /** Le même brouillard, corrigé. */
    public Ecriture corrigee(String nouveauJournal, LocalDate nouvelleDate, String nouvellePiece, String nouveauLibelle,
                             List<LigneEcriture> nouvellesLignes) {
        if (validee()) {
            throw new ErreurMetier("Une écriture validée ne se modifie plus : contre-passez-la.");
        }
        return new Ecriture(id, tenantId, nouveauJournal, "", nouvelleDate, nouvellePiece, nouveauLibelle,
                nouvellesLignes, statut, origine, reference, inverseDe, creeLe, creePar, null, "");
    }

    public Ecriture validee(String nouveauNumero, Instant quand, String par) {
        if (validee()) {
            throw new ErreurMetier("L'écriture " + numero + " est déjà validée.");
        }
        return new Ecriture(id, tenantId, journal, nouveauNumero, date, piece, libelle, lignes, Statut.VALIDEE,
                origine, reference, inverseDe, creeLe, creePar, quand, par);
    }

    /** L'écriture inverse, au brouillard, à cette date. */
    public Ecriture inverse(LocalDate jour, String motif, String nouvelleReference, Instant maintenant, String par) {
        String texte = "Contre-passation " + (numero.isEmpty() ? "" : numero + " ") + ": " + motif;
        return brouillard(tenantId, journal, jour, piece, texte.length() > 160 ? texte.substring(0, 160) : texte,
                lignes.stream().map(LigneEcriture::inverse).toList(), origine, nouvelleReference, id, maintenant, par);
    }

    /** La même écriture, avec ses lignes (lettrage changé). */
    public Ecriture avecLignes(List<LigneEcriture> nouvellesLignes) {
        return new Ecriture(id, tenantId, journal, numero, date, piece, libelle, nouvellesLignes, statut, origine,
                reference, inverseDe, creeLe, creePar, valideeLe, valideePar);
    }

    /** « VE-2026-00012 ». */
    public static String numero(String journal, int annee, long rang) {
        return journal + "-" + annee + "-" + String.format("%05d", rang);
    }
}
