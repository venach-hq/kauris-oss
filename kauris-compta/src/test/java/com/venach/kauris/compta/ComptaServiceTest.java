package com.venach.kauris.compta;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.compta.application.PortsCompta;
import com.venach.kauris.compta.domain.Balance;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.compta.domain.EtatTva;
import com.venach.kauris.compta.domain.EtatsFinanciers;
import com.venach.kauris.compta.domain.Imputation;
import com.venach.kauris.compta.domain.LigneEcriture;
import com.venach.kauris.compta.domain.OperationRecue;
import com.venach.kauris.compta.domain.Parametres;
import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Ligne;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Role;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComptaServiceTest {

    /** Une horloge qu'on avance. */
    static final class Horloge extends Clock {
        Instant maintenant = Instant.parse("2026-11-18T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    private final ComptaEnMemoire s = new ComptaEnMemoire();
    private final Horloge horloge = new Horloge();
    private final ComptaService compta = new ComptaService(s, s, s, s, s, s, s, s, UniteDeTravail.DIRECTE, horloge);
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Quincaillerie du Marché", true, UUID.randomUUID(), "Afi",
            "afi@exemple.test", Set.of(AttributionRole.pourTouteLOrganisation(
            com.venach.kauris.kernel.organisation.domain.Role.PROPRIETAIRE)));
    private final Acces gestionnaire = new Acces(tenant, "Quincaillerie du Marché", true, UUID.randomUUID(), "Kossi",
            "kossi@exemple.test", Set.of(AttributionRole.pourTouteLOrganisation(
            com.venach.kauris.kernel.organisation.domain.Role.GESTIONNAIRE)));

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 10);

    private OperationComptable facture(String ref, long ht, long tva) {
        return new OperationComptable(tenant, "ventes", "FACTURE:" + ref, JOUR, OperationComptable.Journal.VENTES,
                "FA-2026-" + ref, "Facture FA-2026-" + ref + " Ets Lawson", List.of(
                Ligne.debit(Role.CLIENT, "", ht + tva).pour("CLI:1", "Ets Lawson"),
                Ligne.credit(Role.VENTE_MARCHANDISES, "", ht),
                Ligne.credit(Role.TVA_COLLECTEE, "18", tva)));
    }

    private OperationComptable reglement(String ref, String piece, long montant, String mode, LocalDate date) {
        return new OperationComptable(tenant, "ventes", "REGLEMENT:" + ref, date, OperationComptable.Journal.TRESORERIE,
                piece, "Règlement " + piece, List.of(
                Ligne.debit(Role.TRESORERIE, mode, montant),
                Ligne.credit(Role.CLIENT, "", montant).pour("CLI:1", "Ets Lawson")));
    }

    private OperationComptable achat() {
        return new OperationComptable(tenant, "achats", "FACTURE:F1", JOUR, OperationComptable.Journal.ACHATS,
                "FF-2026-0001", "Facture fournisseur Sotoco", List.of(
                Ligne.debit(Role.ACHAT_MARCHANDISES, "", 500_000),
                Ligne.debit(Role.TVA_DEDUCTIBLE, "18", 90_000),
                Ligne.credit(Role.FOURNISSEUR, "", 590_000).pour("FRS:9", "Sotoco")));
    }

    private Ecriture ecritureDe(String origine, String reference) {
        OperationRecue o = s.operation(tenant, origine, reference).orElseThrow();
        assertEquals(OperationRecue.Statut.ECRITE, o.statut(), o.message());
        return s.ecriture(tenant, o.ecritureId()).orElseThrow();
    }

    @Test
    void operationsRecuesEtatsEtTva() {
        compta.recevoir(facture("0001", 1_000_000, 180_000));
        compta.recevoir(facture("0001", 1_000_000, 180_000));   // reçue deux fois : une seule écriture
        compta.recevoir(reglement("R1", "FA-2026-0001", 1_180_000, "ESPECES", JOUR));
        compta.recevoir(achat());
        assertEquals(3, s.ecritures.size());

        Ecriture f = ecritureDe("ventes", "FACTURE:0001");
        assertEquals("VE", f.journal());
        assertFalse(f.validee(), "au brouillard par défaut");
        assertEquals(List.of("411000", "701000", "443100"), f.lignes().stream().map(LigneEcriture::compte).toList());
        assertEquals("CLI:1", f.lignes().get(0).tiers());
        assertEquals("571000", ecritureDe("ventes", "REGLEMENT:R1").lignes().get(0).compte(), "espèces → caisse");

        // Validation : numéros sans trou par journal.
        assertEquals(3, compta.validerBrouillard(patron, JOUR));
        assertEquals("VE-2026-00001", ecritureDe("ventes", "FACTURE:0001").numero());
        assertEquals("TR-2026-00001", ecritureDe("ventes", "REGLEMENT:R1").numero());

        // Lettrage automatique : la facture et son règlement (même pièce).
        assertEquals(1, compta.lettrerAutomatiquement(patron, "411000"));
        assertTrue(compta.lignesALettrer(patron, "411000", false).isEmpty());

        Balance b = compta.balance(patron, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), false);
        assertTrue(b.equilibree());
        assertEquals(1_180_000L, (long) b.soldes().get("571000"));
        assertEquals(0L, (long) b.soldes().get("411000"));

        // TVA de novembre : 180 000 − 90 000 = 90 000 à payer.
        EtatTva t = compta.tva(patron, YearMonth.of(2026, 11));
        assertEquals(1_000_000, t.chiffreAffaires());
        assertEquals(180_000, t.totalCollectee());
        assertEquals(90_000, t.totalDeductible());
        assertEquals(90_000, t.due());
        Ecriture liquidation = compta.liquiderTva(patron, YearMonth.of(2026, 11));
        assertEquals(90_000, liquidation.lignes().stream().filter(l -> l.compte().equals(EtatTva.COMPTE_DUE))
                .mapToLong(LigneEcriture::credit).sum());
        assertThrows(ErreurMetier.class, () -> compta.liquiderTva(patron, YearMonth.of(2026, 11)));
        // La liquidation ne compte pas dans l'état du mois.
        assertEquals(90_000, compta.tva(patron, YearMonth.of(2026, 11)).due());

        // États (brouillard compris) : résultat 1 000 000 − 500 000 = 500 000, bilan équilibré.
        ComptaService.Etats e = compta.etats(patron, 2026, null, true);
        EtatsFinanciers n = e.exercice();
        assertEquals(500_000, n.resultatNet());
        assertEquals(1_000_000, n.valeur("TA"));
        assertEquals(500_000, n.valeur("RA"));
        assertEquals(500_000, n.valeur("XA"), "marge commerciale");
        assertEquals(500_000, n.valeur("CJ"));
        assertEquals(1_180_000, n.valeur("BS"));
        assertEquals(590_000, n.valeur("DJ"));
        assertTrue(n.equilibre(), n.totalActif() + " ≠ " + n.totalPassif());
        assertEquals(1_180_000 + 0, n.totalActif() - 0 - 0 - (n.valeur("BJ")));

        // Système minimal : mêmes chiffres, états simplifiés.
        compta.definirParametres(patron, Parametres.Systeme.MINIMAL, false);
        EtatsFinanciers m = compta.etats(patron, 2026, null, true).exercice();
        assertEquals(1_000_000, m.valeur("RT"));
        assertEquals(500_000, m.valeur("DT"));
        assertEquals(500_000, m.valeur("RN"));
        assertEquals(1_180_000, m.valeur("AD"));
        assertTrue(m.equilibre());
    }

    @Test
    void annulationEtContrePassation() {
        compta.definirParametres(patron, Parametres.Systeme.NORMAL, true);   // validation automatique
        compta.recevoir(facture("0002", 100_000, 0));
        compta.recevoir(reglement("R2", "FA-2026-0002", 100_000, "MOBILE_MONEY", JOUR));
        Ecriture r = ecritureDe("ventes", "REGLEMENT:R2");
        assertTrue(r.validee());
        assertEquals("552000", r.lignes().get(0).compte());

        compta.annulerOperation(tenant, "ventes", "REGLEMENT:R2", JOUR.plusDays(2), "erreur de saisie");
        OperationRecue o = s.operation(tenant, "ventes", "REGLEMENT:R2").orElseThrow();
        assertEquals(OperationRecue.Statut.ANNULEE, o.statut());
        Ecriture inverse = s.inverseDe(tenant, r.id()).orElseThrow();
        assertTrue(inverse.validee());
        assertEquals(100_000, inverse.lignes().get(0).credit());
        Balance b = compta.balance(patron, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), false);
        assertEquals(100_000L, (long) b.soldes().get("411000"), "la facture reste due");

        // Une écriture saisie : contre-passation manuelle, une seule fois.
        Ecriture saisie = compta.saisir(patron, "OD", JOUR, "PC-1", "Frais bancaires", List.of(
                LigneEcriture.debit("631000", 2_500, ""), LigneEcriture.credit("521000", 2_500, "")));
        Ecriture validee = compta.valider(patron, saisie.id());
        assertEquals("OD-2026-00001", validee.numero());
        assertThrows(ErreurMetier.class, () -> compta.corriger(patron, validee.id(), "OD", JOUR, "", "x",
                validee.lignes()));
        compta.contrePasser(patron, validee.id(), JOUR, "doublon");
        assertThrows(ErreurMetier.class, () -> compta.contrePasser(patron, validee.id(), JOUR, "encore"));
    }

    @Test
    void saisieControleeEtDroits() {
        assertThrows(ErreurMetier.class, () -> compta.saisir(patron, "OD", JOUR, "", "Déséquilibrée", List.of(
                LigneEcriture.debit("601000", 1_000, ""), LigneEcriture.credit("571000", 900, ""))));
        assertThrows(ErreurMetier.class, () -> compta.saisir(patron, "OD", JOUR, "", "Compte inconnu", List.of(
                LigneEcriture.debit("699999", 1_000, ""), LigneEcriture.credit("571000", 1_000, ""))));
        assertThrows(ErreurMetier.class, () -> compta.saisir(patron, "AN", JOUR, "", "À-nouveaux", List.of(
                LigneEcriture.debit("601000", 1_000, ""), LigneEcriture.credit("571000", 1_000, ""))));
        assertThrows(AccesRefuse.class, () -> compta.plan(gestionnaire));

        // Recette (système minimal) : caisse au débit, vente au crédit.
        Ecriture r = compta.recetteOuDepense(patron, true, JOUR, "571000", "701000", 25_000, "Ventes du jour", "");
        assertEquals("TR", r.journal());
        assertEquals(25_000, r.lignes().get(0).debit());
        assertEquals("571000", r.lignes().get(0).compte());
        assertThrows(ErreurMetier.class, () -> compta.recetteOuDepense(patron, false, JOUR, "601000", "571000",
                1_000, "Inversé", ""));
    }

    @Test
    void imputationManquanteEtCompteCree() {
        compta.preparer(tenant);
        // Sans imputation pour les ventes de services : à revoir, sans bloquer.
        List<Imputation> sans = new ArrayList<>(s.imputations(tenant));
        sans.removeIf(i -> i.role() == Role.VENTE_SERVICES);
        s.remplacer(tenant, sans);
        compta.recevoir(new OperationComptable(tenant, "ventes", "FACTURE:S1", JOUR,
                OperationComptable.Journal.VENTES, "FA-2026-0003", "Prestation", List.of(
                Ligne.debit(Role.CLIENT, "", 50_000), Ligne.credit(Role.VENTE_SERVICES, "", 50_000))));
        OperationRecue o = s.operation(tenant, "ventes", "FACTURE:S1").orElseThrow();
        assertEquals(OperationRecue.Statut.A_REVOIR, o.statut());
        assertTrue(o.message().contains("Services vendus"), o.message());
        assertThrows(ErreurMetier.class, () -> compta.enregistrerImputations(patron, sans),
                "toutes les imputations générales sont exigées");
        List<Imputation> avec = new ArrayList<>(sans);
        avec.add(new Imputation(tenant, Role.VENTE_SERVICES, "", "706100"));
        compta.enregistrerImputations(patron, avec);
        assertEquals(OperationRecue.Statut.ECRITE, compta.reessayer(patron, o.id()).statut());
        assertEquals("Services vendus", s.compte(tenant, "706100").orElseThrow().libelle(),
                "compte créé avec le libellé de son parent");

        // La paie impose ses comptes : ils sont créés s'ils manquent.
        compta.recevoir(new OperationComptable(tenant, "paie", "PERIODE:11", LocalDate.of(2026, 11, 30),
                OperationComptable.Journal.PAIE, "Paie 11/2026", "Paie de novembre 2026", List.of(
                new Ligne(Role.COMPTE, "", "661100", "", "", "Salaires", 300_000, 0),
                new Ligne(Role.COMPTE, "", "431300", "", "", "CNSS", 0, 12_000),
                new Ligne(Role.COMPTE, "", "422000", "", "", "Net à payer", 0, 288_000))));
        assertEquals("PA", ecritureDe("paie", "PERIODE:11").journal());
        assertTrue(s.compte(tenant, "431300").isPresent());
    }

    @Test
    void clotureEtANouveaux() {
        compta.definirParametres(patron, Parametres.Systeme.NORMAL, true);
        compta.recevoir(facture("0003", 1_000_000, 180_000));
        compta.recevoir(achat());
        horloge.maintenant = Instant.parse("2027-01-05T10:00:00Z");
        assertTrue(compta.obstaclesCloture(patron, 2026).isEmpty(), compta.obstaclesCloture(patron, 2026).toString());
        Ecriture an = compta.cloturer(patron, 2026);
        assertEquals("AN-2027-00001", an.numero());
        assertEquals(LocalDate.of(2027, 1, 1), an.date());
        assertEquals(500_000, an.lignes().stream().filter(l -> l.compte().equals("130100"))
                .mapToLong(LigneEcriture::credit).sum(), "bénéfice en instance d'affectation");
        assertEquals(1_180_000, an.lignes().stream().filter(l -> l.compte().equals("411000"))
                .mapToLong(LigneEcriture::debit).sum());
        assertEquals("CLI:1", an.lignes().stream().filter(l -> l.compte().equals("411000")).findFirst()
                .orElseThrow().tiers(), "détail par tiers conservé");

        // Plus rien n'entre en 2026 : l'opération reste à revoir.
        compta.recevoir(reglement("R3", "FA-2026-0003", 1_180_000, "VIREMENT", LocalDate.of(2026, 12, 31)));
        assertEquals(OperationRecue.Statut.A_REVOIR, s.operation(tenant, "ventes", "REGLEMENT:R3").orElseThrow()
                .statut());
        // Le bilan d'ouverture 2027 = le bilan de clôture 2026.
        Balance b = compta.balance(patron, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), false);
        assertEquals(1_180_000L, (long) b.soldes().get("411000"));
        assertEquals(-500_000L, (long) b.soldes().get("130100"));

        // Rouvrir, corriger, reclôturer : les à-nouveaux sont refaits.
        compta.rouvrir(patron, 2026);
        assertEquals(OperationRecue.Statut.ECRITE, compta.reessayer(patron,
                s.operation(tenant, "ventes", "REGLEMENT:R3").orElseThrow().id()).statut());
        Ecriture an2 = compta.cloturer(patron, 2026);
        assertEquals(1_180_000, an2.lignes().stream().filter(l -> l.compte().equals("521000"))
                .mapToLong(LigneEcriture::debit).sum());
        assertTrue(an2.lignes().stream().noneMatch(l -> l.compte().equals("411000")), "client soldé");
        assertEquals(1, s.ecritures.values().stream().filter(x -> x.journal().equals("AN")).count());
    }

    @Test
    void filtreDesEcritures() {
        compta.recevoir(facture("0004", 10_000, 1_800));
        compta.recevoir(achat());
        assertEquals(1, compta.ecritures(patron, new PortsCompta.Filtre("AC", null, null, null, null, 100)).size());
        assertEquals(2, compta.ecritures(patron, new PortsCompta.Filtre(null, null, null, Ecriture.Statut.BROUILLARD,
                null, 100)).size());
    }
}
