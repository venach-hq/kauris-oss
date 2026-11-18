package com.venach.kauris.paie.domain;

import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.paie.domain.ReglesPaie.Application;
import com.venach.kauris.paie.domain.ReglesPaie.Arrondi;
import com.venach.kauris.paie.domain.ReglesPaie.Comptes;
import com.venach.kauris.paie.domain.ReglesPaie.Contenu;
import com.venach.kauris.paie.domain.ReglesPaie.Cotisation;
import com.venach.kauris.paie.domain.ReglesPaie.Deduction;
import com.venach.kauris.paie.domain.ReglesPaie.Famille;
import com.venach.kauris.paie.domain.ReglesPaie.HeuresSupplementaires;
import com.venach.kauris.paie.domain.ReglesPaie.Impot;
import com.venach.kauris.paie.domain.ReglesPaie.Majoration;
import com.venach.kauris.paie.domain.ReglesPaie.Parts;
import com.venach.kauris.paie.domain.ReglesPaie.Periodicite;
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
                1000, 10, Arrondi.PLUS_PROCHE, true, true, d("20"), 10_000_000, "447200",
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
                new Contenu(cotisations, List.of(), irpp, heures, Comptes.SYSCOHADA),
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
                new Parts(d("1"), d("2"), d("1.5"), d("2"), d("0.5"), d("5")),
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
                LocalDate.of(2024, 1, 1), new Contenu(cotisations, taxes, its, heures, Comptes.SYSCOHADA), "",
                List.of("CNPS — taux au 1er janvier 2025 (CLEISS)",
                        "Ordonnance de 2023 réformant l'impôt sur les traitements et salaires, art. 119 et 120 du CGI",
                        "Décret n° 96-203 du 7 mars 1996 relatif à la durée du travail"));
    }

    // ================================================================== vide

    /** Un point de départ pour un pays sans modèle : un impôt à zéro, aucune cotisation. Tout est à saisir. */
    public static Modele vide(Pays pays, LocalDate depuis) {
        Impot impot = new Impot("Impôt", "IMPOTS", Periodicite.MENSUELLE, List.of(t(0, "0")), false, d("0"), d("0"),
                0, Famille.AUCUNE, 0, 0, 0, Parts.AUCUNE, 1, 1, Arrondi.PLUS_PROCHE, false, false, d("0"), 0, "447200",
                "À compléter : barème, abattements et charges de famille du pays.");
        return new Modele(VIDE, pays, pays.nom() + " — règles à compléter", depuis.withDayOfMonth(1), new Contenu(List.of(), List.of(), impot, HeuresSupplementaires.AUCUNE, Comptes.SYSCOHADA),
                "Aucun modèle recommandé pour ce pays : saisissez les cotisations, l'impôt et les taxes avec votre "
                        + "conseil.", List.of());
    }

    // ================================================================== catalogue

    public static List<Modele> tous() {
        return List.of(togo(), coteDIvoire());
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
