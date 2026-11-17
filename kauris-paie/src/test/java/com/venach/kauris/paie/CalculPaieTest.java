package com.venach.kauris.paie;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.paie.domain.Bareme;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Element;
import com.venach.kauris.paie.domain.Foyer;
import com.venach.kauris.paie.domain.Parametre;
import com.venach.kauris.paie.domain.Parametres;
import com.venach.kauris.paie.domain.PaysPaie;
import com.venach.kauris.paie.domain.SituationFamiliale;
import com.venach.kauris.paie.domain.TypeContrat;
import com.venach.kauris.paie.domain.TypeElement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le calcul des bulletins, sur des cas comptés à la main. */
class CalculPaieTest {

    private final Parametres p = Parametres.parDefaut();

    private static CalculPaie.Resultat calcul(PaysPaie pays, long base, int jours, List<Element> e, Foyer foyer,
                                              Parametres p) {
        return CalculPaie.calculer(new CalculPaie.Entree(pays, base, jours, e, foyer, false), p);
    }

    private static Foyer foyer(SituationFamiliale s, int enfants, boolean conjoint) {
        return new Foyer(s, enfants, conjoint);
    }

    // ================================================================== Togo

    @Test
    void togoSalarieSeul() {
        CalculPaie.Resultat r = calcul(PaysPaie.TOGO, 300_000, 30, List.of(), Foyer.seul(), p);
        // CNSS 4 % = 12 000 ; AMU 5 % = 15 000.
        assertEquals(12_000, r.salarieDe("CNSS"));
        assertEquals(15_000, r.salarieDe("AMU"));
        // (300 000 − 27 000) × 12 = 3 276 000 ; − 28 % (917 280) = 2 358 720 → 2 358 000 ;
        // (2 358 000 − 900 000) × 3 % = 43 740 par an, soit 3 645 par mois.
        assertEquals(2_358_000, r.baseImpot());
        assertEquals(3_645, r.impot());
        assertEquals(269_355, r.netAPayer());
        // Employeur : pension 12,5 % + famille 3 % + risques 2 % + AMU 5 % = 22,5 %.
        assertEquals(37_500 + 9_000 + 6_000, r.employeurDe("CNSS"));
        assertEquals(15_000, r.employeurDe("AMU"));
        assertEquals(367_500, r.coutEmployeur());
    }

    @Test
    void togoPersonnesACharge() {
        // Marié, conjoint sans revenu, deux enfants : 3 personnes × 10 000 × 12 = 360 000 de moins.
        CalculPaie.Resultat r = calcul(PaysPaie.TOGO, 300_000, 30, List.of(),
                foyer(SituationFamiliale.MARIE, 2, true), p);
        // 3 276 000 − 917 280 − 360 000 = 1 998 720 → 1 998 000 ; 1 098 000 × 3 % = 32 940 ; / 12 = 2 745.
        assertEquals(1_998_000, r.baseImpot());
        assertEquals(2_745, r.impot());
        // Plus de six personnes : six seulement.
        CalculPaie.Resultat huit = calcul(PaysPaie.TOGO, 300_000, 30, List.of(),
                foyer(SituationFamiliale.MARIE, 7, true), p);
        assertEquals(3_276_000 - 917_280 - 720_000, huit.baseImpot() + 720, "arrondi au millier inférieur");
    }

    @Test
    void togoAbattementLimite() {
        CalculPaie.Resultat r = calcul(PaysPaie.TOGO, 2_000_000, 30, List.of(), Foyer.seul(), p);
        // (2 000 000 − 80 000 − 100 000) × 12 = 21 840 000 ; abattement 28 % de 10 000 000 = 2 800 000 ;
        // imposable 19 040 000 : 2 163 000 jusqu'à 15 000 000, puis 4 040 000 × 30 % = 1 212 000.
        assertEquals(19_040_000, r.baseImpot());
        assertEquals(3_375_000 / 12, r.impot());
    }

    @Test
    void togoPetitSalaireSansImpot() {
        CalculPaie.Resultat r = calcul(PaysPaie.TOGO, 90_000, 30, List.of(), Foyer.seul(), p);
        assertEquals(0, r.impot());
        assertEquals(90_000 - 3_600 - 4_500, r.netAPayer());
    }

    // ================================================================== Côte d'Ivoire

