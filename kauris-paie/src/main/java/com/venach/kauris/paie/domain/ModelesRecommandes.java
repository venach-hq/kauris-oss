package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.paie.domain.ReglesPaie.Application;
import com.venach.kauris.paie.domain.ReglesPaie.Arrondi;
import com.venach.kauris.paie.domain.ReglesPaie.Comptes;
import com.venach.kauris.paie.domain.ReglesPaie.Contenu;
import com.venach.kauris.paie.domain.ReglesPaie.Cotisation;
import com.venach.kauris.paie.domain.ReglesPaie.Deduction;
import com.venach.kauris.paie.domain.ReglesPaie.Famille;
import com.venach.kauris.paie.domain.ReglesPaie.Forfait;
import com.venach.kauris.paie.domain.ReglesPaie.HeuresSupplementaires;
import com.venach.kauris.paie.domain.ReglesPaie.Impot;
import com.venach.kauris.paie.domain.ReglesPaie.Majoration;
import com.venach.kauris.paie.domain.ReglesPaie.Palier;
import com.venach.kauris.paie.domain.ReglesPaie.Parts;
import com.venach.kauris.paie.domain.ReglesPaie.Periodicite;
import com.venach.kauris.paie.domain.ReglesPaie.Reduction;
import com.venach.kauris.paie.domain.ReglesPaie.TaxeEmployeur;
import com.venach.kauris.paie.domain.ReglesPaie.Tranche;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Les règles que Kauris recommande, pays par pays, avec leurs notes et leurs sources. Ce ne sont que des
 * propositions : l'entreprise les active, puis les corrige dans l'éditeur des règles (son taux d'accident du travail,
 * une loi de finances nouvelle, sa convention collective). Ajouter un pays, c'est ajouter un modèle ici, ou laisser
 * l'entreprise partir du modèle vide.
 * <p>
 * Valeurs vérifiées en novembre 2026. Quand les sources publiques divergent, la note le dit.
 */
public final class ModelesRecommandes {

    /** Un modèle de règles. */
    public record Modele(String code, Pays pays, String nom, LocalDate depuis, Contenu contenu, String notes,
                         List<String> sources) {
    }

    public static final String TOGO_2026 = "TG-2026";
    public static final String COTE_D_IVOIRE_2024 = "CI-2024";
    public static final String SENEGAL_2026 = "SN-2026";
    public static final String VIDE = "VIDE";

