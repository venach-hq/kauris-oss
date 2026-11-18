package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.paie.domain.ReglesPaie.Cotisation;
import com.venach.kauris.paie.domain.ReglesPaie.Deduction;
import com.venach.kauris.paie.domain.ReglesPaie.Famille;
import com.venach.kauris.paie.domain.ReglesPaie.Impot;
import com.venach.kauris.paie.domain.ReglesPaie.TaxeEmployeur;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Le calcul d'un bulletin de paie, entièrement piloté par les règles du pays ({@link ReglesPaie.Contenu}) : aucun
 * taux, plafond ou barème n'est écrit ici. Pur calcul, sans base de données. Montants en francs, sans décimales ;
 * chaque ligne s'arrondit au franc le plus proche, l'impôt selon ses propres règles d'arrondi.
 * <ol>
 *   <li>Salaire de base au prorata des jours payés (sur 30), heures supplémentaires au taux horaire majoré.</li>
 *   <li>Éléments : primes soumises, primes exceptionnelles, avantages en nature (soumis, jamais payés), frais
 *       d'emploi (non soumis, sauf dirigeant ou gérant), indemnités non soumises, retenues.</li>
 *   <li>Cotisations sur le brut soumis, primes exceptionnelles comprises : plafond, assiette minimale, ou montant
 *       fixe par personne couverte ; selon le personnel (local, expatrié, apprenti).</li>
 *   <li>Impôt : revenu du mois (ou de l'année), moins les cotisations déductibles (retraite plafonnée), l'abattement
 *       et les personnes à charge ; arrondi, barème, réduction par demi-part, arrondi ; primes exceptionnelles par
 *       différence ; régimes apprenti et gérant.</li>
 *   <li>Taxes de l'employeur sur les salaires.</li>
 *   <li>Retenues, puis avances et prêts plafonnés au net disponible (le reste est reporté).</li>
 * </ol>
 */
public final class CalculPaie {

    /** Mois de paie : la base est payée sur 30 jours. */
    public static final int JOURS_DU_MOIS = 30;

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private CalculPaie() {
    }

    /** Ce que la ligne représente sur le bulletin. */
    public enum Nature {
        /** Salaire, heures supplémentaires, prime, avantage en nature : entre dans le brut soumis. */
        GAIN,
        /** Indemnité non soumise, frais d'emploi : payée, sans cotisation ni impôt. */
        GAIN_NON_SOUMIS,
        /** Cotisation sociale : part du salarié et part de l'employeur. */
        COTISATION,
        /** Impôt retenu sur le salaire. */
        IMPOT,
        /** Taxe sur les salaires, payée par l'employeur seul. */
        TAXE_EMPLOYEUR,
        /** Retenue sur le net, remboursement d'avance ou de prêt. */
        RETENUE
    }

    /**
     * Une ligne du bulletin.
     *
     * @param organisme     à qui l'argent est versé (CNSS, AMU, OTR, CNPS, CMU, DGI, FDFP…) ; vide pour un gain
     * @param base          montant sur lequel s'applique le taux (nombre de personnes pour un montant par personne,
     *                      nombre d'heures × 100 pour des heures supplémentaires)
     * @param gain          montant ajouté au brut
     * @param salarie       montant retenu au salarié
     * @param employeur     montant payé par l'employeur en plus du salaire
     * @param compte        compte crédité dans les écritures de paie (organisme, impôt, avances)
     * @param compteCharge  compte de charge débité (salaires, indemnités, charges sociales, taxes) ; vide pour un
     *                      avantage en nature, qui ne se comptabilise pas dans la paie
     */
    public record Ligne(String code, String libelle, Nature nature, String organisme, long base,
                        BigDecimal tauxSalarie, long gain, long salarie, BigDecimal tauxEmployeur, long employeur,
                        String compte, String compteCharge) {

        public Ligne {
            organisme = organisme == null ? "" : organisme;
            compte = compte == null ? "" : compte;
            compteCharge = compteCharge == null ? "" : compteCharge;
        }

        static Ligne gain(String code, String libelle, long montant, boolean soumis, String compteCharge) {
            return new Ligne(code, libelle, soumis ? Nature.GAIN : Nature.GAIN_NON_SOUMIS, "", 0, null, montant, 0,
                    null, 0, "", compteCharge);
        }
    }

    /** Une avance ou un prêt à rembourser ce mois-ci (montant voulu, avant le plafond du net). */
    public record AvanceDue(String code, String libelle, long montant) {
    }

    /**
     * Ce qu'il faut pour calculer un mois.
     *
     * @param salaireBase  salaire de base mensuel du contrat, pour un mois complet
     * @param joursPayes   jours payés sur 30 (moins les absences non payées, entrée ou sortie en cours de mois)
     * @param heures       heures supplémentaires du mois
     * @param elements     primes, avantages et retenues du contrat et du mois
     * @param avances      avances et prêts dont une échéance tombe ce mois-ci
     */
    public record Entree(ReglesPaie.Contenu regles, long salaireBase, int joursPayes, List<Variables.Heures> heures,
                         List<Element> elements, Foyer foyer, boolean expatrie, RegimeFiscal regime,
                         List<AvanceDue> avances) {

        public Entree {
            if (regles == null) {
                throw new ErreurMetier("Aucune règle de paie pour ce mois : activez ou saisissez les règles du pays.");
            }
            if (salaireBase < 0 || salaireBase > Element.MONTANT_MAX) {
                throw new ErreurMetier("Salaire de base invalide.");
            }
            if (joursPayes < 0 || joursPayes > JOURS_DU_MOIS) {
                throw new ErreurMetier("Jours payés : entre 0 et " + JOURS_DU_MOIS + ".");
            }
            heures = List.copyOf(heures == null ? List.of() : heures);
            elements = List.copyOf(elements == null ? List.of() : elements);
            avances = List.copyOf(avances == null ? List.of() : avances);
            foyer = foyer == null ? Foyer.seul() : foyer;
            regime = regime == null ? RegimeFiscal.NORMAL : regime;
        }
    }

    /**
     * Le bulletin calculé.
     *
     * @param brut             tout ce que le salarié gagne, avantages en nature et indemnités non soumises compris
     * @param brutSoumis       la base des cotisations et de l'impôt (primes exceptionnelles comprises)
     * @param baseImpot        revenu imposable arrondi de la période de l'impôt (mois ou année)
     * @param detailImpot      comment l'impôt a été obtenu, en une phrase pour le bulletin
     * @param impotNom         IRPP, ITS…
     * @param compteNet        compte crédité du net à payer dans les écritures de paie
     */
    public record Resultat(List<Ligne> lignes, long brut, long brutSoumis, long cotisationsSalarie, long impot,
                           long retenues, long avantagesNature, long netAPayer, long chargesEmployeur,
                           long baseImpot, String detailImpot, String impotNom, String compteNet) {

        public Resultat {
            lignes = List.copyOf(lignes);
            impotNom = impotNom == null ? "" : impotNom;
            compteNet = compteNet == null ? "" : compteNet;
        }

        /** Le salaire plus tout ce que l'employeur paie en plus. */
        public long coutEmployeur() {
            return brut + chargesEmployeur;
        }

        public long salarieDe(String organisme) {
            return lignes.stream().filter(l -> l.organisme().equals(organisme)).mapToLong(Ligne::salarie).sum();
        }

        public long employeurDe(String organisme) {
            return lignes.stream().filter(l -> l.organisme().equals(organisme)).mapToLong(Ligne::employeur).sum();
        }

        /** Les organismes des cotisations, dans l'ordre du bulletin. */
        public List<String> organismesSociaux() {
            Set<String> o = new LinkedHashSet<>();
            lignes.stream().filter(l -> l.nature() == Nature.COTISATION).forEach(l -> o.add(l.organisme()));
            return List.copyOf(o);
        }

        /** Les organismes des taxes de l'employeur. */
        public List<String> organismesTaxes() {
            Set<String> o = new LinkedHashSet<>();
            lignes.stream().filter(l -> l.nature() == Nature.TAXE_EMPLOYEUR).forEach(l -> o.add(l.organisme()));
            return List.copyOf(o);
        }

        /** Ce qui a été retenu pour rembourser cette avance. */
        public long rembourse(String codeAvance) {
            return lignes.stream().filter(l -> l.code().equals(codeAvance)).mapToLong(Ligne::salarie).sum();
        }
    }

    /** Le salaire du mois pour {@code jours} jours payés sur 30. */
    public static long prorata(long salaireBase, int jours) {
        if (jours >= JOURS_DU_MOIS) {
            return salaireBase;
        }
        return BigDecimal.valueOf(salaireBase).multiply(BigDecimal.valueOf(jours))
                .divide(BigDecimal.valueOf(JOURS_DU_MOIS), 0, RoundingMode.HALF_UP).longValue();
    }

    public static Resultat calculer(Entree e) {
        ReglesPaie.Contenu r = e.regles();
        ReglesPaie.Comptes comptes = r.comptes();
        List<Ligne> lignes = new ArrayList<>();
        boolean dirigeant = e.regime() == RegimeFiscal.DIRIGEANT || e.regime() == RegimeFiscal.GERANT;
        boolean apprenti = e.regime() == RegimeFiscal.APPRENTI;

        // 1. Salaire et heures supplémentaires
        long salaire = prorata(e.salaireBase(), e.joursPayes());
        lignes.add(Ligne.gain("BASE", e.joursPayes() >= JOURS_DU_MOIS ? "Salaire de base"
                : "Salaire de base (" + e.joursPayes() + " j / " + JOURS_DU_MOIS + ")", salaire, true,
                comptes.salaires()));
        long regulier = salaire;
        BigDecimal horaire = BigDecimal.valueOf(e.salaireBase()).divide(r.heures().heuresDuMois(), 6,
                RoundingMode.HALF_UP);
        int rang = 1;
        for (Variables.Heures h : e.heures()) {
            long m = horaire.multiply(CENT.add(h.majoration())).divide(CENT, 6, RoundingMode.HALF_UP)
                    .multiply(h.nombre()).setScale(0, RoundingMode.HALF_UP).longValue();
            lignes.add(new Ligne("HS" + rang++, "Heures supplémentaires : " + h.libelle() + " ("
                    + h.nombre().toPlainString().replace('.', ',') + " h à +" + h.majoration().toPlainString()
                    .replace('.', ',') + " %)", Nature.GAIN, "", 0, null, m, 0, null, 0, "", comptes.salaires()));
            regulier += m;
        }

        // 2. Éléments
        long exceptionnelles = 0;
        long avantages = 0;
        long nonSoumis = 0;
        long retenues = 0;
        rang = 1;
        for (Element x : e.elements()) {
            String code = "E" + rang++;
            switch (x.type()) {
                case GAIN_SOUMIS -> {
                    lignes.add(Ligne.gain(code, x.libelle(), x.montant(), true, comptes.salaires()));
                    regulier += x.montant();
                }
                case PRIME_EXCEPTIONNELLE -> {
                    lignes.add(Ligne.gain(code, x.libelle() + " (prime exceptionnelle)", x.montant(), true,
                            comptes.salaires()));
                    exceptionnelles += x.montant();
                }
                case AVANTAGE_NATURE -> {
                    lignes.add(Ligne.gain(code, x.libelle() + " (en nature)", x.montant(), true, ""));
                    regulier += x.montant();
                    avantages += x.montant();
                }
                case FRAIS_EMPLOI -> {
                    if (dirigeant) {
                        lignes.add(Ligne.gain(code, x.libelle() + " (frais d'emploi, imposables pour un dirigeant)",
                                x.montant(), true, comptes.indemnites()));
                        regulier += x.montant();
                    } else {
                        lignes.add(Ligne.gain(code, x.libelle() + " (frais d'emploi, non soumis)", x.montant(), false,
                                comptes.indemnites()));
                        nonSoumis += x.montant();
                    }
                }
                case GAIN_NON_SOUMIS -> {
                    lignes.add(Ligne.gain(code, x.libelle(), x.montant(), false, comptes.indemnites()));
                    nonSoumis += x.montant();
                }
                case RETENUE -> {
                    lignes.add(new Ligne(code, x.libelle(), Nature.RETENUE, "", 0, null, 0, x.montant(), null, 0,
                            comptes.avances(), ""));
                    retenues += x.montant();
                }
            }
        }
        long soumis = regulier + exceptionnelles;

        // 3. Cotisations
        List<Cotisation> applicables = r.cotisations().stream()
                .filter(c -> c.application().concerne(e.expatrie()) && (!apprenti || c.apprentis())).toList();
        long cotisationsSalarie = 0;
        for (Cotisation c : applicables) {
            long base;
            long sal;
            long emp;
            if (c.fixe()) {
                int personnes = c.personnesMax() == 0 ? 1 : Math.min(e.foyer().personnesCouvertes(), c.personnesMax());
                base = personnes;
                sal = c.fixeSalarie() * personnes;
                emp = c.fixeEmployeur() * personnes;
            } else {
                base = assiette(c, soumis);
                sal = taux(base, c.tauxSalarie());
                emp = taux(base, c.tauxEmployeur());
            }
            if (sal == 0 && emp == 0) {
                continue;
            }
            String libelle = c.fixe() ? c.libelle() + ", " + base + " personne(s)" : c.libelle();
            lignes.add(new Ligne(c.code(), libelle, Nature.COTISATION, c.organisme(), base,
                    c.fixe() ? null : c.tauxSalarie(), 0, sal, c.fixe() ? null : c.tauxEmployeur(), emp, c.compte(),
                    comptes.chargesSociales()));
            cotisationsSalarie += sal;
        }

        // 4. Impôt
        Impot i = r.impot();
        Calcule impot = impot(i, applicables, e, regulier, exceptionnelles, soumis);
        lignes.add(new Ligne(i.nom(), i.nom() + (impot.montant == 0 && apprenti && i.apprentisExoneres()
                ? " (apprenti, exonéré)" : ""), Nature.IMPOT, i.organisme(), impot.base, null, 0, impot.montant, null,
                0, i.compte(), ""));

        // 5. Taxes de l'employeur
        for (TaxeEmployeur t : r.taxes()) {
            if (!t.application().concerne(e.expatrie()) || (apprenti && !t.apprentis())) {
                continue;
            }
            long base = t.plafond() > 0 ? Math.min(soumis, t.plafond()) : soumis;
            long montant = taux(base, t.taux());
            if (montant > 0) {
                lignes.add(new Ligne(t.code(), t.libelle(), Nature.TAXE_EMPLOYEUR, t.organisme(), base, null, 0, 0,
                        t.taux(), montant, t.compte(), t.compteCharge()));
            }
        }

        // 6. Net avant avances ; les retenues ne peuvent pas rendre le net négatif
        long brut = soumis + nonSoumis;
        long disponible = brut - avantages - cotisationsSalarie - impot.montant - retenues;
        if (disponible < 0) {
            throw new ErreurMetier("Les retenues dépassent ce que le salarié gagne ce mois-ci : le net serait négatif ("
                    + disponible + "). Réduisez une retenue ou reportez-la au mois suivant.");
        }
        // 7. Avances et prêts, plafonnés au net disponible
        long avancesRetenues = 0;
        for (AvanceDue a : e.avances()) {
            long retenu = Math.min(Math.max(0, a.montant()), disponible - avancesRetenues);
            if (retenu <= 0 && a.montant() <= 0) {
                continue;
            }
            lignes.add(new Ligne(a.code(), "Remboursement : " + a.libelle() + (retenu < a.montant()
                    ? " (" + (a.montant() - retenu) + " reportés)" : ""), Nature.RETENUE, "", 0, null, 0, retenu,
                    null, 0, comptes.avances(), ""));
            avancesRetenues += retenu;
        }
        long charges = lignes.stream().mapToLong(Ligne::employeur).sum();
        long net = disponible - avancesRetenues;
        return new Resultat(lignes, brut, soumis, cotisationsSalarie, impot.montant, retenues + avancesRetenues,
                avantages, net, charges, impot.base, impot.detail, i.nom(), comptes.remunerationsDues());
    }

    /** L'assiette d'une cotisation à taux : brut soumis, plafonné, au moins l'assiette minimale (s'il y a un salaire). */
    static long assiette(Cotisation c, long soumis) {
        if (soumis <= 0) {
            return 0;
        }
        long base = c.plafond() > 0 ? Math.min(soumis, c.plafond()) : soumis;
        return Math.max(base, c.assietteMinimale());
    }

    // ================================================================== impôt

    private record Calcule(long montant, long base, String detail) {
    }

    private record Periode(long abattement, long famille, long imposable, long impot, long reduction) {
    }

    private static Calcule impot(Impot i, List<Cotisation> applicables, Entree e, long regulier, long exceptionnelles,
                                 long soumis) {
        RegimeFiscal regime = e.regime();
        if (regime == RegimeFiscal.APPRENTI && i.apprentisExoneres()) {
            return new Calcule(0, 0, "Apprenti : exonéré d'impôt.");
        }
        int mois = i.periodicite().mois();
        boolean gerant = regime == RegimeFiscal.GERANT && i.gerantAbattement().signum() > 0;
        long dedRegulier = gerant || !i.deduireCotisations() ? 0 : deductible(i, applicables, e, regulier);
        long dedTotal = gerant || !i.deduireCotisations() ? 0 : deductible(i, applicables, e, soumis);
        long r;
        long avecPrimes;
        if (mois == 1) {
            r = soumis - dedTotal;
            avecPrimes = r;
        } else {
            r = (regulier - dedRegulier) * mois;
            avecPrimes = r + exceptionnelles - (dedTotal - dedRegulier);
        }
        Periode sans = periode(i, e.foyer(), Math.max(0, r), gerant, mois);
        long montant;
        String detail;
        if (mois == 1) {
            montant = sans.impot;
            detail = "Revenu du mois " + r + (sans.abattement > 0 ? " − abattement " + sans.abattement : "")
                    + (sans.famille > 0 ? " − personnes à charge " + sans.famille : "") + " = " + sans.imposable
                    + " imposable ; " + i.nom() + " " + (sans.impot + sans.reduction)
                    + (sans.reduction > 0 ? " − réduction pour charges de famille " + sans.reduction : "") + " = "
                    + montant + ".";
        } else {
            long annuel = sans.impot;
            long duMois = i.arrondiMensuel().appliquer(BigDecimal.valueOf(annuel).divide(BigDecimal.valueOf(mois), 6,
                    RoundingMode.HALF_UP), 1);
            long surPrimes = 0;
            if (exceptionnelles > 0) {
                if (i.primesDifferentielles()) {
                    surPrimes = Math.max(0, periode(i, e.foyer(), Math.max(0, avecPrimes), gerant, mois).impot
                            - annuel);
                } else {
                    long avec = periode(i, e.foyer(), Math.max(0, avecPrimes), gerant, mois).impot;
                    surPrimes = Math.max(0, i.arrondiMensuel().appliquer(BigDecimal.valueOf(avec)
                            .divide(BigDecimal.valueOf(mois), 6, RoundingMode.HALF_UP), 1) - duMois);
                }
            }
            montant = duMois + surPrimes;
            detail = (gerant ? "Gérant : revenu annuel " : "Revenu annuel ") + r
                    + (sans.abattement > 0 ? " − " + (gerant ? "frais forfaitaires " : "abattement ") + sans.abattement
                    : "") + (sans.famille > 0 ? " − personnes à charge " + sans.famille : "") + " = " + sans.imposable
                    + " imposable ; " + i.nom() + " annuel " + annuel + (sans.reduction > 0 ? " (après réduction "
                    + sans.reduction + ")" : "") + ", soit " + duMois + " par mois"
                    + (surPrimes > 0 ? " ; " + surPrimes + " sur les primes exceptionnelles" : "") + ".";
        }
        return new Calcule(montant, sans.imposable, detail);
    }

    private static Periode periode(Impot i, Foyer foyer, long revenu, boolean gerant, int mois) {
        long abattement;
        long famille = 0;
        if (gerant) {
            long limite = i.gerantAbattementLimite();
            abattement = taux(limite > 0 ? Math.min(revenu, limite) : revenu, i.gerantAbattement());
        } else {
            long limite = i.abattementLimite();
            abattement = taux(limite > 0 ? Math.min(revenu, limite) : revenu, i.abattement());
            if (i.famille() == Famille.DEDUCTION_PAR_PERSONNE) {
                int personnes = Math.min(foyer.personnesACharge(), i.personnesMax());
                famille = (long) personnes * i.deductionParPersonne() * mois;
            }
        }
        abattement = Math.min(abattement, revenu);
        long imposable = ReglesPaie.Arrondi.INFERIEUR.appliquer(Math.max(0, revenu - abattement - famille),
                i.arrondiRevenu());
        long brut = i.bareme(imposable);
        long reduction = 0;
        if (!gerant && i.famille() == Famille.REDUCTION_PAR_DEMI_PART) {
            long demiParts = i.parts().de(foyer).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(2))
                    .setScale(0, RoundingMode.FLOOR).longValue();
            reduction = Math.min(brut, Math.max(0, demiParts) * i.reductionParDemiPart() * mois);
        }
        long impot = ReglesPaie.Arrondi.INFERIEUR.appliquer(brut - reduction, i.arrondiImpot());
        return new Periode(abattement, famille, imposable, impot, reduction);
    }

    /** Les cotisations du salarié déductibles du revenu imposable, la retraite dans la limite fixée. */
    private static long deductible(Impot i, List<Cotisation> applicables, Entree e, long assiette) {
        long totales = 0;
        long retraite = 0;
        for (Cotisation c : applicables) {
            long part = c.fixe()
                    ? c.fixeSalarie() * (c.personnesMax() == 0 ? 1 : Math.min(e.foyer().personnesCouvertes(),
                    c.personnesMax()))
                    : taux(assiette(c, assiette), c.tauxSalarie());
            if (c.deduction() == Deduction.TOTALE) {
                totales += part;
            } else if (c.deduction() == Deduction.RETRAITE_PLAFONNEE) {
                retraite += part;
            }
        }
        if (i.retraiteDeductibleMax().signum() > 0) {
            retraite = Math.min(retraite, taux(Math.max(0, assiette), i.retraiteDeductibleMax()));
        }
        return totales + retraite;
    }

    /** {@code base × taux %}, au franc le plus proche. */
    static long taux(long base, BigDecimal pourcent) {
        if (pourcent == null || pourcent.signum() == 0 || base == 0) {
            return 0;
        }
        return BigDecimal.valueOf(base).multiply(pourcent).divide(CENT, 0, RoundingMode.HALF_UP).longValue();
    }
}
