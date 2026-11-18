package com.venach.kauris.compta;

import com.venach.kauris.compta.application.ComptaService;
import com.venach.kauris.compta.application.PortsCompta;
import com.venach.kauris.compta.domain.Balance;
import com.venach.kauris.compta.domain.Ecriture;
import com.venach.kauris.compta.domain.LigneEcriture;
import com.venach.kauris.compta.domain.OperationRecue;
import com.venach.kauris.compta.domain.Parametres;
import com.venach.kauris.kernel.comptabilite.Comptabilisation;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Ligne;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Role;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.ui.SessionKauris;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La comptabilité sur un vrai MySQL : migrations et schéma, opérations reçues par le noyau (une seule fois),
 * validation et numéros, contre-passation, lettrage, balance, états, TVA, clôture et à-nouveaux, écrans. Docker requis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ComptaMySqlTest {

    static final AtomicReference<IdentiteConnectee> CONNECTE = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {

        @Bean
        SessionKauris sessionDeTest() {
            return () -> Optional.ofNullable(CONNECTE.get());
        }
    }

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GererOrganisationUseCase organisations;
    @Autowired
    private ResoudreAccesUseCase acces;
    @Autowired
    private Comptabilisation comptabilisation;
    @Autowired
    private ComptaService compta;

    private Acces ouvrir(String nom) {
        String sujet = "sub-" + UUID.randomUUID();
        OrganisationOuverte o = organisations.ouvrir(new Ouverture(nom, null, Identite.venach(sujet),
                sujet + "@exemple.test", "Afi Lawson"));
        CONNECTE.set(new IdentiteConnectee(Identite.venach(sujet), sujet + "@exemple.test", true, "Afi Lawson"));
        return acces.accesDe(Identite.venach(sujet)).stream().filter(x -> x.tenantId().equals(o.tenantId()))
                .findFirst().orElseThrow();
    }

    private static OperationComptable facture(UUID tenant, String ref, LocalDate date, long ht, long tva) {
        return new OperationComptable(tenant, "ventes", "FACTURE:" + ref, date, OperationComptable.Journal.VENTES,
                "FA-" + ref, "Facture FA-" + ref + " · Ets Lawson", List.of(
                Ligne.debit(Role.CLIENT, "", ht + tva).pour("CLI:1", "Ets Lawson"),
                Ligne.credit(Role.VENTE_MARCHANDISES, "", ht),
                Ligne.credit(Role.TVA_COLLECTEE, "18", tva)));
    }

    private static OperationComptable reglement(UUID tenant, String ref, String piece, LocalDate date, long montant) {
        return new OperationComptable(tenant, "ventes", "REGLEMENT:" + ref, date,
                OperationComptable.Journal.TRESORERIE, piece, "Règlement " + piece, List.of(
                Ligne.debit(Role.TRESORERIE, UUID.randomUUID() + "|ESPECES", montant),
                Ligne.credit(Role.CLIENT, "", montant).pour("CLI:1", "Ets Lawson")));
    }

    @Test
    void comptabiliteSurMySql() throws Exception {
        Acces a = ouvrir("Quincaillerie du Marché");
        UUID t = a.tenantId();
        int annee = LocalDate.now(ZoneOffset.UTC).getYear() - 1;
        LocalDate jour = LocalDate.of(annee, 11, 10);

        // Par le noyau, dans la transaction de l'appelant ; deux fois la même opération : une écriture.
        comptabilisation.enregistrer(facture(t, "0001", jour, 1_000_000, 180_000));
        comptabilisation.enregistrer(facture(t, "0001", jour, 1_000_000, 180_000));
        comptabilisation.enregistrer(reglement(t, "R1", "FA-0001", jour.plusDays(5), 1_180_000));
        List<Ecriture> e = compta.ecritures(a, new PortsCompta.Filtre(null, null, null, null, null, 100));
        assertEquals(2, e.size());
        Ecriture vente = e.stream().filter(x -> x.journal().equals("VE")).findFirst().orElseThrow();
        assertEquals(List.of("411000", "701000", "443100"), vente.lignes().stream().map(LigneEcriture::compte).toList());
        assertEquals("CLI:1", vente.lignes().get(0).tiers());
        assertEquals("571000", e.stream().filter(x -> x.journal().equals("TR")).findFirst().orElseThrow().lignes()
                .get(0).compte(), "compte de dépôt sans imputation : le mode de paiement décide");
        assertTrue(compta.precisionsRecues(a).contains("ESPECES"));

        // Validation : numéros sans trou ; lettrage automatique ; balance.
        assertEquals(2, compta.validerBrouillard(a, jour.plusDays(30)));
        vente = compta.ecriture(a, vente.id());
        assertEquals("VE-" + annee + "-00001", vente.numero());
        assertEquals(1, compta.lettrerAutomatiquement(a, "411000"));
        assertTrue(compta.lignesALettrer(a, "411000", false).isEmpty());
        assertEquals(2, compta.lignesALettrer(a, "411000", true).size());
        Balance b = compta.balance(a, LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31), false);
        assertTrue(b.equilibree());
        assertEquals(1_180_000L, (long) b.soldes().get("571000"));

        // Saisie, contre-passation, achat, TVA.
        Ecriture frais = compta.saisir(a, "OD", jour, "REL-11", "Frais bancaires", List.of(
                LigneEcriture.debit("631000", 2_500, ""), LigneEcriture.credit("521000", 2_500, "")));
        frais = compta.valider(a, frais.id());
        Ecriture inverse = compta.contrePasser(a, frais.id(), jour, "doublon");
        assertEquals(frais.id(), inverse.inverseDe());
        assertTrue(compta.inverseDe(a, frais.id()).isPresent());
        comptabilisation.enregistrer(new OperationComptable(t, "achats", "FACTURE:F1", jour,
                OperationComptable.Journal.ACHATS, "FF-1", "Facture Sotoco", List.of(
                Ligne.debit(Role.ACHAT_MARCHANDISES, "", 500_000), Ligne.debit(Role.TVA_DEDUCTIBLE, "", 90_000),
                Ligne.credit(Role.FOURNISSEUR, "", 590_000).pour("FRS:9", "Sotoco"))));
        assertEquals(90_000, compta.tva(a, YearMonth.from(jour)).due());
        Ecriture liquidation = compta.liquiderTva(a, YearMonth.from(jour));
        compta.valider(a, liquidation.id());
        compta.validerBrouillard(a, LocalDate.of(annee, 12, 31));

        // États : résultat 500 000, bilan équilibré.
        ComptaService.Etats etats = compta.etats(a, annee, null, false);
        assertEquals(500_000, etats.exercice().resultatNet());
        assertTrue(etats.exercice().equilibre());

        // Annulation d'une opération validée : contre-passation datée du jour d'annulation.
        comptabilisation.annuler(t, "achats", "FACTURE:F1", LocalDate.of(annee, 12, 20), "facture en double");
        assertEquals(OperationRecue.Statut.ANNULEE, compta.aRevoir(a).stream().findFirst()
                .map(OperationRecue::statut).orElse(OperationRecue.Statut.ANNULEE));
        compta.validerBrouillard(a, LocalDate.of(annee, 12, 31));
        assertEquals(1_000_000, compta.etats(a, annee, null, false).exercice().resultatNet(),
                "l'achat annulé ne compte plus");

        // Clôture : à-nouveaux de l'année suivante ; plus rien n'entre dans l'exercice clos.
        assertTrue(compta.obstaclesCloture(a, annee).isEmpty(), compta.obstaclesCloture(a, annee).toString());
        Ecriture an = compta.cloturer(a, annee);
        assertEquals("AN-" + (annee + 1) + "-00001", an.numero());
        assertEquals(1_000_000, an.lignes().stream().filter(l -> l.compte().equals("130100"))
                .mapToLong(LigneEcriture::credit).sum());
        comptabilisation.enregistrer(facture(t, "0002", LocalDate.of(annee, 12, 30), 10_000, 0));
        assertEquals(1, compta.aRevoir(a).size(), "exercice clos : à revoir, sans bloquer l'émission");
        compta.rouvrir(a, annee);
        assertEquals(OperationRecue.Statut.ECRITE, compta.reessayer(a, compta.aRevoir(a).get(0).id()).statut());

        // Les écrans s'affichent ; le système minimal change les états.
        for (String page : List.of("/compta", "/compta/ecritures", "/compta/ecritures/nouvelle",
                "/compta/ecritures/" + vente.id(), "/compta/grand-livre?compte=411000&du=" + annee + "-01-01",
                "/compta/balance?du=" + annee + "-01-01", "/compta/lettrage?compte=411000&toutes=1",
                "/compta/etats?annee=" + annee, "/compta/tva?mois=" + YearMonth.from(jour), "/compta/exercices",
                "/compta/parametres", "/compta/plan", "/compta/operations", "/compta/recettes-depenses")) {
            mvc.perform(get(page)).andExpect(status().isOk());
        }
        mvc.perform(get("/compta/etats?annee=" + annee)).andExpect(content().string(containsString("MARGE COMMERCIALE")));
        mvc.perform(post("/compta/parametres").param("systeme", "MINIMAL").param("validationAutomatique", "on"))
                .andExpect(flash().attributeExists("succes"));
        assertEquals(Parametres.Systeme.MINIMAL, compta.parametres(a).systeme());
        assertTrue(compta.parametres(a).validationAutomatique());
        mvc.perform(get("/compta/etats?annee=" + annee)).andExpect(content().string(containsString("TOTAL DES RECETTES")));
        for (String export : List.of("/compta/balance.pdf?du=" + annee + "-01-01", "/compta/etats/bilan.xlsx?annee="
                + annee, "/compta/etats/resultat.pdf?annee=" + annee, "/compta/grand-livre.csv?du=" + annee + "-01-01",
                "/compta/tva.pdf?mois=" + YearMonth.from(jour), "/compta/plan.xlsx", "/compta/ecritures/" + vente.id()
                        + ".pdf")) {
            mvc.perform(get(export)).andExpect(status().isOk());
        }

        // Les imputations se corrigent par l'écran : les opérations suivantes vont sur le nouveau compte.
        mvc.perform(post("/compta/plan").param("numero", "701100").param("libelle", "Ventes de quincaillerie"))
                .andExpect(flash().attributeExists("succes"));
        var imputations = compta.imputations(a);
        var form = post("/compta/imputations");
        for (int i = 0; i < imputations.size(); i++) {
            var x = imputations.get(i);
            form.param("imp_role_" + i, x.role().name()).param("imp_precision_" + i, x.precision())
                    .param("imp_compte_" + i, x.role() == Role.VENTE_MARCHANDISES && x.precision().isEmpty()
                            ? "701100 Ventes de quincaillerie" : x.compte());
        }
        mvc.perform(form).andExpect(flash().attributeExists("succes"));
        comptabilisation.enregistrer(facture(t, "0003", LocalDate.now(ZoneOffset.UTC), 50_000, 9_000));
        Ecriture apres = compta.ecritures(a, new PortsCompta.Filtre("VE", LocalDate.now(ZoneOffset.UTC), null, null,
                null, 10)).get(0);
        assertEquals("701100", apres.lignes().get(1).compte());
        assertTrue(apres.validee(), "validation automatique");

        // Une autre organisation ne voit rien.
        Acces autre = ouvrir("Autre");
        assertTrue(compta.ecritures(autre, new PortsCompta.Filtre(null, null, null, null, null, 10)).isEmpty());
        assertFalse(compta.plan(autre).isEmpty(), "son plan est prêt");
    }
}