    private ModelesRecommandes() {
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Tranche t(long jusqua, String taux) {
        return new Tranche(jusqua, d(taux));
    }

    // ================================================================== Togo

    static Modele togo() {
        List<Cotisation> cotisations = List.of(
                new Cotisation("CNSS_PENSION", "CNSS : pension (vieillesse, invalidité, décès)", "CNSS", d("4"),
                        d("12.5"), 0, 0, 0, 0, 0, Deduction.RETRAITE_PLAFONNEE, Application.TOUS, true, "431100",
                        "Régime général de la CNSS : 4 % salarié, 12,5 % employeur, sans plafond."),
                new Cotisation("CNSS_FAMILLE", "CNSS : prestations familiales", "CNSS", d("0"), d("3"), 0, 0, 0, 0,
                        0, Deduction.TOTALE, Application.TOUS, true, "431100", ""),
                new Cotisation("CNSS_RISQUES", "CNSS : risques professionnels", "CNSS", d("0"), d("2"), 0, 0, 0, 0,
                        0, Deduction.TOTALE, Application.TOUS, true, "431100",
                        "Taux courant ; vérifiez celui notifié par la CNSS pour votre activité."),
                new Cotisation("AMU", "Assurance maladie universelle (AMU)", "AMU", d("5"), d("5"), 0, 0, 0, 0, 0,
                        Deduction.TOTALE, Application.TOUS, true, "431300",
                        "Décret n° 2023-096/PR : 10 % de la rémunération, dont au moins la moitié à la charge de "
                                + "l'employeur. Certaines sources citent un autre partage : vérifiez avec la CNSS."));
        Impot irpp = new Impot("IRPP", "OTR", Periodicite.ANNUELLE, List.of(
                t(900_000, "0"), t(3_000_000, "3"), t(6_000_000, "10"), t(9_000_000, "15"), t(12_000_000, "20"),
                t(15_000_000, "25"), t(20_000_000, "30"), t(0, "35")),
                true, d("6"), d("28"), 10_000_000, Famille.DEDUCTION_PAR_PERSONNE, 10_000, 6, 0, Parts.AUCUNE,
                List.of(), d("0"), 1000, 10, Arrondi.PLUS_PROCHE, true, true, d("20"), 10_000_000, "447200",
                "CGI du Togo : cotisations déductibles, la retraite dans la limite de 6 % du brut (art. 26-1) ; "
                        + "abattement de 28 % sur la part du revenu jusqu'à 10 000 000 (art. 26) ; 10 000 F par mois "
                        + "et par personne à charge, six au plus (art. 72-73) ; revenu arrondi au millier inférieur, "
                        + "impôt à la dizaine inférieure (art. 74) ; apprentis exonérés (art. 23-4) ; gérants : 20 % "
                        + "de frais jusqu'à 10 000 000, sans autre déduction (art. 28) ; primes non mensuelles imposées "
                        + "par différence.");
        HeuresSupplementaires heures = new HeuresSupplementaires(d("173.33"), List.of(
                new Majoration("Heures de jour, jours ouvrables", d("15")),
                new Majoration("Heures de nuit", d("35")),
                new Majoration("Dimanches et jours fériés", d("60"))),
                "40 heures par semaine. Les sources divergent sur les majorations (15/35/60 % ou 25/50 %) : "
                        + "appliquez celles de votre convention collective.");
        return new Modele(TOGO_2026, Pays.TOGO, "Togo — règles recommandées 2026", LocalDate.of(2026, 1, 1),
                new Contenu(cotisations, List.of(), List.of(), irpp, heures, Comptes.SYSCOHADA),
                "Une taxe de 3 % sur les salaires est parfois citée pour le Togo ; elle n'est pas retenue ici. "
                        + "Ajoutez-la dans les taxes de l'employeur si votre conseil la confirme.",
                List.of("CNSS Togo — taux du régime général (CLEISS, 1er janvier 2023)",
                        "Décret n° 2023-096/PR fixant les cotisations de l'Assurance maladie universelle",
                        "Code général des impôts du Togo, mis à jour 2025, et loi de finances 2026 (OTR)"));
    }

    // ================================================================== Côte d'Ivoire

    static Modele coteDIvoire() {
        List<Cotisation> cotisations = List.of(
                new Cotisation("CNPS_RETRAITE", "CNPS : retraite", "CNPS", d("6.3"), d("7.7"), 3_375_000, 75_000, 0,
                        0, 0, Deduction.AUCUNE, Application.TOUS, true, "431100",
                        "Plafond mensuel 3 375 000 ; assiette minimale : le SMIG (75 000)."),
                new Cotisation("CNPS_FAMILLE", "CNPS : prestations familiales", "CNPS", d("0"), d("5"), 70_000, 0, 0,
                        0, 0, Deduction.AUCUNE, Application.TOUS, true, "431100", "Plafond mensuel 70 000."),
                new Cotisation("CNPS_MATERNITE", "CNPS : assurance maternité", "CNPS", d("0"), d("0.75"), 70_000, 0,
                        0, 0, 0, Deduction.AUCUNE, Application.TOUS, true, "431100", ""),
                new Cotisation("CNPS_ACCIDENT", "CNPS : accidents du travail", "CNPS", d("0"), d("2"), 70_000, 0, 0,
                        0, 0, Deduction.AUCUNE, Application.TOUS, true, "431100",
                        "De 2 à 5 % selon le risque de votre secteur : indiquez le taux notifié par la CNPS."),
                new Cotisation("CMU", "Couverture maladie universelle (CMU)", "CMU", d("0"), d("0"), 0, 0, 500, 500,
                        8, Deduction.AUCUNE, Application.TOUS, true, "431300",
                        "1 000 F par mois et par personne couverte (salarié, conjoint, enfants), moitié chacun."));
        List<TaxeEmployeur> taxes = List.of(
                new TaxeEmployeur("PATRONAL_LOCAL", "Contribution patronale sur les salaires (personnel local)",
                        "DGI", d("1.2"), 0, Application.LOCAUX, true, "641300", "447300", ""),
                new TaxeEmployeur("PATRONAL_EXPAT", "Contribution patronale sur les salaires (expatriés)", "DGI",
                        d("10.4"), 0, Application.EXPATRIES, true, "641300", "447300",
                        "1,2 % de contribution nationale + 9,2 % de contribution des employeurs d'expatriés."),
                new TaxeEmployeur("TA", "Taxe d'apprentissage", "FDFP", d("0.4"), 0, Application.TOUS, true, "641400",
                        "447800", ""),
                new TaxeEmployeur("FPC", "Formation professionnelle continue", "FDFP", d("0.6"), 0, Application.TOUS,
                        true, "641400", "447800", ""),
                new TaxeEmployeur("FPC_ADD", "Formation professionnelle continue (taxe additionnelle)", "FDFP",
                        d("0.6"), 0, Application.TOUS, true, "641400", "447800", ""));
        Impot its = new Impot("ITS", "DGI", Periodicite.MENSUELLE, List.of(
                t(75_000, "0"), t(240_000, "16"), t(800_000, "21"), t(2_400_000, "24"), t(8_000_000, "28"),
                t(0, "32")),
                false, d("0"), d("0"), 0, Famille.REDUCTION_PAR_DEMI_PART, 0, 0, 5_500,
                new Parts(d("1"), d("2"), d("1.5"), d("2"), d("0.5"), d("5"), d("0")), List.of(), d("0"),
                1, 1, Arrondi.PLUS_PROCHE, false, false, d("0"), 0, "447200",
                "Réforme de l'ITS (ordonnance de 2023, en vigueur au 1er janvier 2024) : barème mensuel sur le brut "
                        + "imposable, sans abattement ni déduction des cotisations ; réduction pour charges de famille "
                        + "de 5 500 F par demi-part au-delà d'une part (art. 120 du CGI), cinq parts au plus.");
        HeuresSupplementaires heures = new HeuresSupplementaires(d("173.33"), List.of(
                new Majoration("41e à 46e heure", d("15")),
                new Majoration("Au-delà de la 46e heure", d("50")),
                new Majoration("Heures de nuit", d("75")),
                new Majoration("Jour, dimanches et jours fériés", d("75")),
                new Majoration("Nuit, dimanches et jours fériés", d("100"))),
                "Décret n° 96-203 du 7 mars 1996, article 24 ; 40 heures par semaine.");
        return new Modele(COTE_D_IVOIRE_2024, Pays.COTE_D_IVOIRE, "Côte d'Ivoire — règles recommandées",
                LocalDate.of(2024, 1, 1), new Contenu(cotisations, taxes, List.of(), its, heures, Comptes.SYSCOHADA), "",
                List.of("CNPS — taux au 1er janvier 2025 (CLEISS)",
                        "Ordonnance de 2023 réformant l'impôt sur les traitements et salaires, art. 119 et 120 du CGI",
                        "Décret n° 96-203 du 7 mars 1996 relatif à la durée du travail"));
    }

    // ================================================================== Sénégal

    private static Reduction red(String parts, String taux, long minimum, long maximum) {
        return new Reduction(d(parts), d(taux), minimum, maximum);
    }

    static Modele senegal() {
        List<Cotisation> cotisations = List.of(
                new Cotisation("IPRES_RG", "IPRES : retraite, régime général", "IPRES", d("5.6"), d("8.4"), 432_000, 0,
                        0, 0, 0, Deduction.AUCUNE, Application.TOUS, true, "431300",
                        "Plafond mensuel 432 000 depuis le 1er juin 2022 (décision du conseil d'administration de "
                                + "l'IPRES du 25 février 2022)."),
                new Cotisation("IPRES_RCC", "IPRES : retraite complémentaire des cadres", "IPRES", d("2.4"), d("3.6"),
                        1_296_000, 0, 0, 0, 0, Deduction.AUCUNE, Application.CADRES, true, "432000",
                        "Cadres seulement (cochez « Cadre » sur la fiche du salarié), sur le salaire jusqu'à 1 296 000 "
                                + "par mois, en plus du régime général."),
                new Cotisation("CSS_PF", "CSS : prestations familiales", "CSS", d("0"), d("7"), 63_000, 0, 0, 0, 0,
                        Deduction.AUCUNE, Application.TOUS, true, "431100", "Plafond mensuel 63 000."),
                new Cotisation("CSS_AT", "CSS : accidents du travail et maladies professionnelles", "CSS", d("0"),
                        d("1"), 63_000, 0, 0, 0, 0, Deduction.AUCUNE, Application.TOUS, true, "431200",
                        "1 %, 3 % ou 5 % selon le risque de votre activité : indiquez le taux notifié par la CSS."),
                new Cotisation("IPM", "IPM : prévoyance maladie", "IPM", d("3"), d("3"), 250_000, 0, 0, 0, 0,
                        Deduction.AUCUNE, Application.TOUS, true, "433100",
                        "Chaque IPM fixe son taux (souvent 5 à 10 % au total) et son plafond ; 3 % + 3 % sur 250 000 "
                                + "au plus est l'usage. Reprenez ceux de votre IPM."));
        List<TaxeEmployeur> taxes = List.of(
                new TaxeEmployeur("CFCE", "Contribution forfaitaire à la charge de l'employeur (CFCE)", "DGID",
                        d("3"), 0, Application.TOUS, true, "641300", "447300",
                        "3 % du brut imposable, depuis le 1er janvier 2013 (CGI, loi n° 2012-31). Les entreprises "
                                + "franches peuvent en être exonérées."));
        List<Forfait> forfaits = List.of(
                new Forfait("TRIMF", "Taxe représentative de l'impôt du minimum fiscal (TRIMF)", "DGID", List.of(
                        new Palier(600_000, 900), new Palier(1_000_000, 3_600), new Palier(2_000_000, 4_800),
                        new Palier(7_000_000, 12_000), new Palier(12_000_000, 18_000), new Palier(0, 36_000)),
                        true, "447200",
                        "Montant annuel selon le revenu brut annuel, retenu par douzième ; dû aussi pour l'épouse "
                                + "sans revenu. Certaines sources placent le 2e palier à 1 200 000 : vérifiez."));
        Impot ir = new Impot("IR", "DGID", Periodicite.ANNUELLE, List.of(
                t(630_000, "0"), t(1_500_000, "20"), t(4_000_000, "30"), t(8_000_000, "35"), t(13_500_000, "37"),
                t(50_000_000, "40"), t(0, "43")),
                false, d("0"), d("30"), 3_000_000, Famille.REDUCTION_EN_POURCENTAGE, 0, 0, 0,
                new Parts(d("1"), d("1.5"), d("1"), d("1.5"), d("0.5"), d("5"), d("0.5")),
                List.of(red("1.5", "10", 100_000, 300_000), red("2", "15", 200_000, 650_000),
                        red("2.5", "20", 300_000, 1_100_000), red("3", "25", 400_000, 1_650_000),
                        red("3.5", "30", 500_000, 2_030_000), red("4", "35", 600_000, 2_490_000),
                        red("4.5", "40", 700_000, 2_755_000), red("5", "45", 800_000, 3_180_000)),
                d("43"), 1000, 1, Arrondi.PLUS_PROCHE, true, false, d("0"), 0, "447200",
                "CGI (loi n° 2012-31) : abattement de 30 % au plus 900 000 par an (30 % jusqu'à 3 000 000), qui "
                        + "remplace la déduction des cotisations (art. 168) ; barème annuel, 43 % au-delà de 50 000 000 "
                        + "(art. 173, LFR 2022) ; réduction pour charges de famille en % de l'impôt, entre un minimum et "
                        + "un maximum selon les parts (art. 174) ; impôt plafonné à 43 % du revenu imposable ; revenu "
                        + "arrondi au millier inférieur.");
        HeuresSupplementaires heures = new HeuresSupplementaires(d("173.33"), List.of(
                new Majoration("41e à 48e heure", d("15")),
                new Majoration("Au-delà de la 48e heure", d("40")),
                new Majoration("Heures de nuit", d("60")),
                new Majoration("Jour, repos hebdomadaire et jours fériés", d("60")),
                new Majoration("Nuit, repos hebdomadaire et jours fériés", d("100"))),
                "Décret n° 70-184 du 20 février 1970 ; 40 heures par semaine. Certaines sources donnent 75 % pour "
                        + "la nuit : appliquez votre convention collective.");
        return new Modele(SENEGAL_2026, Pays.SENEGAL, "Sénégal — règles recommandées 2026", LocalDate.of(2026, 1, 1),
                new Contenu(cotisations, taxes, forfaits, ir, heures, Comptes.SYSCOHADA),
                "Parts (art. 174) : 1 ; marié 1,5, plus 0,5 si le conjoint n'a pas de revenu ; plus 0,5 par "
                        + "enfant ; veuf avec enfants comme un marié ; 5 au plus. Un non-résident n'a qu'une part, sans "
                        + "réduction : déclarez-le célibataire sans enfant. "
                        + "À vérifier pour votre entreprise : le taux d'accident du travail notifié par la CSS, le taux et le "
                        + "plafond de votre IPM, une exonération de CFCE (entreprise franche). Un salarié qui a plusieurs "
                        + "épouses sans revenu doit une TRIMF pour chacune : ajoutez la différence en retenue.",
                List.of("Code général des impôts du Sénégal (loi n° 2012-31), art. 168, 173 et 174",
                        "IPRES — plafonds du régime général et du régime des cadres au 1er juin 2022",
                        "Code de la sécurité sociale (loi n° 73-37) — CSS, prestations familiales et accidents du "
                                + "travail",
                        "Décret n° 70-184 du 20 février 1970 — rémunération des heures supplémentaires"));
    }

    // ================================================================== vide

    /** Un point de départ pour un pays sans modèle : un impôt à zéro, aucune cotisation. Tout est à saisir. */
    public static Modele vide(Pays pays, LocalDate depuis) {
        Impot impot = new Impot("Impôt", "IMPOTS", Periodicite.MENSUELLE, List.of(t(0, "0")), false, d("0"), d("0"),
                0, Famille.AUCUNE, 0, 0, 0, Parts.AUCUNE, List.of(), d("0"), 1, 1, Arrondi.PLUS_PROCHE, false, false, d("0"), 0, "447200",
                "À compléter : barème, abattements et charges de famille du pays.");
        return new Modele(VIDE, pays, pays.nom() + " — règles à compléter", depuis.withDayOfMonth(1),
                new Contenu(List.of(), List.of(), List.of(), impot, HeuresSupplementaires.AUCUNE, Comptes.SYSCOHADA),
                "Aucun modèle recommandé pour ce pays : saisissez les cotisations, l'impôt et les taxes avec votre "
                        + "conseil.", List.of());
    }

    // ================================================================== catalogue

    public static List<Modele> tous() {
        return List.of(togo(), coteDIvoire(), senegal());
    }

    public static Optional<Modele> pour(Pays pays) {
        return tous().stream().filter(m -> m.pays() == pays).findFirst();
    }

    public static Optional<Modele> parCode(String code) {
        return tous().stream().filter(m -> m.code().equals(code)).findFirst();
    }

    /** Les pays qui ont un modèle recommandé. */
    public static List<Pays> paysRecommandes() {
        return Arrays.stream(Pays.values()).filter(p -> pour(p).isPresent()).toList();
    }
}
