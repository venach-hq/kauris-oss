package com.venach.kauris.paie;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.application.PortsPaie;
import com.venach.kauris.paie.domain.Avance;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Element;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La paie de bout en bout, en mémoire : règles, salariés, contrats, avances, mois, clôture, droits. */
class PaieServiceTest {

    /** Mercredi 18 novembre 2026. */
    private static final YearMonth NOVEMBRE = YearMonth.of(2026, 11);

    private final PaieEnMemoire s = new PaieEnMemoire();
    private final AtomicReference<Pays> pays = new AtomicReference<>(Pays.TOGO);
    private final List<String> verses = new ArrayList<>();
    private final PortsPaie.Versements versements = new PortsPaie.Versements() {
        @Override
        public boolean disponible(UUID tenantId) {
            return true;
        }

        @Override
        public String verser(Acces a, PeriodePaie p, List<Bulletin> b) {
            b.forEach(x -> verses.add(x.nomSalarie() + " " + x.net()));
            return b.size() + " salaire(s) envoyé(s)";
        }
    };
    private final PaieService paie = new PaieService(s, s, s, s, s, s, s, s, t -> Optional.ofNullable(pays.get()),
            versements, UniteDeTravail.DIRECTE, Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Boulangerie du Port", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));
    private final Acces gestionnaire = new Acces(tenant, "Boulangerie du Port", true, UUID.randomUUID(), "Kodjo",
            "kodjo@exemple.test", Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));

    private static Salarie.Saisie fiche(String nom, SituationFamiliale situation, int enfants, ModePaiement mode,
                                        String compte) {
        return new Salarie.Saisie(nom, "Akosua", LocalDate.of(1990, 3, 4), "+228 90 00 00 01", "", "Lomé", "",
                "CNSS-" + nom, situation, enfants, false, 0, false, false, RegimeFiscal.NORMAL, mode, "Ecobank", compte, true,
                "");
    }

    private static ContratTravail.Saisie cdi(String poste, long salaire, LocalDate debut, Element... elements) {
        return new ContratTravail.Saisie(TypeContrat.CDI, poste, "", debut, null, salaire, List.of(elements), "");
    }

    private ReglesPaie activerTogo() {
        return paie.activerModele(patron, ModelesRecommandes.TOGO_2026, null, null);
    }

    @Test
    void reglesActiveesCorrigeesEtVersionnees() {
        assertTrue(paie.alertes(patron).sansRegles());
        assertThrows(ErreurMetier.class, () -> paie.ouvrirMois(patron, NOVEMBRE), "pas de règles, pas de paie");
        assertThrows(AccesRefuse.class, () -> paie.activerModele(gestionnaire, ModelesRecommandes.TOGO_2026, null,
                null));
        ReglesPaie r = activerTogo();
        assertEquals(LocalDate.of(2026, 1, 1), r.depuis());
        assertEquals("Awa", r.modifiePar());
        assertFalse(paie.alertes(patron).sansRegles());
        assertThrows(ErreurMetier.class, this::activerTogo, "une seule version par date");

        // L'entreprise corrige son taux de risques professionnels : rien n'est figé.
        List<ReglesPaie.Cotisation> c = new ArrayList<>(r.contenu().cotisations());
        ReglesPaie.Cotisation risques = c.get(2);
        c.set(2, new ReglesPaie.Cotisation(risques.code(), risques.libelle(), risques.organisme(),
                risques.tauxSalarie(), new BigDecimal("3.5"), 0, 0, 0, 0, 0, risques.deduction(),
                risques.application(), true, risques.compte(), "Notification CNSS du 3 mars"));
        ReglesPaie.Contenu x = r.contenu();
        paie.enregistrerRegles(patron, r.id(), "Togo — nos règles", null, new ReglesPaie.Contenu(c, x.taxes(),
                x.forfaits(), x.impot(), x.heures(), x.comptes()), "");
        assertEquals(new BigDecimal("3.5"), paie.reglesDuMois(tenant, Pays.TOGO, NOVEMBRE).orElseThrow().contenu()
                .cotisations().get(2).tauxEmployeur());
        assertEquals(ModelesRecommandes.TOGO_2026, paie.recommandation(paie.reglesParId(patron, r.id()))
                .orElseThrow().code(), "on garde la recommandation pour comparer");

        // Une loi de finances : nouvelle version au 1er janvier 2027 ; novembre garde l'ancienne.
        ReglesPaie v2027 = paie.nouvelleVersion(patron, r.id(), LocalDate.of(2027, 1, 15));
        assertEquals(LocalDate.of(2027, 1, 1), v2027.depuis());
        assertEquals(r.id(), paie.reglesDuMois(tenant, Pays.TOGO, NOVEMBRE).orElseThrow().id());
        assertEquals(v2027.id(), paie.reglesDuMois(tenant, Pays.TOGO, YearMonth.of(2027, 2)).orElseThrow().id());
        assertEquals(2, paie.regles(patron).size());

        // Un mois ouvert garde toujours des règles.
        paie.ouvrirMois(patron, NOVEMBRE);
        assertThrows(ErreurMetier.class, () -> paie.supprimerRegles(patron, r.id()));
        paie.supprimerRegles(patron, v2027.id());
        assertEquals(1, paie.regles(patron).size());
    }

