package com.venach.kauris.paie;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.CalculPaie;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.EcrituresPaie;
import com.venach.kauris.paie.domain.Element;
import com.venach.kauris.paie.domain.Foyer;
import com.venach.kauris.paie.domain.ModePaiement;
import com.venach.kauris.paie.domain.ModelesRecommandes;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.RegimeFiscal;
import com.venach.kauris.paie.domain.ReglesPaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.SituationFamiliale;
import com.venach.kauris.paie.domain.TypeContrat;
import com.venach.kauris.paie.domain.TypeElement;
import com.venach.kauris.paie.domain.Variables;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le calcul des bulletins avec les règles recommandées, sur des cas comptés à la main. */
class CalculPaieTest {

    static final ReglesPaie.Contenu TOGO = ModelesRecommandes.pour(Pays.TOGO).orElseThrow().contenu();
    static final ReglesPaie.Contenu CI = ModelesRecommandes.pour(Pays.COTE_D_IVOIRE).orElseThrow().contenu();
    static final ReglesPaie.Contenu SN = ModelesRecommandes.pour(Pays.SENEGAL).orElseThrow().contenu();

    private static CalculPaie.Resultat calcul(ReglesPaie.Contenu r, long base, List<Element> e, Foyer foyer) {
        return CalculPaie.calculer(new CalculPaie.Entree(r, base, 30, List.of(), e, foyer, false, false, RegimeFiscal.NORMAL,
                List.of()));
    }

    private static CalculPaie.Resultat calcul(ReglesPaie.Contenu r, long base, RegimeFiscal regime, boolean expatrie) {
        return CalculPaie.calculer(new CalculPaie.Entree(r, base, 30, List.of(), List.of(), Foyer.seul(), expatrie,
                false, regime, List.of()));
    }

    private static Foyer foyer(SituationFamiliale s, int enfants, boolean conjoint) {
        return new Foyer(s, enfants, conjoint, 0);
    }

    // ================================================================== Togo

    @Test
    void togoSalarieSeul() {
        CalculPaie.Resultat r = calcul(TOGO, 300_000, List.of(), Foyer.seul());
        assertEquals(12_000, r.salarieDe("CNSS"));
        assertEquals(15_000, r.salarieDe("AMU"));
        // (300 000 − 27 000) × 12 = 3 276 000 ; − 28 % = 2 358 720 → 2 358 000 ; 1 458 000 × 3 % = 43 740 ; / 12.
        assertEquals(2_358_000, r.baseImpot());
        assertEquals(3_645, r.impot());
        assertEquals(269_355, r.netAPayer());
        assertEquals(37_500 + 9_000 + 6_000, r.employeurDe("CNSS"));
        assertEquals(15_000, r.employeurDe("AMU"));
        assertEquals(367_500, r.coutEmployeur());
        assertEquals("IRPP", r.impotNom());
    }

    @Test
    void togoPersonnesACharge() {
        CalculPaie.Resultat r = calcul(TOGO, 300_000, List.of(), foyer(SituationFamiliale.MARIE, 2, true));
        // 3 276 000 − 917 280 − 3 × 120 000 = 1 998 720 → 1 998 000 ; 1 098 000 × 3 % = 32 940 ; / 12 = 2 745.
        assertEquals(1_998_000, r.baseImpot());
        assertEquals(2_745, r.impot());
        CalculPaie.Resultat neuf = calcul(TOGO, 300_000, List.of(), new Foyer(SituationFamiliale.MARIE, 7, true, 1));
        assertEquals((3_276_000 - 917_280 - 720_000) / 1000 * 1000, neuf.baseImpot(), "six personnes au plus");
    }

    @Test
    void togoAbattementLimite() {
        CalculPaie.Resultat r = calcul(TOGO, 2_000_000, List.of(), Foyer.seul());
        // (2 000 000 − 180 000) × 12 = 21 840 000 ; abattement 28 % de 10 000 000 ; 19 040 000 imposable.
        assertEquals(19_040_000, r.baseImpot());
        assertEquals(3_375_000 / 12, r.impot());
    }

