package com.venach.kauris.compta.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * L'état de TVA d'un mois, à reporter sur la déclaration : TVA collectée (comptes 443), TVA déductible (445),
 * crédit du mois précédent (4449) ; TVA due ou crédit à reporter. Les écritures de liquidation de la TVA ne sont pas
 * comptées dans le mois.
 *
 * @param chiffreAffaires ventes du mois hors taxes (comptes 70, nets des réductions)
 * @param collectee       par compte 443…
 * @param deductible      par compte 445…
 * @param creditAnterieur solde débiteur du compte 4449 à la fin du mois précédent
 */
public record EtatTva(YearMonth mois, long chiffreAffaires, Map<String, Long> collectee, Map<String, Long> deductible,
                      long creditAnterieur) {

    public static final String ORIGINE_LIQUIDATION = "tva";
    public static final String COMPTE_DUE = "444100";
    public static final String COMPTE_CREDIT = "444900";

    public static EtatTva de(YearMonth mois, List<Mouvement> mouvementsDuMois, long soldeCreditAvant) {
        long ca = 0;
        Map<String, Long> col = new TreeMap<>();
        Map<String, Long> ded = new TreeMap<>();
        for (Mouvement m : mouvementsDuMois) {
            if (m.aNouveau() || ORIGINE_LIQUIDATION.equals(m.origine()) || !YearMonth.from(m.date()).equals(mois)) {
                continue;
            }
            if (m.compte().startsWith("70")) {
                ca += m.credit() - m.debit();
            } else if (m.compte().startsWith("443")) {
                col.merge(m.compte(), m.credit() - m.debit(), Long::sum);
            } else if (m.compte().startsWith("445")) {
                ded.merge(m.compte(), m.debit() - m.credit(), Long::sum);
            }
        }
        col.values().removeIf(v -> v == 0);
        ded.values().removeIf(v -> v == 0);
        return new EtatTva(mois, ca, col, ded, Math.max(0, soldeCreditAvant));
    }

    public long totalCollectee() {
        return collectee.values().stream().mapToLong(Long::longValue).sum();
    }

    public long totalDeductible() {
        return deductible.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Collectée − déductible − crédit antérieur : positif = TVA due, négatif = crédit à reporter. */
    public long net() {
        return totalCollectee() - totalDeductible() - creditAnterieur;
    }

    public long due() {
        return Math.max(0, net());
    }

    public long creditAReporter() {
        return Math.max(0, -net());
    }

    /** Les lignes de l'écriture de liquidation : solde les comptes de TVA du mois vers la TVA due ou le crédit. */
    public List<LigneEcriture> liquidation() {
        List<LigneEcriture> l = new ArrayList<>();
        String texte = "TVA " + mois;
        collectee.forEach((c, v) -> l.add(v > 0 ? LigneEcriture.debit(c, v, texte) : LigneEcriture.credit(c, -v, texte)));
        deductible.forEach((c, v) -> l.add(v > 0 ? LigneEcriture.credit(c, v, texte)
                : LigneEcriture.debit(c, -v, texte)));
        if (creditAnterieur > 0) {
            l.add(LigneEcriture.credit(COMPTE_CREDIT, creditAnterieur, "Crédit de TVA imputé"));
        }
        if (net() > 0) {
            l.add(LigneEcriture.credit(COMPTE_DUE, net(), "TVA due " + mois));
        } else if (net() < 0) {
            l.add(LigneEcriture.debit(COMPTE_CREDIT, -net(), "Crédit de TVA à reporter"));
        }
        return l;
    }
}
