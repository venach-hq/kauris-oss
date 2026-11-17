package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Le calcul d'un bulletin de paie, du salaire de base au net à payer et au coût pour l'employeur. Pur calcul : rien
 * n'est lu ni écrit ailleurs. Montants en francs CFA, sans décimales ; une cotisation s'arrondit au franc le plus
 * proche.
 */
public final class CalculPaie {

    /** Mois de paie : la base est payée sur 30 jours. */
    public static final int JOURS_DU_MOIS = 30;

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private CalculPaie() {
    }

    /** Ce que la ligne représente sur le bulletin. */
    public enum Nature {
        /** Salaire de base, prime, avantage en nature : entre dans le brut soumis. */
        GAIN,
        /** Indemnité non soumise : payée, sans cotisation ni impôt. */
        GAIN_NON_SOUMIS,
        /** Cotisation sociale : part du salarié et part de l'employeur. */
        COTISATION,
        /** Impôt retenu sur le salaire. */
        IMPOT,
        /** Taxe sur les salaires, payée par l'employeur seul. */
        TAXE_EMPLOYEUR,
        /** Avance, acompte, prêt : retenu sur le net. */
        RETENUE
    }

    /**
     * Une ligne du bulletin.
     *
     * @param organisme à qui l'argent est versé : CNSS, AMU, OTR, CNPS, CMU, DGI, FDFP ; vide pour un gain
     * @param base      montant sur lequel s'applique le taux (ou nombre de personnes pour la CMU)
     * @param gain      montant ajouté au brut (gains seulement)
     * @param salarie   montant retenu au salarié
     * @param employeur montant payé par l'employeur en plus du salaire
     */
    public record Ligne(String code, String libelle, Nature nature, String organisme, long base,
                        BigDecimal tauxSalarie, long gain, long salarie, BigDecimal tauxEmployeur, long employeur) {

        static Ligne gain(String code, String libelle, long montant, boolean soumis) {
            return new Ligne(code, libelle, soumis ? Nature.GAIN : Nature.GAIN_NON_SOUMIS, "", 0, null, montant, 0,
                    null, 0);
        }
    }

    /**
     * Ce qu'il faut pour calculer un mois.
     *
     * @param salaireBase  salaire de base mensuel du contrat, pour un mois complet
     * @param joursPayes   jours payés sur 30 (moins les absences non payées, entrée ou sortie en cours de mois)
     * @param elements     primes, avantages et retenues du contrat et du mois
     * @param expatrie     Côte d'Ivoire : l'impôt patronal d'un salarié expatrié est plus élevé
     */
    public record Entree(PaysPaie pays, long salaireBase, int joursPayes, List<Element> elements, Foyer foyer,
                         boolean expatrie) {

        public Entree {
            if (pays == null) {
                throw new ErreurMetier("La paie n'est pas disponible pour ce pays.");
            }
            if (salaireBase < 0 || salaireBase > Element.MONTANT_MAX) {
                throw new ErreurMetier("Salaire de base invalide.");
            }
            if (joursPayes < 0 || joursPayes > JOURS_DU_MOIS) {
                throw new ErreurMetier("Jours payés : entre 0 et " + JOURS_DU_MOIS + ".");
            }
            elements = List.copyOf(elements);
            foyer = foyer == null ? Foyer.seul() : foyer;
        }
    }