    @Test
    void togoPrimeExceptionnelleParDifference() {
        CalculPaie.Resultat r = calcul(TOGO, 300_000,
                List.of(new Element("Treizième mois", TypeElement.PRIME_EXCEPTIONNELLE, 120_000)), Foyer.seul());
        // Cotisations sur 420 000 : 16 800 + 21 000. Paie régulière : 3 645 par mois (cas ci-dessus).
        // Avec la prime : 3 276 000 + 120 000 − 10 800 = 3 385 200 ; − 28 % = 2 437 344 → 2 437 000 ;
        // 1 537 000 × 3 % = 46 110 ; différence 46 110 − 43 740 = 2 370, retenue en totalité ce mois-ci.
        assertEquals(37_800, r.cotisationsSalarie());
        assertEquals(3_645 + 2_370, r.impot());
        assertEquals(420_000 - 37_800 - 6_015, r.netAPayer());
        assertTrue(r.detailImpot().contains("2370 sur les primes exceptionnelles"), r.detailImpot());
    }

    @Test
    void togoRegimes() {
        assertEquals(0, calcul(TOGO, 300_000, RegimeFiscal.APPRENTI, false).impot(), "apprenti exonéré");
        // Gérant : 500 000 × 12 = 6 000 000, frais 20 % = 1 200 000, 4 800 000 imposable :
        // 63 000 + 1 800 000 × 10 % = 243 000 par an, 20 250 par mois.
        CalculPaie.Resultat g = calcul(TOGO, 500_000, RegimeFiscal.GERANT, false);
        assertEquals(4_800_000, g.baseImpot());
        assertEquals(20_250, g.impot());
        // Frais d'emploi : non soumis pour un salarié, imposables pour un dirigeant.
        List<Element> frais = List.of(new Element("Frais de représentation", TypeElement.FRAIS_EMPLOI, 50_000));
        CalculPaie.Resultat salarie = calcul(TOGO, 300_000, frais, Foyer.seul());
        assertEquals(300_000, salarie.brutSoumis());
        assertEquals(350_000, salarie.brut());
        CalculPaie.Resultat dirigeant = CalculPaie.calculer(new CalculPaie.Entree(TOGO, 300_000, 30, List.of(), frais,
                Foyer.seul(), false, false, RegimeFiscal.DIRIGEANT, List.of()));
        assertEquals(350_000, dirigeant.brutSoumis());
    }

    @Test
    void retraiteDeductiblePlafonnee() {
        // Une CNSS salariale à 8 % : seuls 6 % du brut se déduisent de l'impôt.
        List<ReglesPaie.Cotisation> c = new ArrayList<>(TOGO.cotisations());
        ReglesPaie.Cotisation p = c.get(0);
        c.set(0, new ReglesPaie.Cotisation(p.code(), p.libelle(), p.organisme(), new BigDecimal("8"),
                p.tauxEmployeur(), 0, 0, 0, 0, 0, p.deduction(), p.application(), true, p.compte(), ""));
        ReglesPaie.Contenu huit = new ReglesPaie.Contenu(c, TOGO.taxes(), TOGO.forfaits(), TOGO.impot(), TOGO.heures(), TOGO.comptes());
        // 500 000 − (30 000 + 25 000) = 445 000 × 12 = 5 340 000 ; − 1 495 200 = 3 844 800 → 3 844 000 ;
        // 63 000 + 84 400 = 147 400 ; / 12 = 12 283,33 → 12 283.
        assertEquals(12_283, calcul(huit, 500_000, List.of(), Foyer.seul()).impot());
    }

    @Test
    void togoPetitSalaireSansImpot() {
        CalculPaie.Resultat r = calcul(TOGO, 90_000, List.of(), Foyer.seul());
        assertEquals(0, r.impot());
        assertEquals(90_000 - 3_600 - 4_500, r.netAPayer());
    }

    // ================================================================== Côte d'Ivoire

    @Test
    void coteDIvoireMarieDeuxEnfants() {
        CalculPaie.Resultat r = calcul(CI, 500_000, List.of(), foyer(SituationFamiliale.MARIE, 2, false));
        assertEquals(31_500, r.salarieDe("CNPS"));
        assertEquals(38_500 + 3_500 + 525 + 1_400, r.employeurDe("CNPS"));
        assertEquals(2_000, r.salarieDe("CMU"));
        assertEquals(2_000, r.employeurDe("CMU"));
        // 165 000 × 16 % + 260 000 × 21 % = 81 000 ; 3 parts → 4 demi-parts × 5 500 = 22 000.
        assertEquals(59_000, r.impot());
        assertEquals(500_000 - 31_500 - 2_000 - 59_000, r.netAPayer());
        assertEquals(6_000, r.employeurDe("DGI"));
        assertEquals(2_000 + 3_000 + 3_000, r.employeurDe("FDFP"));
        assertEquals(List.of("CNPS", "CMU"), r.organismesSociaux());
    }

