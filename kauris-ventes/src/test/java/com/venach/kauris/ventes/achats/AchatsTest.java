package com.venach.kauris.ventes.achats;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Droit;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.ventes.EnMemoire;
import com.venach.kauris.ventes.achats.application.AchatsService;
import com.venach.kauris.ventes.achats.application.PermissionsAchats;
import com.venach.kauris.ventes.achats.application.PortsAchats;
import com.venach.kauris.ventes.achats.domain.Commande;
import com.venach.kauris.ventes.achats.domain.Commande.LigneCommande;
import com.venach.kauris.ventes.achats.domain.FactureFournisseur;
import com.venach.kauris.ventes.achats.domain.Fournisseur;
import com.venach.kauris.ventes.achats.domain.Reception;
import com.venach.kauris.ventes.achats.domain.ReglementFournisseur;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.stock.StockEnMemoire;
import com.venach.kauris.ventes.stock.application.StockService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les achats : de la commande à la réception (stock), puis la facture fournisseur et son règlement. */
class AchatsTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);
    private final Clock horloge = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private final EnMemoire ventesStockees = new EnMemoire();
    private final StockEnMemoire stockage = new StockEnMemoire();
    private final AchatsEnMemoire achatsStockes = new AchatsEnMemoire();
    private final VentesService ventes = new VentesService(ventesStockees, ventesStockees, ventesStockees,
            ventesStockees, ventesStockees, UniteDeTravail.DIRECTE, horloge);
    private final StockService stock = new StockService(ventesStockees, stockage, stockage, stockage,
            UniteDeTravail.DIRECTE, horloge);
    private final AchatsService achats = new AchatsService(achatsStockes, achatsStockes, achatsStockes, achatsStockes,
            achatsStockes, achatsStockes, PortsAchats.Decaissements.AUCUN, ventesStockees, ventesStockees, stock,
            UniteDeTravail.DIRECTE, horloge);
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Boutique", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));

    private Fournisseur fournisseur(String nom) {
        return achats.creerFournisseur(patron, new Fournisseur.Etat(null, null, nom, "M. Agbo", "", "+228 90", "",
                "Lomé", "Togo", "1000777", "", 30, "", true, null));
    }

    private Article article(String nom, boolean suivi) {
        return ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.BIEN, "", nom, "sac", 15_000,
                new BigDecimal("18"), suivi, null, true, null));
    }

    @Test
    void deLaCommandeALaReceptionPuisALaFactureEtAuReglement() {
        Fournisseur sotoco = fournisseur("Sotoco");
        Article ciment = article("Ciment 50 kg", true);
        Article fer = article("Fer de 10", false);
        Commande c = achats.creerCommande(patron, sotoco.id(), JOUR, JOUR.plusDays(3), "Chantier", "", List.of(
                new LigneCommande(ciment.id(), "Ciment 50 kg", new BigDecimal("100"), "sac", 4_500, new BigDecimal("18"),
                        null),
                new LigneCommande(fer.id(), "Fer de 10", new BigDecimal("20"), "barre", 3_000, new BigDecimal("18"),
                        null)));
        assertThrows(ErreurMetier.class, () -> achats.recevoir(patron, c.id(), List.of(BigDecimal.ONE), null, JOUR, ""),
                "un brouillon ne se réceptionne pas");
        Commande envoyee = achats.envoyer(patron, c.id());
        assertEquals("BC-2026-0001", envoyee.numero());
        // 450 000 + 60 000 = 510 000 HT ; TVA 91 800 ; TTC 601 800
        assertEquals(601_800, envoyee.totaux().totalTtc());

        UUID magasin = stock.depots(patron, false).get(0).id();
        Reception r1 = achats.recevoir(patron, c.id(), List.of(new BigDecimal("60"), new BigDecimal("20")), magasin,
                JOUR, "BL-555");
        assertEquals("BR-2026-0001", r1.numero());
        assertEquals(Commande.Statut.RECUE_EN_PARTIE, achats.commande(patron, c.id()).statut());
        StockService.Etat etat = stock.etat(patron, null, null, false);
        assertEquals(1, etat.lignes().size(), "le fer n'est pas suivi en stock");
        assertEquals(new BigDecimal("60.000"), etat.lignes().get(0).quantite());
        assertEquals(270_000, etat.valeurTotale(), "au prix de la commande");

        achats.recevoir(patron, c.id(), java.util.Arrays.asList(new BigDecimal("40"), null), magasin, JOUR, "BL-556");
        assertEquals(Commande.Statut.RECUE, achats.commande(patron, c.id()).statut());
        assertEquals(2, achats.receptions(patron, c.id()).size());

        FactureFournisseur f = achats.enregistrerFacture(patron, sotoco.id(), "F-2026-889", JOUR, null, "Chantier",
                510_000, 91_800, c.id());
        assertEquals("FF-2026-0001", f.numero());
        assertEquals(JOUR.plusDays(30), f.echeance(), "délai du fournisseur");
        assertThrows(ErreurMetier.class, () -> achats.enregistrerFacture(patron, sotoco.id(), "f-2026-889", JOUR, null,
                "", 1, 0, null), "déjà enregistrée");

        ReglementFournisseur r = achats.payer(patron, f.id(), JOUR, 300_000, ModeReglement.VIREMENT, "VIR-1", null);
        assertEquals(301_800, achats.facture(patron, f.id()).reste());
        assertThrows(ErreurMetier.class, () -> achats.payer(patron, f.id(), JOUR, 301_801, ModeReglement.ESPECES, "",
                null));
        assertThrows(ErreurMetier.class, () -> achats.annulerFacture(patron, f.id()), "déjà réglée en partie");
        achats.annulerReglement(patron, r.id(), "Erreur");
        assertEquals(601_800, achats.facture(patron, f.id()).reste());
        AchatsService.Bilan bilan = achats.bilan(patron);
        assertEquals(601_800, bilan.aPayer());
        assertTrue(bilan.enCours().isEmpty(), "commande entièrement reçue");
        assertEquals(1, achats.dettes(patron).size());
        assertTrue(achats.payerAutomatiquement(tenant, f.id(), JOUR, 700_000, ModeReglement.PISPI, "E2E", null)
                .orElseThrow().montant() == 601_800, "ramené au reste");
        assertEquals("Payée", achats.facture(patron, f.id()).situation(JOUR));
    }

    @Test
    void commandeAnnuleeOuSoldeeEtDroits() {
        Fournisseur f = fournisseur("Cimtogo");
        Article ciment = article("Ciment", true);
        Commande c = achats.envoyer(patron, achats.creerCommande(patron, f.id(), JOUR, null, "", "", List.of(
                new LigneCommande(ciment.id(), "Ciment", BigDecimal.TEN, "sac", 4_000, BigDecimal.ZERO, null))).id());
        achats.recevoir(patron, c.id(), List.of(new BigDecimal("4")), stock.depots(patron, false).get(0).id(), JOUR, "");
        achats.annulerOuSolder(patron, c.id());
        assertEquals(Commande.Statut.RECUE, achats.commande(patron, c.id()).statut(), "reçue en partie : soldée");
        Commande vide = achats.envoyer(patron, achats.creerCommande(patron, f.id(), JOUR, null, "", "", List.of(
                new LigneCommande(null, "Transport", BigDecimal.ONE, "", 10_000, BigDecimal.ZERO, null))).id());
        achats.annulerOuSolder(patron, vide.id());
        assertEquals(Commande.Statut.ANNULEE, achats.commande(patron, vide.id()).statut());
        assertThrows(ErreurMetier.class, () -> fournisseur("cimtogo"), "doublon");

        Acces magasinier = new Acces(tenant, "Boutique", true, UUID.randomUUID(), "Kofi", "k@exemple.test",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)), Set.of(
                new Droit(PermissionsAchats.CONSULTER, null), new Droit(PermissionsAchats.RECEVOIR, null)), List.of());
        assertThrows(AccesRefuse.class, () -> achats.creerCommande(magasinier, f.id(), JOUR, null, "", "", List.of()));
        assertThrows(AccesRefuse.class, () -> achats.enregistrerFacture(magasinier, f.id(), "X", JOUR, null, "", 1, 0,
                null));
        assertEquals(1, achats.fournisseurs(magasinier, "cim", false).size());
    }
}