    @Test
    void paysSansModele() {
        pays.set(Pays.BENIN);
        assertTrue(ModelesRecommandes.pour(Pays.BENIN).isEmpty());
        ReglesPaie r = paie.activerModele(patron, ModelesRecommandes.VIDE, null, LocalDate.of(2026, 11, 20));
        assertEquals(Pays.BENIN, r.pays());
        assertEquals(LocalDate.of(2026, 11, 1), r.depuis());
        // L'entreprise saisit son barème et une cotisation : le calcul les suit.
        ReglesPaie.Impot i = r.contenu().impot();
        ReglesPaie.Impot irpp = new ReglesPaie.Impot("IR", "DGID", ReglesPaie.Periodicite.MENSUELLE, List.of(
                new ReglesPaie.Tranche(100_000, BigDecimal.ZERO), new ReglesPaie.Tranche(0, new BigDecimal("20"))),
                false, BigDecimal.ZERO, BigDecimal.ZERO, 0, ReglesPaie.Famille.AUCUNE, 0, 0, 0, null, null, null, 1, 1, null,
                false, false, BigDecimal.ZERO, 0, i.compte(), "");
        ReglesPaie.Cotisation ipres = new ReglesPaie.Cotisation("IPRES", "IPRES régime général", "IPRES",
                new BigDecimal("5.6"), new BigDecimal("8.4"), 432_000, 0, 0, 0, 0, ReglesPaie.Deduction.AUCUNE,
                ReglesPaie.Application.TOUS, true, "431200", "");
        paie.enregistrerRegles(patron, r.id(), r.nom(), null, new ReglesPaie.Contenu(List.of(ipres), List.of(), List.of(), irpp,
                null, null), "Saisi avec notre comptable");
        Salarie a = paie.enregistrerSalarie(patron, null, fiche("Diop", SituationFamiliale.CELIBATAIRE, 0,
                ModePaiement.ESPECES, ""));
        paie.creerContrat(patron, a.id(), cdi("Agent", 300_000, LocalDate.of(2026, 1, 1)));
        PeriodePaie p = paie.ouvrirMois(patron, NOVEMBRE);
        Bulletin b = paie.bulletinDe(patron, p.id(), a.id());
        assertEquals(16_800, b.resultat().salarieDe("IPRES"));
        assertEquals(40_000, b.resultat().impot(), "200 000 × 20 %");
        assertEquals("IR", b.resultat().impotNom());
    }

    /** Une comptabilité qui note ce qu'elle reçoit. */
    static final class Recue implements com.venach.kauris.kernel.comptabilite.Comptabilisation {
        final List<com.venach.kauris.kernel.comptabilite.OperationComptable> operations = new java.util.ArrayList<>();
        final List<String> annulations = new java.util.ArrayList<>();

        @Override
        public void enregistrer(com.venach.kauris.kernel.comptabilite.OperationComptable o) {
            operations.add(o);
        }

        @Override
        public void annuler(UUID tenantId, String origine, String reference, LocalDate date, String motif) {
            annulations.add(reference);
        }
    }