    @Test
    void coteDIvoirePlafondsEtExpatrie() {
        CalculPaie.Resultat r = calcul(CI, 4_000_000, RegimeFiscal.NORMAL, false);
        assertEquals(212_625, r.salarieDe("CNPS"), "retraite plafonnée à 3 375 000");
        assertEquals(976_000, r.impot());
        assertEquals(500, r.salarieDe("CMU"));
        assertEquals(48_000, r.employeurDe("DGI"), "1,2 % pour le personnel local");
        CalculPaie.Resultat expatrie = calcul(CI, 4_000_000, RegimeFiscal.NORMAL, true);
        assertEquals(416_000, expatrie.employeurDe("DGI"), "10,4 % pour un expatrié");
    }

    @Test
    void coteDIvoireAssietteMinimale() {
        // Temps partiel payé 60 000 : la retraite se calcule sur le SMIG (75 000).
        CalculPaie.Resultat r = calcul(CI, 60_000, List.of(), Foyer.seul());
        assertEquals(4_725, r.lignes().stream().filter(l -> l.code().equals("CNPS_RETRAITE")).findFirst()
                .orElseThrow().salarie());
        assertEquals(0, r.impot());
        CalculPaie.Resultat famille = calcul(CI, 100_000, List.of(), foyer(SituationFamiliale.MARIE, 4, false));
        assertEquals(0, famille.impot(), "la réduction ne rend jamais l'impôt négatif");
    }

    @Test
    void partsDeLaCoteDIvoire() {
        ReglesPaie.Parts p = CI.impot().parts();
        assertEquals(new BigDecimal("1"), p.de(Foyer.seul()));
        assertEquals(new BigDecimal("2"), p.de(foyer(SituationFamiliale.CELIBATAIRE, 1, false)));
        assertEquals(new BigDecimal("2.5"), p.de(foyer(SituationFamiliale.DIVORCE, 2, false)));
        assertEquals(new BigDecimal("2"), p.de(foyer(SituationFamiliale.MARIE, 0, false)));
        assertEquals(new BigDecimal("3"), p.de(foyer(SituationFamiliale.MARIE, 2, false)));
        assertEquals(new BigDecimal("2.5"), p.de(foyer(SituationFamiliale.VEUF, 1, false)));
        assertEquals(new BigDecimal("1"), p.de(foyer(SituationFamiliale.VEUF, 0, false)));
        assertEquals(new BigDecimal("5"), p.de(foyer(SituationFamiliale.MARIE, 9, false)));
        assertEquals(5, foyer(SituationFamiliale.MARIE, 3, true).personnesCouvertes());
    }

    // ================================================================== heures, éléments, avances

    @Test
    void heuresSupplementaires() {
        // 346 660 / 173,33 = 2 000 de l'heure ; 10 h à +15 % = 23 000 ; 4 h à +50 % = 12 000.
        CalculPaie.Resultat r = CalculPaie.calculer(new CalculPaie.Entree(CI, 346_660, 30, List.of(
                new Variables.Heures("41e à 46e heure", new BigDecimal("15"), new BigDecimal("10")),
                new Variables.Heures("Au-delà de la 46e heure", new BigDecimal("50"), new BigDecimal("4"))),
                List.of(), Foyer.seul(), false, false, RegimeFiscal.NORMAL, List.of()));
        assertEquals(346_660 + 23_000 + 12_000, r.brutSoumis());
    }

    @Test
    void elementsDePaie() {
        List<Element> e = List.of(new Element("Prime de rendement", TypeElement.GAIN_SOUMIS, 50_000),
                new Element("Logement", TypeElement.AVANTAGE_NATURE, 40_000),
                new Element("Transport", TypeElement.GAIN_NON_SOUMIS, 20_000),
                new Element("Cotisation syndicale", TypeElement.RETENUE, 3_000));
        CalculPaie.Resultat r = calcul(TOGO, 200_000, e, Foyer.seul());
        assertEquals(290_000, r.brutSoumis());
        assertEquals(310_000, r.brut());
        assertEquals(26_100, r.cotisationsSalarie());
        assertEquals(310_000 - 40_000 - 26_100 - r.impot() - 3_000, r.netAPayer());
        ErreurMetier x = assertThrows(ErreurMetier.class, () -> calcul(TOGO, 100_000,
                List.of(new Element("Saisie sur salaire", TypeElement.RETENUE, 200_000)), Foyer.seul()));
        assertTrue(x.getMessage().contains("négatif"));
    }