    /**
     * Le bulletin calculé.
     *
     * @param brut             tout ce que le salarié gagne, avantages en nature et indemnités non soumises compris
     * @param brutSoumis       la base des cotisations et de l'impôt
     * @param baseImpot        le revenu sur lequel s'applique le barème (mensuel ; annuel au Togo)
     * @param detailImpot      comment l'impôt a été obtenu, en une phrase pour le bulletin
     * @param netAPayer        ce que le salarié reçoit
     */
    public record Resultat(List<Ligne> lignes, long brut, long brutSoumis, long cotisationsSalarie, long impot,
                           long retenues, long avantagesNature, long netAPayer, long chargesEmployeur,
                           long baseImpot, String detailImpot) {

        public Resultat {
            lignes = List.copyOf(lignes);
        }

        /** Le salaire plus tout ce que l'employeur paie en plus. */
        public long coutEmployeur() {
            return brut + chargesEmployeur;
        }

        /** Le net avant les retenues (avances, prêts) : le « net imposable » que l'on voit sur un bulletin. */
        public long netAvantRetenues() {
            return netAPayer + retenues;
        }

        public long salarieDe(String organisme) {
            return lignes.stream().filter(l -> l.organisme().equals(organisme)).mapToLong(Ligne::salarie).sum();
        }

        public long employeurDe(String organisme) {
            return lignes.stream().filter(l -> l.organisme().equals(organisme)).mapToLong(Ligne::employeur).sum();
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

    public static Resultat calculer(Entree e, Parametres p) {
        List<Ligne> lignes = new ArrayList<>();
        long salaire = prorata(e.salaireBase(), e.joursPayes());
        lignes.add(Ligne.gain("BASE", e.joursPayes() >= JOURS_DU_MOIS ? "Salaire de base"
                : "Salaire de base (" + e.joursPayes() + " j / " + JOURS_DU_MOIS + ")", salaire, true));
        long soumis = salaire;
        long avantages = 0;
        long nonSoumis = 0;
        long retenues = 0;
        int rang = 1;
        for (Element x : e.elements()) {
            String code = "E" + rang++;
            switch (x.type()) {
                case GAIN_SOUMIS -> {
                    lignes.add(Ligne.gain(code, x.libelle(), x.montant(), true));
                    soumis += x.montant();
                }
                case AVANTAGE_NATURE -> {
                    lignes.add(Ligne.gain(code, x.libelle() + " (en nature)", x.montant(), true));
                    soumis += x.montant();
                    avantages += x.montant();
                }
                case GAIN_NON_SOUMIS -> {
                    lignes.add(Ligne.gain(code, x.libelle(), x.montant(), false));
                    nonSoumis += x.montant();
                }
                case RETENUE -> {
                    lignes.add(new Ligne(code, x.libelle(), Nature.RETENUE, "", 0, null, 0, x.montant(), null, 0));
                    retenues += x.montant();
                }
            }
        }
        Impot impot = switch (e.pays()) {
            case TOGO -> togo(lignes, soumis, e, p);
            case COTE_D_IVOIRE -> coteDIvoire(lignes, soumis, e, p);
        };
        long cotisations = lignes.stream().filter(l -> l.nature() == Nature.COTISATION).mapToLong(Ligne::salarie)
                .sum();
        long charges = lignes.stream().mapToLong(Ligne::employeur).sum();
        long brut = soumis + nonSoumis;
        long net = brut - avantages - cotisations - impot.montant() - retenues;
        if (net < 0) {
            throw new ErreurMetier("Les retenues dépassent ce que le salarié gagne ce mois-ci : le net serait négatif ("
                    + net + "). Réduisez une retenue ou reportez-la au mois suivant.");
        }
        return new Resultat(lignes, brut, soumis, cotisations, impot.montant(), retenues, avantages, net, charges,
                impot.base(), impot.detail());
    }

    private record Impot(long montant, long base, String detail) {
    }

    // ================================================================== Togo

    /**
     * Togo : CNSS (pension pour le salarié ; pension, prestations familiales et risques professionnels pour
     * l'employeur), AMU, puis IRPP. L'IRPP se calcule sur l'année : (brut soumis − CNSS − AMU du salarié) × 12, moins
     * l'abattement de 28 % (sur la part du revenu qui ne dépasse pas la limite), moins les personnes à charge ;
     * le revenu s'arrondit au millier inférieur, l'impôt annuel à la dizaine inférieure, et le mois en paie le
     * douzième.
     */
    private static Impot togo(List<Ligne> lignes, long soumis, Entree e, Parametres p) {
        long plafond = p.montant(Parametre.TG_CNSS_PLAFOND);
        long baseCnss = plafond > 0 ? Math.min(soumis, plafond) : soumis;
        BigDecimal tauxSal = p.valeur(Parametre.TG_CNSS_SALARIE);
        BigDecimal tauxPension = p.valeur(Parametre.TG_CNSS_PENSION_EMPLOYEUR);
        long cnssSal = taux(baseCnss, tauxSal);
        lignes.add(new Ligne("CNSS_PENSION", "CNSS : pension vieillesse, invalidité, décès", Nature.COTISATION,
                "CNSS", baseCnss, tauxSal, 0, cnssSal, tauxPension, taux(baseCnss, tauxPension)));
        BigDecimal tauxFamille = p.valeur(Parametre.TG_CNSS_FAMILLE_EMPLOYEUR);
        lignes.add(new Ligne("CNSS_FAMILLE", "CNSS : prestations familiales", Nature.COTISATION, "CNSS", baseCnss,
                null, 0, 0, tauxFamille, taux(baseCnss, tauxFamille)));
        BigDecimal tauxRisques = p.valeur(Parametre.TG_CNSS_RISQUES_EMPLOYEUR);
        lignes.add(new Ligne("CNSS_RISQUES", "CNSS : risques professionnels", Nature.COTISATION, "CNSS", baseCnss,
                null, 0, 0, tauxRisques, taux(baseCnss, tauxRisques)));
        BigDecimal amuSalTaux = p.valeur(Parametre.TG_AMU_SALARIE);
        BigDecimal amuPatTaux = p.valeur(Parametre.TG_AMU_EMPLOYEUR);
        long amuSal = taux(soumis, amuSalTaux);
        lignes.add(new Ligne("AMU", "Assurance maladie universelle (AMU)", Nature.COTISATION, "AMU", soumis,
                amuSalTaux, 0, amuSal, amuPatTaux, taux(soumis, amuPatTaux)));

        long annuel = Math.max(0, soumis - cnssSal - amuSal) * 12;
        long limite = p.montant(Parametre.TG_IRPP_ABATTEMENT_LIMITE);
        long abattement = taux(limite > 0 ? Math.min(annuel, limite) : annuel, p.valeur(Parametre.TG_IRPP_ABATTEMENT));
        int personnes = Math.min(e.foyer().personnesACharge(), p.nombre(Parametre.TG_IRPP_CHARGES_MAX));
        long famille = (long) personnes * p.montant(Parametre.TG_IRPP_CHARGE_FAMILLE) * 12;
        long imposable = Math.max(0, annuel - abattement - famille) / 1000 * 1000;
        long annuelImpot = Bareme.IRPP_TOGO.impot(imposable) / 10 * 10;
        long mois = annuelImpot / 12;
        lignes.add(new Ligne("IRPP", "IRPP retenu à la source", Nature.IMPOT, "OTR", imposable, null, 0, mois, null,
                0));
        String detail = "Revenu annuel " + annuel + " − abattement " + abattement + " − " + personnes
                + " personne(s) à charge " + famille + " = " + imposable + " imposable ; IRPP annuel " + annuelImpot
                + ", soit " + mois + " par mois.";
        return new Impot(mois, imposable, detail);
    }

    // ================================================================== Côte d'Ivoire

    /**
     * Côte d'Ivoire : CNPS (retraite, plafonnée ; prestations familiales, maternité et accidents du travail, à la
     * charge de l'employeur, sur un plafond plus bas), CMU par personne couverte, ITS sur le brut soumis réduit par
     * les charges de famille (RICF), puis les taxes de l'employeur sur les salaires.
     */
    private static Impot coteDIvoire(List<Ligne> lignes, long soumis, Entree e, Parametres p) {
        long baseRetraite = Math.min(soumis, p.montant(Parametre.CI_RETRAITE_PLAFOND));
        BigDecimal retSal = p.valeur(Parametre.CI_RETRAITE_SALARIE);
        BigDecimal retPat = p.valeur(Parametre.CI_RETRAITE_EMPLOYEUR);
        lignes.add(new Ligne("CNPS_RETRAITE", "CNPS : retraite", Nature.COTISATION, "CNPS", baseRetraite, retSal, 0,
                taux(baseRetraite, retSal), retPat, taux(baseRetraite, retPat)));
        long basePlafonnee = Math.min(soumis, p.montant(Parametre.CI_FAMILLE_PLAFOND));
        for (Parametre x : List.of(Parametre.CI_FAMILLE_EMPLOYEUR, Parametre.CI_MATERNITE_EMPLOYEUR,
                Parametre.CI_ACCIDENT_EMPLOYEUR)) {
            BigDecimal t = p.valeur(x);
            String[] codeEtLibelle = switch (x) {
                case CI_FAMILLE_EMPLOYEUR -> new String[]{"CNPS_FAMILLE", "CNPS : prestations familiales"};
                case CI_MATERNITE_EMPLOYEUR -> new String[]{"CNPS_MATERNITE", "CNPS : assurance maternité"};
                default -> new String[]{"CNPS_ACCIDENT", "CNPS : accidents du travail"};
            };
            lignes.add(new Ligne(codeEtLibelle[0], codeEtLibelle[1], Nature.COTISATION, "CNPS", basePlafonnee, null,
                    0, 0, t, taux(basePlafonnee, t)));
        }
        int personnes = Math.min(e.foyer().personnesCmu(), p.nombre(Parametre.CI_CMU_PERSONNES_MAX));
        long cmu = (long) personnes * p.montant(Parametre.CI_CMU_PAR_PERSONNE);
        long cmuPat = taux(cmu, p.valeur(Parametre.CI_CMU_PART_EMPLOYEUR));
        lignes.add(new Ligne("CMU", "Couverture maladie universelle (CMU), " + personnes + " personne(s)",
                Nature.COTISATION, "CMU", personnes, null, 0, cmu - cmuPat, null, cmuPat));

        long brutImpot = Bareme.ITS_COTE_D_IVOIRE.impot(soumis);
        BigDecimal parts = e.foyer().parts();
        long demiParts = parts.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(2)).longValue();
        long ricf = demiParts * p.montant(Parametre.CI_ITS_RICF_DEMI_PART);
        long its = Math.max(0, brutImpot - ricf);
        lignes.add(new Ligne("ITS", "Impôt sur les traitements et salaires (ITS)", Nature.IMPOT, "DGI", soumis, null,
                0, its, null, 0));

        Parametre patronal = e.expatrie() ? Parametre.CI_IMPOT_PATRONAL_EXPATRIE : Parametre.CI_IMPOT_PATRONAL_LOCAL;
        lignes.add(taxe("IMPOT_PATRONAL", "Impôt patronal sur les salaires" + (e.expatrie() ? " (expatrié)" : ""),
                "DGI", soumis, p.valeur(patronal)));
        lignes.add(taxe("TA", "Taxe d'apprentissage", "FDFP", soumis, p.valeur(Parametre.CI_TAXE_APPRENTISSAGE)));
        lignes.add(taxe("FPC", "Formation professionnelle continue", "FDFP", soumis, p.valeur(Parametre.CI_FPC)));
        lignes.add(taxe("FPC_COMPLEMENTAIRE", "Formation professionnelle continue (taxe additionnelle)", "FDFP",
                soumis, p.valeur(Parametre.CI_FPC_COMPLEMENTAIRE)));
        String detail = "Impôt brut " + brutImpot + " sur " + soumis + " − réduction pour charges de famille ("
                + parts.stripTrailingZeros().toPlainString() + " part(s)) " + ricf + " = " + its + ".";
        return new Impot(its, soumis, detail);
    }

    private static Ligne taxe(String code, String libelle, String organisme, long base, BigDecimal t) {
        return new Ligne(code, libelle, Nature.TAXE_EMPLOYEUR, organisme, base, null, 0, 0, t, taux(base, t));
    }

    /** {@code base × taux %}, au franc le plus proche. */
    static long taux(long base, BigDecimal pourcent) {
        if (pourcent == null || pourcent.signum() == 0 || base == 0) {
            return 0;
        }
        return BigDecimal.valueOf(base).multiply(pourcent).divide(CENT, 0, RoundingMode.HALF_UP).longValue();
    }
}