    @Test
    void moisDePaieComplet() {
        Recue compta = new Recue();
        paie.brancherComptabilite(compta);
        activerTogo();
        Salarie awa = paie.enregistrerSalarie(patron, null, fiche("Mensah", SituationFamiliale.CELIBATAIRE, 0,
                ModePaiement.VIREMENT, "TG001 0000 1234"));
        Salarie kofi = paie.enregistrerSalarie(patron, null, fiche("Agbeko", SituationFamiliale.MARIE, 2,
                ModePaiement.ESPECES, ""));
        assertEquals("S-0001", awa.matricule());
        paie.creerContrat(patron, awa.id(), cdi("Comptable", 300_000, LocalDate.of(2025, 1, 1)));
        ContratTravail ck = paie.creerContrat(patron, kofi.id(), cdi("Vendeur", 120_000, LocalDate.of(2026, 11, 16),
                new Element("Transport", TypeElement.GAIN_NON_SOUMIS, 10_000)));
        assertEquals("CTR-2026-0001", ck.numero());
        assertThrows(ErreurMetier.class, () -> paie.creerContrat(patron, awa.id(), cdi("Directrice", 500_000,
                LocalDate.of(2026, 12, 1))), "un seul contrat à la fois");

        PeriodePaie p = paie.ouvrirMois(patron, NOVEMBRE);
        assertThrows(ErreurMetier.class, () -> paie.ouvrirMois(patron, NOVEMBRE));
        assertThrows(ErreurMetier.class, () -> paie.ouvrirMois(patron, YearMonth.of(2027, 2)), "trop loin");
        PaieService.Mois m = paie.mois(patron, p.id());
        assertEquals(2, m.bulletins().size());
        assertEquals("Togo — règles recommandées 2026", m.regles().nom());
        Bulletin bAwa = paie.bulletinDe(patron, p.id(), awa.id());
        assertEquals(269_355, bAwa.net());
        assertEquals("0 personne(s) à charge", bAwa.famille());
        Bulletin bKofi = paie.bulletinDe(patron, p.id(), kofi.id());
        assertEquals(15, bKofi.joursPayes());
        assertEquals(60_000 + 10_000, bKofi.resultat().brut());

        // Éléments du mois : 2 jours d'absence, 5 heures supplémentaires, une cotisation syndicale.
        paie.enregistrerVariables(patron, p.id(), awa.id(), 2, List.of(new Variables.Heures("Heures de jour",
                new BigDecimal("15"), new BigDecimal("5"))), List.of(new Element("Syndicat", TypeElement.RETENUE,
                1_000)));
        bAwa = paie.bulletinDe(patron, p.id(), awa.id());
        assertEquals(28, bAwa.joursPayes());
        // 280 000 + 300 000 / 173,33 × 1,15 × 5 = 9 952 (arrondi).
        assertEquals(280_000 + 9_952, bAwa.resultat().brut());
        assertEquals(1_000, bAwa.resultat().retenues());
        assertThrows(ErreurMetier.class, () -> paie.enregistrerVariables(patron, p.id(), kofi.id(), 0, List.of(),
                List.of(new Element("Saisie", TypeElement.RETENUE, 500_000))), "net négatif refusé à la saisie");

        // Droits : un gestionnaire n'a pas accès aux salaires.
        assertThrows(AccesRefuse.class, () -> paie.mois(gestionnaire, p.id()));
        assertThrows(AccesRefuse.class, () -> paie.salaries(gestionnaire));

        // Clôture : bulletins numérotés et figés, même si les règles changent ensuite.
        paie.clore(patron, p.id());
        List<Bulletin> figes = paie.mois(patron, p.id()).bulletins();
        assertEquals(List.of("BP-202611-0001", "BP-202611-0002"), figes.stream().map(Bulletin::numero).toList());
        // La comptabilité reçoit un bulletin par salarié : comptes de la paie, net à payer au nom du salarié.
        assertEquals(2, compta.operations.size());
        var op = compta.operations.stream().filter(o -> o.piece().equals(figes.get(1).numero())).findFirst()
                .orElseThrow();
        assertEquals(com.venach.kauris.kernel.comptabilite.OperationComptable.Journal.PAIE, op.journal());
        assertEquals(LocalDate.of(2026, 11, 30), op.date());
        var net = op.lignes().stream().filter(l -> l.compte().equals("422000")).findFirst().orElseThrow();
        assertEquals(figes.get(1).net(), net.credit());
        assertEquals("SAL:" + figes.get(1).salarieId(), net.tiers());
        long netAvant = paie.bulletinDe(patron, p.id(), awa.id()).net();
        ReglesPaie r = paie.regles(patron).get(0);
        ReglesPaie.Contenu x = r.contenu();
        paie.enregistrerRegles(patron, r.id(), r.nom(), null, new ReglesPaie.Contenu(List.of(), x.taxes(), x.forfaits(), x.impot(),
                x.heures(), x.comptes()), "");
        assertEquals(netAvant, paie.bulletinDe(patron, p.id(), awa.id()).net(), "le bulletin clos ne bouge pas");
        assertThrows(ErreurMetier.class, () -> paie.terminerContrat(patron, ck.id(), LocalDate.of(2026, 11, 30),
                "Fin"), "mois clos");

        assertEquals("1 salaire(s) envoyé(s)", paie.verser(patron, p.id()));
        assertEquals(List.of("MENSAH Akosua " + netAvant), verses);

        paie.rouvrir(patron, p.id());
        assertTrue(s.bulletins.isEmpty());
        assertEquals(figes.stream().map(b -> "BULLETIN:" + b.id()).sorted().toList(),
                compta.annulations.stream().sorted().toList(), "les bulletins retirés sont annulés en comptabilité");
        assertTrue(paie.bulletinDe(patron, p.id(), awa.id()).net() > netAvant, "sans cotisations, le net monte");
        paie.clore(patron, p.id());
        assertEquals("BP-202611-0004", paie.bulletinDe(patron, p.id(), awa.id()).numero(),
                "numéros jamais réutilisés, par ordre alphabétique (AGBEKO, puis MENSAH)");
        paie.marquerPaye(patron, p.id(), LocalDate.of(2026, 11, 18));
        assertThrows(ErreurMetier.class, () -> paie.rouvrir(patron, p.id()));
        // Puis les paiements : le net soldé contre la trésorerie, selon le mode de chaque salarié.
        var paiements = compta.operations.stream().filter(o -> o.reference().startsWith("PAIEMENT:")).toList();
        assertEquals(2, paiements.size());
        assertEquals(java.util.Set.of("VIREMENT", "ESPECES"), paiements.stream().map(o -> o.lignes().get(1).precision())
                .collect(java.util.stream.Collectors.toSet()));
        Bulletin final1 = paie.bulletinDe(patron, p.id(), awa.id());
        assertEquals(final1.net(), paiements.stream().filter(o -> o.piece().equals(final1.numero())).findFirst()
                .orElseThrow().montant());
    }