    @Test
    void avancesPlafonneesAuNet() {
        // 100 000 : cotisations 9 000, pas d'impôt ; net disponible 91 000. L'avance de 120 000 n'est retenue
        // qu'à hauteur de 91 000 : le net vaut 0, le reste est reporté.
        CalculPaie.Resultat r = CalculPaie.calculer(new CalculPaie.Entree(TOGO, 100_000, 30, List.of(), List.of(),
                Foyer.seul(), false, false, RegimeFiscal.NORMAL, List.of(new CalculPaie.AvanceDue("AV:1", "Prêt moto",
                120_000))));
        assertEquals(0, r.netAPayer());
        assertEquals(91_000, r.rembourse("AV:1"));
        assertTrue(r.lignes().stream().anyMatch(l -> l.libelle().contains("29000 reportés")));
    }

    @Test
    void prorataEtJoursDuContrat() {
        assertEquals(150_000, CalculPaie.prorata(300_000, 15));
        ContratTravail c = ContratTravail.de(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CTR-1",
                new ContratTravail.Saisie(TypeContrat.CDI, "Vendeuse", "", LocalDate.of(2026, 11, 16), null, 150_000,
                        List.of(), ""), Instant.EPOCH);
        assertEquals(0, c.joursDans(YearMonth.of(2026, 10)));
        assertEquals(15, c.joursDans(YearMonth.of(2026, 11)));
        assertEquals(30, c.joursDans(YearMonth.of(2026, 12)));
        ContratTravail fini = c.terminer(LocalDate.of(2027, 2, 10), "Démission");
        assertEquals(10, fini.joursDans(YearMonth.of(2027, 2)));
        assertThrows(ErreurMetier.class, () -> ContratTravail.de(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "CTR-2", new ContratTravail.Saisie(TypeContrat.CDI, "Caissier", "",
                        LocalDate.of(2026, 11, 1), null, 100_000, List.of(new Element("Prime de fin d'année",
                        TypeElement.PRIME_EXCEPTIONNELLE, 10_000)), ""), Instant.EPOCH), "pas de prime exceptionnelle "
                + "dans un contrat");
    }

    // ================================================================== règles et écritures

    @Test
    void reglesInvalides() {
        assertThrows(ErreurMetier.class, () -> new ReglesPaie.Impot("IRPP", "OTR", ReglesPaie.Periodicite.ANNUELLE,
                List.of(new ReglesPaie.Tranche(900_000, BigDecimal.ZERO), new ReglesPaie.Tranche(500_000,
                        BigDecimal.ONE), new ReglesPaie.Tranche(0, BigDecimal.TEN)), true, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, ReglesPaie.Famille.AUCUNE, 0, 0, 0, null, null, null, 1, 1, null, false, false,
                BigDecimal.ZERO, 0, "", ""), "limites décroissantes");
        assertThrows(ErreurMetier.class, () -> new ReglesPaie.Impot("IRPP", "OTR", ReglesPaie.Periodicite.ANNUELLE,
                List.of(new ReglesPaie.Tranche(900_000, BigDecimal.ZERO)), true, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                ReglesPaie.Famille.AUCUNE, 0, 0, 0, null, null, null, 1, 1, null, false, false, BigDecimal.ZERO, 0, "", ""),
                "la dernière tranche est sans limite");
        assertThrows(ErreurMetier.class, () -> new ReglesPaie.Tranche(100, new BigDecimal("140")));
        assertThrows(ErreurMetier.class, () -> new ReglesPaie.Contenu(List.of(TOGO.cotisations().get(0),
                TOGO.cotisations().get(0)), List.of(), List.of(), TOGO.impot(), null, null), "codes en double");
        assertThrows(ErreurMetier.class, () -> new ReglesPaie(UUID.randomUUID(), UUID.randomUUID(), Pays.TOGO,
                LocalDate.of(2027, 1, 15), "Togo", "", TOGO, "", Instant.EPOCH, ""), "premier jour du mois");
        ReglesPaie.Contenu vide = ModelesRecommandes.vide(Pays.SENEGAL, LocalDate.of(2027, 3, 12)).contenu();
        assertEquals(0, calcul(vide, 300_000, List.of(), Foyer.seul()).impot());
    }

    // ================================================================== Sénégal

