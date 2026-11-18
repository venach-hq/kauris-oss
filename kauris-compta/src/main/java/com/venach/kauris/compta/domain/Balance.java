package com.venach.kauris.compta.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * La balance : pour chaque compte, le report (à-nouveaux et mouvements avant la période), les mouvements de la période
 * et le solde. La même chose par tiers pour un compte de tiers (balance auxiliaire).
 */
public record Balance(List<Ligne> lignes, Ligne total) {

    /** Une ligne de balance ; {@code cle} est le numéro de compte ou l'identifiant du tiers. */
    public record Ligne(String cle, String libelle, long reportDebit, long reportCredit, long debit, long credit) {

        public long solde() {
            return reportDebit - reportCredit + debit - credit;
        }

        public long soldeDebiteur() {
            return Math.max(0, solde());
        }

        public long soldeCrediteur() {
            return Math.max(0, -solde());
        }

        public boolean vide() {
            return reportDebit == 0 && reportCredit == 0 && debit == 0 && credit == 0;
        }

        Ligne plus(Ligne o) {
            return new Ligne(cle, libelle, reportDebit + o.reportDebit, reportCredit + o.reportCredit, debit + o.debit,
                    credit + o.credit);
        }
    }

    /**
     * @param mouvements les mouvements de l'exercice jusqu'à {@code au}
     * @param du         début de la période : avant, ou au journal des à-nouveaux, c'est du report
     * @param libelles   libellé de chaque clé
     */
    public static Balance de(List<Mouvement> mouvements, LocalDate du, LocalDate au, Function<Mouvement, String> cle,
                             Map<String, String> libelles) {
        Map<String, Ligne> parCle = new TreeMap<>();
        for (Mouvement m : mouvements) {
            if (m.date().isAfter(au)) {
                continue;
            }
            String k = cle.apply(m);
            boolean report = m.aNouveau() || m.date().isBefore(du);
            Ligne l = report ? new Ligne(k, "", m.debit(), m.credit(), 0, 0) : new Ligne(k, "", 0, 0, m.debit(),
                    m.credit());
            parCle.merge(k, new Ligne(k, libelles.getOrDefault(k, ""), 0, 0, 0, 0).plus(l), Ligne::plus);
        }
        List<Ligne> lignes = new ArrayList<>();
        Ligne total = new Ligne("", "Total", 0, 0, 0, 0);
        for (Ligne l : parCle.values()) {
            if (!l.vide()) {
                lignes.add(l);
                total = total.plus(l);
            }
        }
        return new Balance(List.copyOf(lignes), total);
    }

    /** Par compte. */
    public static Balance parCompte(List<Mouvement> mouvements, LocalDate du, LocalDate au,
                                    Map<String, String> libelles) {
        return de(mouvements, du, au, Mouvement::compte, libelles);
    }

    /** Les soldes de fin de période, par compte (débit − crédit). */
    public Map<String, Long> soldes() {
        Map<String, Long> s = new TreeMap<>();
        lignes.forEach(l -> s.put(l.cle(), l.solde()));
        return s;
    }

    /** Vrai si débits et crédits sont égaux (report et période). */
    public boolean equilibree() {
        return total.reportDebit() == total.reportCredit() && total.debit() == total.credit();
    }
}
