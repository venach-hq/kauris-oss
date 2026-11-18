package com.venach.kauris.paie.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les écritures comptables de la paie d'un mois, une pièce par bulletin, datée du dernier jour du mois, avec les
 * comptes des règles de paie (SYSCOHADA par défaut) :
 * <pre>
 * D salaires, primes, heures supplémentaires        (661…)
 * D indemnités non soumises, frais d'emploi         (663…)
 * D charges sociales de l'employeur                 (664…)
 * D taxes sur les salaires                          (641…)
 *     C organismes sociaux, part salariale + patronale (431…)
 *     C impôt retenu à la source                      (447…)
 *     C taxes sur les salaires                        (447…)
 *     C avances, prêts et retenues                    (421…)
 *     C net à payer                                   (422…)
 * </pre>
 * Les avantages en nature n'y figurent pas : ils ne sont pas payés en argent. Chaque pièce est équilibrée.
 */
public final class EcrituresPaie {

    /** Une ligne d'écriture. */
    public record Ligne(LocalDate date, String journal, String piece, String compte, String libelle, long debit,
                        long credit) {
    }

    public static final String JOURNAL = "OD";

    private EcrituresPaie() {
    }

    public static List<Ligne> de(PeriodePaie p, List<Bulletin> bulletins) {
        List<Ligne> l = new ArrayList<>();
        LocalDate date = p.mois().atEndOfMonth();
        for (Bulletin b : bulletins) {
            String piece = b.brouillon() ? "BROUILLON-" + b.matricule() : b.numero();
            String qui = " - " + b.nomSalarie();
            Map<String, Long> debits = new LinkedHashMap<>();
            Map<String, Long> credits = new LinkedHashMap<>();
            Map<String, String> libelles = new LinkedHashMap<>();
            for (CalculPaie.Ligne x : b.lignes()) {
                switch (x.nature()) {
                    case GAIN, GAIN_NON_SOUMIS -> {
                        if (!x.compteCharge().isEmpty()) {
                            ajouter(debits, libelles, x.compteCharge(), x.gain(), x.nature() == CalculPaie.Nature.GAIN
                                    ? "Rémunérations" : "Indemnités");
                        }
                    }
                    case COTISATION -> {
                        ajouter(debits, libelles, x.compteCharge(), x.employeur(), "Charges sociales");
                        ajouter(credits, libelles, x.compte(), x.salarie() + x.employeur(), "Cotisations "
                                + x.organisme());
                    }
                    case IMPOT -> ajouter(credits, libelles, x.compte(), x.salarie(), x.libelle());
                    case TAXE_EMPLOYEUR -> {
                        ajouter(debits, libelles, x.compteCharge(), x.employeur(), "Taxes sur les salaires");
                        ajouter(credits, libelles, x.compte(), x.employeur(), x.libelle());
                    }
                    case RETENUE -> ajouter(credits, libelles, x.compte(), x.salarie(), "Avances et retenues");
                }
            }
            ajouter(credits, libelles, b.resultat().compteNet(), b.net(), "Net à payer");
            debits.forEach((compte, m) -> l.add(new Ligne(date, JOURNAL, piece, compte, libelles.get(compte) + qui, m,
                    0)));
            credits.forEach((compte, m) -> l.add(new Ligne(date, JOURNAL, piece, compte, libelles.get(compte) + qui, 0,
                    m)));
        }
        return l;
    }

    private static void ajouter(Map<String, Long> m, Map<String, String> libelles, String compte, long montant,
                                String libelle) {
        if (montant == 0) {
            return;
        }
        String c = compte == null || compte.isEmpty() ? "471000" : compte;
        m.merge(c, montant, Long::sum);
        libelles.putIfAbsent(c, libelle);
    }
}