    private static CalculPaie.Resultat senegal(long base, Foyer foyer, boolean cadre, ReglesPaie.Contenu r) {
        return CalculPaie.calculer(new CalculPaie.Entree(r, base, 30, List.of(), List.of(), foyer, false, cadre,
                RegimeFiscal.NORMAL, List.of()));
    }

    @Test
    void senegalCadreMarieDeuxEnfants() {
        // Cadre à 1 000 000, marié, épouse sans revenu, 2 enfants : 3 parts.
        CalculPaie.Resultat r = senegal(1_000_000, foyer(SituationFamiliale.MARIE, 2, true), true, SN);
        // IPRES RG 432 000 × 5,6 % = 24 192 ; RCC 1 000 000 × 2,4 % = 24 000 ; IPM 250 000 × 3 % = 7 500.
        assertEquals(24_192 + 24_000 + 7_500, r.cotisationsSalarie());
        assertEquals(36_288, r.employeurDe("IPRES") - 36_000);
        assertEquals(4_410 + 630, r.employeurDe("CSS"));
        // IR : 12 000 000 − 900 000 (30 %, au plus 900 000) = 11 100 000 ; barème 3 471 000 ;
        // réduction 25 % (entre 400 000 et 1 650 000) = 867 750 ; 2 603 250 / 12 = 216 937,5 → 216 938.
        assertEquals(11_100_000, r.baseImpot());
        assertEquals(216_938, r.lignes().stream().filter(l -> l.code().equals("IR")).findFirst().orElseThrow()
                .salarie());
        // TRIMF : 12 000 000 → 18 000 par an, 1 500 par mois, pour lui et son épouse.
        assertEquals(3_000, r.lignes().stream().filter(l -> l.code().equals("TRIMF")).findFirst().orElseThrow()
                .salarie());
        assertEquals(219_938, r.impot());
        assertEquals(1_000_000 - 55_692 - 219_938, r.netAPayer());
        assertEquals(30_000, r.employeurDe("DGID"), "CFCE 3 %");
        assertEquals(36_288 + 36_000 + 4_410 + 630 + 7_500 + 30_000, r.chargesEmployeur());
    }

    @Test
    void senegalNonCadreEtReductionMinimale() {
        // Célibataire, 150 000, non cadre : pas de retraite des cadres, pas de réduction (1 part).
        CalculPaie.Resultat seul = senegal(150_000, Foyer.seul(), false, SN);
        assertTrue(seul.lignes().stream().noneMatch(l -> l.code().equals("IPRES_RCC")));
        assertEquals(8_400 + 4_500, seul.cotisationsSalarie());
        // 1 800 000 − 540 000 = 1 260 000 ; (1 260 000 − 630 000) × 20 % = 126 000 ; / 12 = 10 500.
        // TRIMF : 1 800 000 → 4 800 par an, 400 par mois.
        assertEquals(10_500 + 400, seul.impot());
        assertEquals(150_000 - 12_900 - 10_900, seul.netAPayer());
        // Le même salaire avec 3 parts : la réduction minimale (400 000) dépasse l'impôt, qui tombe à zéro.
        CalculPaie.Resultat famille = senegal(150_000, foyer(SituationFamiliale.MARIE, 2, true), false, SN);
        assertEquals(800, famille.impot(), "seule la TRIMF reste, pour lui et son épouse");
    }