    @Test
    void coteDIvoireMarieDeuxEnfants() {
        CalculPaie.Resultat r = calcul(PaysPaie.COTE_D_IVOIRE, 500_000, 30, List.of(),
                foyer(SituationFamiliale.MARIE, 2, false), p);
        // Retraite 6,3 % / 7,7 % ; famille, maternité, accident sur 70 000.
        assertEquals(31_500 + 0, r.salarieDe("CNPS"));
        assertEquals(38_500 + 3_500 + 525 + 1_400, r.employeurDe("CNPS"));
        // CMU : 4 personnes × 1 000, moitié chacun.
        assertEquals(2_000, r.salarieDe("CMU"));
        assertEquals(2_000, r.employeurDe("CMU"));
        // ITS : 165 000 × 16 % + 260 000 × 21 % = 81 000 ; 3 parts → 4 demi-parts × 5 500 = 22 000 de réduction.
        assertEquals(59_000, r.impot());
        assertEquals(500_000 - 31_500 - 2_000 - 59_000, r.netAPayer());
        // Taxes de l'employeur : 1,2 % + 0,4 % + 0,6 % + 0,6 %.
        assertEquals(6_000, r.employeurDe("DGI"));
        assertEquals(2_000 + 3_000 + 3_000, r.employeurDe("FDFP"));
    }

    @Test
    void coteDIvoirePlafonds() {
        CalculPaie.Resultat r = calcul(PaysPaie.COTE_D_IVOIRE, 4_000_000, 30, List.of(), Foyer.seul(), p);
        assertEquals(212_625, r.salarieDe("CNPS"), "retraite plafonnée à 3 375 000");
        // 26 400 + 117 600 + 384 000 + 1 600 000 × 28 % = 976 000.
        assertEquals(976_000, r.impot());
        assertEquals(1_000 / 2, r.salarieDe("CMU"));
        CalculPaie.Resultat expatrie = CalculPaie.calculer(new CalculPaie.Entree(PaysPaie.COTE_D_IVOIRE, 4_000_000,
                30, List.of(), Foyer.seul(), true), p);
        assertEquals(416_000, expatrie.employeurDe("DGI"), "10,4 % pour un expatrié");
    }

    @Test
    void coteDIvoirePetitSalaire() {
        CalculPaie.Resultat r = calcul(PaysPaie.COTE_D_IVOIRE, 75_000, 30, List.of(), Foyer.seul(), p);
        assertEquals(0, r.impot(), "pas d'ITS jusqu'à 75 000");
        // La réduction ne rend jamais l'impôt négatif.
        CalculPaie.Resultat famille = calcul(PaysPaie.COTE_D_IVOIRE, 100_000, 30, List.of(),
                foyer(SituationFamiliale.MARIE, 4, false), p);
        assertEquals(0, famille.impot());
    }

    @Test
    void partsDeLaCoteDIvoire() {
        assertEquals(new BigDecimal("1"), Foyer.seul().parts());
        assertEquals(new BigDecimal("2"), foyer(SituationFamiliale.CELIBATAIRE, 1, false).parts());
        assertEquals(new BigDecimal("2.5"), foyer(SituationFamiliale.DIVORCE, 2, false).parts());
        assertEquals(new BigDecimal("2"), foyer(SituationFamiliale.MARIE, 0, false).parts());
        assertEquals(new BigDecimal("3"), foyer(SituationFamiliale.MARIE, 2, false).parts());
        assertEquals(new BigDecimal("2.5"), foyer(SituationFamiliale.VEUF, 1, false).parts());
        assertEquals(new BigDecimal("1"), foyer(SituationFamiliale.VEUF, 0, false).parts());
        assertEquals(new BigDecimal("5"), foyer(SituationFamiliale.MARIE, 9, false).parts());
        assertEquals(1 + 1 + 3, foyer(SituationFamiliale.MARIE, 3, true).personnesCmu());
        assertTrue(!foyer(SituationFamiliale.CELIBATAIRE, 0, true).conjointACharge(), "pas de conjoint sans mariage");
    }

    // ================================================================== éléments, prorata, paramètres

    @Test
    void elementsDePaie() {
        List<Element> e = List.of(new Element("Prime de rendement", TypeElement.GAIN_SOUMIS, 50_000),
                new Element("Logement", TypeElement.AVANTAGE_NATURE, 40_000),
                new Element("Transport", TypeElement.GAIN_NON_SOUMIS, 20_000),
                new Element("Avance du 10", TypeElement.RETENUE, 30_000));
        CalculPaie.Resultat r = calcul(PaysPaie.TOGO, 200_000, 30, e, Foyer.seul(), p);
        assertEquals(290_000, r.brutSoumis(), "base + prime + avantage en nature");
        assertEquals(310_000, r.brut());
        assertEquals(26_100, r.cotisationsSalarie(), "9 % du brut soumis, rien sur le transport");
        assertEquals(310_000 - 40_000 - 26_100 - r.impot() - 30_000, r.netAPayer());
        assertEquals(30_000, r.retenues());
    }

