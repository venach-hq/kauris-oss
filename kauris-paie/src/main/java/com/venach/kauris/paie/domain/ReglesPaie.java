package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Pays;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les règles de paie d'une entreprise pour un pays, à partir d'une date : cotisations, taxes de l'employeur, impôt
 * sur les salaires (barème, abattement, charges de famille, parts, arrondis, régimes), heures supplémentaires et
 * comptes comptables. <strong>Rien n'est figé dans le code</strong> : Kauris propose des modèles recommandés
 * ({@link ModelesRecommandes}), l'entreprise les active puis corrige ce qu'elle veut ; une loi nouvelle se saisit en
 * créant une nouvelle version à partir de sa date. Un pays sans modèle se paramètre de la même façon.
 * <p>
 * Taux en pour cent (« 12.5 » = 12,5 %), montants en francs entiers. Les montants « par mois » le restent même quand
 * l'impôt se calcule sur l'année ; les limites du barème et de l'abattement sont exprimées dans la période de
 * l'impôt (annuelle ou mensuelle).
 *
 * @param modele code du modèle recommandé d'origine (pour comparer et afficher les recommandations) ; vide sinon
 */
public record ReglesPaie(UUID id, UUID tenantId, Pays pays, LocalDate depuis, String nom, String modele,
                         Contenu contenu, String notes, Instant modifieLe, String modifiePar) {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    /** Ce qui se calcule : tout ce qui est saisi dans l'éditeur des règles. */
    public record Contenu(List<Cotisation> cotisations, List<TaxeEmployeur> taxes, Impot impot,
                          HeuresSupplementaires heures, Comptes comptes) {

        public Contenu {
            cotisations = List.copyOf(cotisations == null ? List.of() : cotisations);
            taxes = List.copyOf(taxes == null ? List.of() : taxes);
            if (impot == null) {
                throw new ErreurMetier("Les règles de l'impôt sont incomplètes.");
            }
            heures = heures == null ? HeuresSupplementaires.AUCUNE : heures;
            comptes = comptes == null ? Comptes.SYSCOHADA : comptes;
            if (cotisations.size() > 30 || taxes.size() > 20) {
                throw new ErreurMetier("Trop de lignes : 30 cotisations et 20 taxes au plus.");
            }
            Set<String> codes = new HashSet<>();
            for (Cotisation c : cotisations) {
                if (!codes.add(c.code())) {
                    throw new ErreurMetier("Deux cotisations ont le même code : " + c.code() + ".");
                }
            }
            for (TaxeEmployeur t : taxes) {
                if (!codes.add(t.code())) {
                    throw new ErreurMetier("Deux lignes ont le même code : " + t.code() + ".");
                }
            }
        }
    }

    /** À qui s'applique une cotisation ou une taxe. */
    public enum Application {
        TOUS("Tous les salariés"),
        LOCAUX("Personnel local seulement"),
        EXPATRIES("Expatriés seulement");

        private final String libelle;

        Application(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }

        public boolean concerne(boolean expatrie) {
            return this == TOUS || (this == EXPATRIES) == expatrie;
        }
    }

    /** Comment la part du salarié se retranche du revenu imposable. */
    public enum Deduction {
        TOTALE("Déductible de l'impôt"),
        RETRAITE_PLAFONNEE("Retraite : déductible dans la limite fixée pour l'impôt"),
        AUCUNE("Non déductible");

        private final String libelle;

        Deduction(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /**
     * Une cotisation sociale : un taux sur le brut soumis (plafonné, avec une assiette minimale), ou un montant fixe
     * par personne couverte (CMU).
     *
     * @param plafond           plafond mensuel de l'assiette ; 0 = aucun
     * @param assietteMinimale  assiette mensuelle minimale (souvent le SMIG) ; 0 = aucune
     * @param fixeSalarie       montant fixe mensuel du salarié par personne couverte (taux ignorés si un montant fixe
     *                          est saisi)
     * @param personnesMax      personnes couvertes au plus pour un montant fixe ; 0 = le salarié seul
     * @param apprentis         vrai si la cotisation s'applique aussi aux apprentis
     * @param compte            compte de tiers crédité (431…) dans les écritures de paie
     */
    public record Cotisation(String code, String libelle, String organisme, BigDecimal tauxSalarie,
                             BigDecimal tauxEmployeur, long plafond, long assietteMinimale, long fixeSalarie,
                             long fixeEmployeur, int personnesMax, Deduction deduction, Application application,
                             boolean apprentis, String compte, String note) {

        public Cotisation {
            code = ReglesPaie.code(code);
            libelle = Textes.obligatoire(libelle, "Libellé de la cotisation", 80);
            organisme = Textes.obligatoire(organisme, libelle + " : organisme", 10).toUpperCase();
            tauxSalarie = ReglesPaie.taux(tauxSalarie, libelle);
            tauxEmployeur = ReglesPaie.taux(tauxEmployeur, libelle);
            ReglesPaie.montant(plafond, libelle + " : plafond");
            ReglesPaie.montant(assietteMinimale, libelle + " : assiette minimale");
            ReglesPaie.montant(fixeSalarie, libelle + " : montant fixe");
            ReglesPaie.montant(fixeEmployeur, libelle + " : montant fixe");
            if (personnesMax < 0 || personnesMax > 30) {
                throw new ErreurMetier(libelle + " : personnes couvertes entre 0 et 30.");
            }
            if (plafond > 0 && assietteMinimale > plafond) {
                throw new ErreurMetier(libelle + " : l'assiette minimale dépasse le plafond.");
            }
            deduction = deduction == null ? Deduction.TOTALE : deduction;
            application = application == null ? Application.TOUS : application;
            compte = ReglesPaie.compte(compte, libelle);
            note = Textes.facultatif(note, libelle + " : note", 300);
        }

        /** Vrai pour une cotisation par personne (montant fixe) plutôt qu'un taux. */
        public boolean fixe() {
            return fixeSalarie > 0 || fixeEmployeur > 0;
        }
    }

    /** Une taxe sur les salaires payée par l'employeur seul (impôt patronal, apprentissage, formation…). */
    public record TaxeEmployeur(String code, String libelle, String organisme, BigDecimal taux, long plafond,
                                Application application, boolean apprentis, String compteCharge, String compte,
                                String note) {

        public TaxeEmployeur {
            code = ReglesPaie.code(code);
            libelle = Textes.obligatoire(libelle, "Libellé de la taxe", 80);
            organisme = Textes.obligatoire(organisme, libelle + " : organisme", 10).toUpperCase();
            taux = ReglesPaie.taux(taux, libelle);
            ReglesPaie.montant(plafond, libelle + " : plafond");
            application = application == null ? Application.TOUS : application;
            compteCharge = ReglesPaie.compte(compteCharge, libelle);
            compte = ReglesPaie.compte(compte, libelle);
            note = Textes.facultatif(note, libelle + " : note", 300);
        }
    }

    /** L'impôt s'applique sur le revenu du mois, ou sur le revenu annualisé puis divisé par 12. */
    public enum Periodicite {
        MENSUELLE("Barème mensuel"),
        ANNUELLE("Barème annuel (revenu du mois × 12, impôt ÷ 12)");

        private final String libelle;

        Periodicite(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }

        public int mois() {
            return this == ANNUELLE ? 12 : 1;
        }
    }

    /** Comment la famille réduit l'impôt. */
    public enum Famille {
        AUCUNE("Aucune réduction"),
        DEDUCTION_PAR_PERSONNE("Déduction du revenu par personne à charge"),
        REDUCTION_PAR_DEMI_PART("Réduction de l'impôt par demi-part");

        private final String libelle;

        Famille(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Arrondi d'un montant à un pas (1, 10, 1 000). */
    public enum Arrondi {
        INFERIEUR("Au pas inférieur"),
        PLUS_PROCHE("Au plus proche");

        private final String libelle;

        Arrondi(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }

        public long appliquer(BigDecimal valeur, long pas) {
            long p = Math.max(1, pas);
            RoundingMode mode = this == INFERIEUR ? RoundingMode.FLOOR : RoundingMode.HALF_UP;
            return valeur.divide(BigDecimal.valueOf(p), 0, mode).longValue() * p;
        }

        public long appliquer(long valeur, long pas) {
            return appliquer(BigDecimal.valueOf(valeur), pas);
        }
    }

    /** Une tranche du barème : jusqu'à {@code jusqua} inclus (0 = sans limite), le taux est {@code taux} %. */
    public record Tranche(long jusqua, BigDecimal taux) {

        public Tranche {
            if (jusqua < 0) {
                throw new ErreurMetier("Barème : une limite de tranche est positive.");
            }
            taux = ReglesPaie.taux(taux, "Barème");
        }

        public long limite() {
            return jusqua == 0 ? Long.MAX_VALUE : jusqua;
        }
    }

    /**
     * Le nombre de parts selon la famille (réduction par demi-part).
     *
     * @param seulAvecEnfants base d'un célibataire, divorcé ou veuf avec enfants, avant les enfants
     * @param veufAvecEnfants base d'un veuf ou d'une veuve avec enfants, avant les enfants
     */
    public record Parts(BigDecimal celibataire, BigDecimal marie, BigDecimal seulAvecEnfants,
                        BigDecimal veufAvecEnfants, BigDecimal parEnfant, BigDecimal maximum) {

        public static final Parts AUCUNE = new Parts(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ONE);

        public Parts {
            for (BigDecimal v : new BigDecimal[]{celibataire, marie, seulAvecEnfants, veufAvecEnfants, parEnfant,
                    maximum}) {
                if (v == null || v.signum() < 0 || v.compareTo(BigDecimal.valueOf(20)) > 0) {
                    throw new ErreurMetier("Parts : des nombres entre 0 et 20.");
                }
            }
            celibataire = celibataire.stripTrailingZeros();
            marie = marie.stripTrailingZeros();
            seulAvecEnfants = seulAvecEnfants.stripTrailingZeros();
            veufAvecEnfants = veufAvecEnfants.stripTrailingZeros();
            parEnfant = parEnfant.stripTrailingZeros();
            maximum = maximum.stripTrailingZeros();
        }

        public BigDecimal de(Foyer f) {
            BigDecimal base = switch (f.situation()) {
                case MARIE -> marie;
                case VEUF -> f.enfants() > 0 ? veufAvecEnfants : celibataire;
                default -> f.enfants() > 0 ? seulAvecEnfants : celibataire;
            };
            BigDecimal p = base.add(parEnfant.multiply(BigDecimal.valueOf(f.enfants())));
            return (p.compareTo(maximum) > 0 ? maximum : p).stripTrailingZeros();
        }
    }

    /**
     * L'impôt sur les salaires.
     *
     * @param retraiteDeductibleMax  part du brut soumis au-delà de laquelle les retraites « plafonnées » ne se
     *                               déduisent plus (%) ; 0 = sans limite
     * @param abattementLimite       revenu (dans la période de l'impôt) au-delà duquel l'abattement ne s'applique plus
     *                               ; 0 = sans limite
     * @param deductionParPersonne   déduction mensuelle par personne à charge
     * @param reductionParDemiPart   réduction mensuelle de l'impôt par demi-part au-delà de la première part
     * @param arrondiRevenu          pas d'arrondi (inférieur) du revenu imposable : 1, 1 000…
     * @param arrondiImpot           pas d'arrondi (inférieur) de l'impôt de la période : 1, 10…
     * @param arrondiMensuel         arrondi de l'impôt annuel divisé par 12
     * @param primesDifferentielles  barème annuel : une prime exceptionnelle est imposée par différence (impôt de
     *                               l'année avec la prime − sans la prime), en totalité le mois où elle est payée
     * @param gerantAbattement       régime des gérants : frais forfaitaires (%), sans déduction des cotisations ni de la
     *                               famille ; 0 = régime normal
     */
    public record Impot(String nom, String organisme, Periodicite periodicite, List<Tranche> tranches,
                        boolean deduireCotisations, BigDecimal retraiteDeductibleMax, BigDecimal abattement,
                        long abattementLimite, Famille famille, long deductionParPersonne, int personnesMax,
                        long reductionParDemiPart, Parts parts, long arrondiRevenu, long arrondiImpot,
                        Arrondi arrondiMensuel, boolean primesDifferentielles, boolean apprentisExoneres,
                        BigDecimal gerantAbattement, long gerantAbattementLimite, String compte, String note) {

        public Impot {
            nom = Textes.obligatoire(nom, "Nom de l'impôt", 20);
            organisme = Textes.obligatoire(organisme, "Organisme de l'impôt", 10).toUpperCase();
            periodicite = periodicite == null ? Periodicite.MENSUELLE : periodicite;
            tranches = List.copyOf(tranches == null ? List.of() : tranches);
            if (tranches.isEmpty() || tranches.size() > 15) {
                throw new ErreurMetier("Barème de l'impôt : entre 1 et 15 tranches.");
            }
            long avant = 0;
            for (int i = 0; i < tranches.size(); i++) {
                long l = tranches.get(i).limite();
                boolean derniere = i == tranches.size() - 1;
                if (!derniere && l == Long.MAX_VALUE) {
                    throw new ErreurMetier("Barème : seule la dernière tranche est sans limite.");
                }
                if (l <= avant) {
                    throw new ErreurMetier("Barème : les limites des tranches doivent croître.");
                }
                avant = l;
            }
            if (tranches.get(tranches.size() - 1).limite() != Long.MAX_VALUE) {
                throw new ErreurMetier("Barème : laissez vide la limite de la dernière tranche (sans limite).");
            }
            retraiteDeductibleMax = ReglesPaie.taux(retraiteDeductibleMax, "Retraite déductible");
            abattement = ReglesPaie.taux(abattement, "Abattement");
            ReglesPaie.montant(abattementLimite, "Limite de l'abattement");
            famille = famille == null ? Famille.AUCUNE : famille;
            ReglesPaie.montant(deductionParPersonne, "Déduction par personne à charge");
            if (personnesMax < 0 || personnesMax > 30) {
                throw new ErreurMetier("Personnes à charge au plus : entre 0 et 30.");
            }
            ReglesPaie.montant(reductionParDemiPart, "Réduction par demi-part");
            parts = parts == null ? Parts.AUCUNE : parts;
            if (arrondiRevenu < 1 || arrondiImpot < 1) {
                throw new ErreurMetier("Les pas d'arrondi valent au moins 1.");
            }
            arrondiMensuel = arrondiMensuel == null ? Arrondi.PLUS_PROCHE : arrondiMensuel;
            gerantAbattement = ReglesPaie.taux(gerantAbattement, "Frais des gérants");
            ReglesPaie.montant(gerantAbattementLimite, "Limite des frais des gérants");
            compte = ReglesPaie.compte(compte, "Impôt");
            note = Textes.facultatif(note, "Note de l'impôt", 500);
        }

        /** L'impôt du barème sur ce revenu, au franc inférieur. */
        public long bareme(long revenu) {
            if (revenu <= 0) {
                return 0;
            }
            BigDecimal total = BigDecimal.ZERO;
            long bas = 0;
            for (Tranche t : tranches) {
                long haut = Math.min(revenu, t.limite());
                if (haut > bas) {
                    total = total.add(BigDecimal.valueOf(haut - bas).multiply(t.taux()));
                }
                if (revenu <= t.limite()) {
                    break;
                }
                bas = t.limite();
            }
            return total.divide(CENT, 0, RoundingMode.DOWN).longValue();
        }
    }

    /** Une majoration d'heures supplémentaires : « 41e à 46e heure : 15 % ». */
    public record Majoration(String libelle, BigDecimal taux) {

        public Majoration {
            libelle = Textes.obligatoire(libelle, "Libellé de la majoration", 60);
            if (taux == null || taux.signum() < 0 || taux.compareTo(BigDecimal.valueOf(300)) > 0) {
                throw new ErreurMetier(libelle + " : majoration entre 0 et 300 %.");
            }
            taux = taux.stripTrailingZeros();
        }
    }

    /**
     * Les heures supplémentaires : salaire horaire = salaire de base ÷ heures du mois, majoré selon le cas.
     *
     * @param heuresDuMois heures de travail d'un mois complet (173,33 pour 40 heures par semaine)
     */
    public record HeuresSupplementaires(BigDecimal heuresDuMois, List<Majoration> majorations, String note) {

        public static final HeuresSupplementaires AUCUNE = new HeuresSupplementaires(new BigDecimal("173.33"),
                List.of(), "");

        public HeuresSupplementaires {
            if (heuresDuMois == null || heuresDuMois.compareTo(BigDecimal.ONE) < 0
                    || heuresDuMois.compareTo(BigDecimal.valueOf(400)) > 0) {
                throw new ErreurMetier("Heures du mois : entre 1 et 400.");
            }
            heuresDuMois = heuresDuMois.stripTrailingZeros();
            majorations = List.copyOf(majorations == null ? List.of() : majorations);
            if (majorations.size() > 10) {
                throw new ErreurMetier("Au plus 10 majorations d'heures supplémentaires.");
            }
            note = Textes.facultatif(note, "Note des heures supplémentaires", 300);
        }
    }

    /** Les comptes des écritures de paie (plan SYSCOHADA par défaut). */
    public record Comptes(String salaires, String indemnites, String chargesSociales, String remunerationsDues,
                          String avances) {

        public static final Comptes SYSCOHADA = new Comptes("661100", "663100", "664100", "422000", "421000");

        public Comptes {
            salaires = ReglesPaie.compte(salaires, "Salaires");
            indemnites = ReglesPaie.compte(indemnites, "Indemnités");
            chargesSociales = ReglesPaie.compte(chargesSociales, "Charges sociales");
            remunerationsDues = ReglesPaie.compte(remunerationsDues, "Rémunérations dues");
            avances = ReglesPaie.compte(avances, "Avances au personnel");
        }
    }

    public ReglesPaie {
        if (pays == null || depuis == null || contenu == null) {
            throw new ErreurMetier("Indiquez le pays, la date d'effet et le contenu des règles.");
        }
        if (depuis.getDayOfMonth() != 1) {
            throw new ErreurMetier("Des règles s'appliquent à partir du premier jour d'un mois.");
        }
        nom = Textes.obligatoire(nom, "Nom des règles", 80);
        modele = modele == null ? "" : modele;
        notes = Textes.facultatif(notes, "Notes", 1000);
        modifiePar = modifiePar == null ? "" : modifiePar;
    }

    /** Les mêmes règles, avec un autre contenu ou une autre date, modifiées par cette personne. */
    public ReglesPaie avec(LocalDate nouvelleDate, String nouveauNom, Contenu nouveau, String nouvellesNotes,
                           String par, Instant quand) {
        return new ReglesPaie(id, tenantId, pays, nouvelleDate, nouveauNom, modele, nouveau, nouvellesNotes, quand, par);
    }

    // ------------------------------------------------------------------ contrôles communs

    static String code(String code) {
        String c = code == null ? "" : code.strip().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (c.isEmpty() || c.length() > 20) {
            throw new ErreurMetier("Code de ligne : de 1 à 20 lettres ou chiffres.");
        }
        return c;
    }

    static BigDecimal taux(BigDecimal t, String champ) {
        BigDecimal v = t == null ? BigDecimal.ZERO : t;
        if (v.signum() < 0 || v.compareTo(CENT) > 0) {
            throw new ErreurMetier(champ + " : un taux entre 0 et 100 %.");
        }
        return v.stripTrailingZeros();
    }

    static void montant(long m, String champ) {
        if (m < 0 || m > 100_000_000_000L) {
            throw new ErreurMetier(champ + " : un montant positif.");
        }
    }

    static String compte(String compte, String champ) {
        String c = compte == null ? "" : compte.strip();
        if (!c.isEmpty() && !c.matches("[0-9A-Za-z]{2,12}")) {
            throw new ErreurMetier(champ + " : un numéro de compte de 2 à 12 chiffres.");
        }
        return c;
    }
}