    @Test
    void partsEtPlafondDeLImpot() {
        ReglesPaie.Impot i = SN.impot();
        assertEquals(0, new BigDecimal("1.5").compareTo(i.parts().de(foyer(SituationFamiliale.MARIE, 0, false))));
        assertEquals(0, new BigDecimal("2").compareTo(i.parts().de(foyer(SituationFamiliale.MARIE, 0, true))));
        assertEquals(0, new BigDecimal("2").compareTo(i.parts().de(foyer(SituationFamiliale.DIVORCE, 2, false))));
        assertEquals(0, new BigDecimal("2").compareTo(i.parts().de(foyer(SituationFamiliale.VEUF, 1, false))));
        assertEquals(0, new BigDecimal("5").compareTo(i.parts().de(foyer(SituationFamiliale.MARIE, 9, true))));
        assertTrue(i.reductionPour(BigDecimal.ONE).isEmpty());
        assertEquals(0, new BigDecimal("45").compareTo(i.reductionPour(new BigDecimal("5")).orElseThrow().taux()));
        // Un plafond à 5 % du revenu imposable : 11 100 000 × 5 % = 555 000 par an, 46 250 par mois.
        ReglesPaie.Impot plafonne = new ReglesPaie.Impot(i.nom(), i.organisme(), i.periodicite(), i.tranches(),
                i.deduireCotisations(), i.retraiteDeductibleMax(), i.abattement(), i.abattementLimite(), i.famille(),
                i.deductionParPersonne(), i.personnesMax(), i.reductionParDemiPart(), i.parts(), i.reductions(),
                new BigDecimal("5"), i.arrondiRevenu(), i.arrondiImpot(), i.arrondiMensuel(),
                i.primesDifferentielles(), i.apprentisExoneres(), i.gerantAbattement(), i.gerantAbattementLimite(),
                i.compte(), i.note());
        ReglesPaie.Contenu c = new ReglesPaie.Contenu(SN.cotisations(), SN.taxes(), SN.forfaits(), plafonne,
                SN.heures(), SN.comptes());
        CalculPaie.Resultat r = senegal(1_000_000, foyer(SituationFamiliale.MARIE, 2, true), true, c);
        assertEquals(46_250 + 3_000, r.impot());
        // Paliers invalides : la dernière limite doit être vide.
        assertThrows(ErreurMetier.class, () -> new ReglesPaie.Forfait("TRIMF", "TRIMF", "DGID", List.of(
                new ReglesPaie.Palier(600_000, 900), new ReglesPaie.Palier(1_000_000, 3_600)), true, "", ""));
        assertThrows(ErreurMetier.class, () -> new ReglesPaie.Reduction(new BigDecimal("2"), BigDecimal.TEN,
                500_000, 100_000), "minimum au-dessus du maximum");
    }

    @Test
    void ecrituresEquilibrees() {
        for (ReglesPaie.Contenu r : List.of(TOGO, CI, SN)) {
            CalculPaie.Resultat res = CalculPaie.calculer(new CalculPaie.Entree(r, 400_000, 30, List.of(), List.of(
                    new Element("Prime", TypeElement.GAIN_SOUMIS, 30_000),
                    new Element("Logement", TypeElement.AVANTAGE_NATURE, 50_000),
                    new Element("Transport", TypeElement.GAIN_NON_SOUMIS, 25_000),
                    new Element("Syndicat", TypeElement.RETENUE, 2_000)), foyer(SituationFamiliale.MARIE, 1, true),
                    false, false, RegimeFiscal.NORMAL, List.of(new CalculPaie.AvanceDue("AV:x", "Avance", 20_000))));
            Salarie s = Salarie.de(UUID.randomUUID(), UUID.randomUUID(), "S-0001", new Salarie.Saisie("Mensah", "",
                    null, "", "", "", "", "", SituationFamiliale.MARIE, 1, true, 0, false, false, RegimeFiscal.NORMAL,
                    ModePaiement.ESPECES, "", "", true, ""), Instant.EPOCH);
            ContratTravail c = ContratTravail.de(UUID.randomUUID(), s.tenantId(), s.id(), "CTR-1",
                    new ContratTravail.Saisie(TypeContrat.CDI, "Comptable", "", LocalDate.of(2025, 1, 1), null,
                            400_000, List.of(), ""), Instant.EPOCH);
            ReglesPaie regles = new ReglesPaie(UUID.randomUUID(), s.tenantId(), Pays.TOGO, LocalDate.of(2026, 1, 1),
                    "Règles", "", r, "", Instant.EPOCH, "");
            Bulletin b = Bulletin.brouillon(s.tenantId(), UUID.randomUUID(), s, c, 30, regles, res);
            PeriodePaie p = PeriodePaie.ouvrir(s.tenantId(), YearMonth.of(2026, 11), Pays.TOGO, Instant.EPOCH);
            List<EcrituresPaie.Ligne> l = EcrituresPaie.de(p, List.of(b));
            long debit = l.stream().mapToLong(EcrituresPaie.Ligne::debit).sum();
            long credit = l.stream().mapToLong(EcrituresPaie.Ligne::credit).sum();
            assertEquals(debit, credit, "écriture équilibrée");
            assertTrue(l.stream().anyMatch(x -> x.compte().equals("422000") && x.credit() == res.netAPayer()));
            assertTrue(l.stream().noneMatch(x -> x.libelle().contains("Logement")), "avantage en nature hors paie");
            assertTrue(!b.famille().isEmpty());
        }
    }
}