    @Test
    void retenueTropForte() {
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> calcul(PaysPaie.TOGO, 100_000, 30,
                List.of(new Element("Avance", TypeElement.RETENUE, 200_000)), Foyer.seul(), p));
        assertTrue(e.getMessage().contains("négatif"));
    }

    @Test
    void prorataEtJoursDuContrat() {
        assertEquals(150_000, CalculPaie.prorata(300_000, 15));
        assertEquals(300_000, CalculPaie.prorata(300_000, 30));
        ContratTravail c = ContratTravail.de(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CTR-1",
                new ContratTravail.Saisie(TypeContrat.CDI, "Vendeuse", "", LocalDate.of(2026, 11, 16), null, 150_000,
                        List.of(), ""), Instant.EPOCH);
        assertEquals(0, c.joursDans(YearMonth.of(2026, 10)));
        assertEquals(15, c.joursDans(YearMonth.of(2026, 11)), "entrée le 16 : 15 jours");
        assertEquals(30, c.joursDans(YearMonth.of(2026, 12)), "mois de 31 jours complet : 30");
        ContratTravail fini = c.terminer(LocalDate.of(2027, 2, 10), "Démission");
        assertEquals(10, fini.joursDans(YearMonth.of(2027, 2)));
        assertEquals(0, fini.joursDans(YearMonth.of(2027, 3)));
        assertThrows(ErreurMetier.class, () -> ContratTravail.de(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "CTR-2", new ContratTravail.Saisie(TypeContrat.CDD, "Caissier", "",
                        LocalDate.of(2026, 11, 1), null, 100_000, List.of(), ""), Instant.EPOCH), "un CDD a une fin");
    }

    @Test
    void correctionsDatees() {
        UUID t = UUID.randomUUID();
        List<Parametres.Correction> c = List.of(
                new Parametres.Correction(t, Parametre.TG_CNSS_RISQUES_EMPLOYEUR, LocalDate.of(2027, 1, 1),
                        new BigDecimal("3"), "Lettre de la CNSS"),
                new Parametres.Correction(t, Parametre.TG_CNSS_RISQUES_EMPLOYEUR, LocalDate.of(2027, 6, 1),
                        new BigDecimal("2.5"), ""));
        assertEquals(new BigDecimal("2"), Parametres.au(LocalDate.of(2026, 12, 31), c)
                .valeur(Parametre.TG_CNSS_RISQUES_EMPLOYEUR));
        assertEquals(new BigDecimal("3"), Parametres.au(LocalDate.of(2027, 1, 31), c)
                .valeur(Parametre.TG_CNSS_RISQUES_EMPLOYEUR));
        Parametres juin = Parametres.au(LocalDate.of(2027, 6, 30), c);
        assertEquals(new BigDecimal("2.5"), juin.valeur(Parametre.TG_CNSS_RISQUES_EMPLOYEUR));
        assertTrue(juin.corrige(Parametre.TG_CNSS_RISQUES_EMPLOYEUR));
        CalculPaie.Resultat r = calcul(PaysPaie.TOGO, 100_000, 30, List.of(), Foyer.seul(), juin);
        assertEquals(12_500 + 3_000 + 2_500, r.employeurDe("CNSS"));
        assertThrows(ErreurMetier.class, () -> new Parametres.Correction(t, Parametre.TG_CNSS_SALARIE,
                LocalDate.of(2027, 1, 1), new BigDecimal("140"), ""), "un taux ne dépasse pas 100 %");
        assertThrows(ErreurMetier.class, () -> new Parametres.Correction(t, Parametre.CI_RETRAITE_PLAFOND,
                LocalDate.of(2027, 1, 1), new BigDecimal("1000.5"), ""), "un plafond est entier");
    }

    @Test
    void baremes() {
        assertEquals(0, Bareme.IRPP_TOGO.impot(900_000));
        assertEquals(63_000, Bareme.IRPP_TOGO.impot(3_000_000));
        assertEquals(2_163_000, Bareme.IRPP_TOGO.impot(15_000_000));
        assertEquals(2_163_000 + 1_500_000 + 350_000, Bareme.IRPP_TOGO.impot(21_000_000));
        assertEquals(0, Bareme.ITS_COTE_D_IVOIRE.impot(75_000));
        assertEquals(26_400, Bareme.ITS_COTE_D_IVOIRE.impot(240_000));
        assertEquals(0, Bareme.ITS_COTE_D_IVOIRE.impot(-5));
    }
}