    @Test
    void avancesSurPlusieursMois() {
        activerTogo();
        Salarie a = paie.enregistrerSalarie(patron, null, fiche("Amouzou", SituationFamiliale.CELIBATAIRE, 0,
                ModePaiement.ESPECES, ""));
        paie.creerContrat(patron, a.id(), cdi("Magasinier", 100_000, LocalDate.of(2026, 1, 1)));
        // Prêt de 150 000, 80 000 par mois à partir de novembre ; net disponible 91 000 par mois.
        Avance pret = paie.accorderAvance(patron, a.id(), Avance.Type.PRET, "Moto", 150_000, 80_000, NOVEMBRE,
                "Accord écrit du 2 novembre");
        PeriodePaie nov = paie.ouvrirMois(patron, NOVEMBRE);
        assertEquals(80_000, paie.bulletinDe(patron, nov.id(), a.id()).resultat().rembourse(pret.code()));
        PeriodePaie dec = paie.ouvrirMois(patron, NOVEMBRE.plusMonths(1));
        assertThrows(ErreurMetier.class, () -> paie.clore(patron, dec.id()), "novembre d'abord");
        paie.clore(patron, nov.id());
        assertEquals(70_000, paie.avances(patron, a.id()).get(0).reste());
        assertEquals(70_000, paie.bulletinDe(patron, dec.id(), a.id()).resultat().rembourse(pret.code()),
                "il ne reste que 70 000");
        paie.clore(patron, dec.id());
        assertEquals(0, paie.avances(patron, a.id()).get(0).reste());
        assertThrows(ErreurMetier.class, () -> paie.rouvrir(patron, nov.id()), "un mois suivant est clos");
        // Une avance accordée sur un mois clos est refusée.
        assertThrows(ErreurMetier.class, () -> paie.accorderAvance(patron, a.id(), Avance.Type.AVANCE, "Fête",
                20_000, 20_000, NOVEMBRE, ""));
        Avance annulee = paie.annulerAvance(patron, pret.id());
        assertTrue(annulee.annulee());
    }

    @Test
    void fichesInvalides() {
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, null, fiche("X",
                SituationFamiliale.CELIBATAIRE, 0, ModePaiement.ESPECES, "")), "nom trop court");
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, null, fiche("Amouzou",
                SituationFamiliale.CELIBATAIRE, 0, ModePaiement.VIREMENT, "")), "virement sans compte");
        Salarie s1 = paie.enregistrerSalarie(patron, null, fiche("Amouzou", SituationFamiliale.CELIBATAIRE, 0,
                ModePaiement.ESPECES, ""));
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, null, fiche("Amouzou",
                SituationFamiliale.CELIBATAIRE, 0, ModePaiement.ESPECES, "")), "même numéro CNSS");
        ContratTravail c = paie.creerContrat(patron, s1.id(), cdi("Gardien", 90_000, LocalDate.of(2026, 1, 1)));
        Salarie.Saisie archive = new Salarie.Saisie("Amouzou", "", null, "", "", "", "", "",
                SituationFamiliale.CELIBATAIRE, 0, false, 0, false, false, RegimeFiscal.NORMAL, ModePaiement.ESPECES, "", "",
                false, "");
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, s1.id(), archive));
        paie.terminerContrat(patron, c.id(), LocalDate.of(2026, 11, 10), "Démission");
        assertFalse(paie.enregistrerSalarie(patron, s1.id(), archive).actif());
    }
}
