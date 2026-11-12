package com.venach.kauris.ventes.stock;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Droit;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.ventes.EnMemoire;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.TypeDocument;
import com.venach.kauris.ventes.stock.application.PermissionsStock;
import com.venach.kauris.ventes.stock.application.StockService;
import com.venach.kauris.ventes.stock.domain.Depot;
import com.venach.kauris.ventes.stock.domain.Position;
import com.venach.kauris.ventes.stock.domain.TypeMouvement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le stock : coût moyen pondéré, entrées et sorties, ventes et avoirs, transferts, inventaire, droits. */
class StockTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);
    private final Clock horloge = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private final EnMemoire ventesStockees = new EnMemoire();
    private final StockEnMemoire stockage = new StockEnMemoire();
    private final VentesService ventes = new VentesService(ventesStockees, ventesStockees, ventesStockees,
            ventesStockees, ventesStockees, UniteDeTravail.DIRECTE, horloge);
    private final StockService stock = new StockService(ventesStockees, stockage, stockage, stockage,
            UniteDeTravail.DIRECTE, horloge);
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Boutique", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));

    StockTest() {
        ventes.brancherStock(stock);
    }

    private Article article(String nom, BigDecimal seuil) {
        return ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.BIEN, "", nom, "u", 1_000,
                new BigDecimal("18"), true, seuil, true, null));
    }

    private UUID principal() {
        return stock.depots(patron, false).get(0).id();
    }

    @Test
    void leCoutMoyenPondereSuitLesEntreesEtValoriseLesSorties() {
        Position p = Position.vide(tenant, UUID.randomUUID())
                .entrer(new BigDecimal("10"), 500)     // 10 à 500 = 5 000
                .entrer(new BigDecimal("30"), 700);    // + 30 à 700 = 21 000 → 40 pour 26 000 : CMUP 650
        assertEquals(650, p.cmup());
        p = p.sortir(new BigDecimal("15"));            // 15 × 650 = 9 750 → 25 pour 16 250
        assertEquals(new BigDecimal("25.000"), p.quantite());
        assertEquals(16_250, p.valeur());
        assertEquals(650, p.cmup());
        p = p.sortir(new BigDecimal("30"));            // 5 vendus de plus qu'en stock
        assertEquals(new BigDecimal("-5.000"), p.quantite());
        p = p.entrer(new BigDecimal("20"), 800);       // le négatif est couvert : 15 à 800
        assertEquals(new BigDecimal("15.000"), p.quantite());
        assertEquals(12_000, p.valeur());
    }

    @Test
    void factureEtAvoirFontSortirEtRevenirLeStock() {
        Article savon = article("Savon", new BigDecimal("5"));
        UUID magasin = principal();
        assertEquals(Depot.NOM_PAR_DEFAUT, stock.depots(patron, false).get(0).nom());
        stock.saisir(patron, TypeMouvement.STOCK_INITIAL, savon.id(), magasin, new BigDecimal("12"), 400, JOUR,
                "", "");
        Client c = ventes.creerClient(patron, new Client.Etat(null, null, Client.Type.PARTICULIER, "Yao", "", "", "",
                "", "", "", "", "", 0, "", true, null));
        DocumentVente f = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(), new DocumentVente.Contenu(JOUR,
                JOUR, "", "", List.of(savon.ligne(new BigDecimal("8")),
                new Ligne(null, "Livraison", BigDecimal.ONE, "", 500, null, null)), null, ""));
        assertEquals(0, stockage.duDocument(tenant, f.id()).size(), "un brouillon ne bouge pas le stock");
        f = ventes.emettre(patron, f.id());
        StockService.Etat etat = stock.etat(patron, null, null, false);
        assertEquals(new BigDecimal("4.000"), etat.lignes().get(0).quantite());
        assertEquals(1_600, etat.valeurTotale());
        assertTrue(etat.lignes().get(0).bas(), "4 sous le seuil de 5");
        assertEquals(1, etat.alertes());

        DocumentVente avoir = ventes.avoirSur(patron, f.id());
        ventes.emettre(patron, avoir.id());
        assertEquals(new BigDecimal("12.000"), stock.etat(patron, magasin, null, false).lignes().get(0).quantite());
        // Une facture ne fait bouger le stock qu'une fois.
        stock.documentEmis(ventes.document(patron, f.id()));
        assertEquals(new BigDecimal("12.000"), stock.etat(patron, null, null, false).lignes().get(0).quantite());
        assertEquals(List.of(TypeMouvement.RETOUR_VENTE, TypeMouvement.VENTE, TypeMouvement.STOCK_INITIAL),
                stock.fiche(patron, savon.id(), null, null).mouvements().stream().map(m -> m.type()).toList());
    }

    @Test
    void transfertEtInventaireParDepot() {
        Article riz = article("Riz 25 kg", null);
        UUID magasin = principal();
        UUID boutique = stock.creerDepot(patron, "Boutique Bè").id();
        stock.saisir(patron, TypeMouvement.ENTREE, riz.id(), magasin, new BigDecimal("50"), 12_000, JOUR, "Achat", "BL-1");
        stock.transferer(patron, riz.id(), magasin, boutique, new BigDecimal("20"), JOUR, "");
        Map<String, BigDecimal> parDepot = stock.fiche(patron, riz.id(), null, null).parDepot().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().nom(), Map.Entry::getValue));
        assertEquals(new BigDecimal("30.000"), parDepot.get(Depot.NOM_PAR_DEFAUT));
        assertEquals(new BigDecimal("20.000"), parDepot.get("Boutique Bè"));
        assertEquals(600_000, stock.etat(patron, null, null, false).valeurTotale(), "un transfert ne change pas la valeur");

        // On compte 18 sacs à la boutique : 2 manquent.
        assertEquals(1, stock.inventorier(patron, boutique, Map.of(riz.id(), new BigDecimal("18")), JOUR, "INV-1"));
        assertEquals(new BigDecimal("48.000"), stock.etat(patron, null, null, false).lignes().get(0).quantite());
        assertEquals(576_000, stock.etat(patron, null, null, false).valeurTotale());
        assertThrows(ErreurMetier.class, () -> stock.archiverDepot(patron, boutique, true), "encore du stock");
        assertThrows(ErreurMetier.class, () -> stock.archiverDepot(patron, magasin, true), "le principal");
    }

    @Test
    void lesRefusEtLesDroits() {
        Article riz = article("Riz", null);
        Article conseil = ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.SERVICE, "", "Conseil",
                "h", 10_000, BigDecimal.ZERO, false, null, true, null));
        UUID magasin = principal();
        assertThrows(ErreurMetier.class, () -> stock.saisir(patron, TypeMouvement.ENTREE, conseil.id(), magasin,
                BigDecimal.ONE, 0, JOUR, "", ""), "une prestation n'a pas de stock");
        assertThrows(ErreurMetier.class, () -> stock.saisir(patron, TypeMouvement.VENTE, riz.id(), magasin,
                BigDecimal.ONE, 0, JOUR, "", ""), "une vente vient d'une facture");
        assertThrows(ErreurMetier.class, () -> stock.saisir(patron, TypeMouvement.ENTREE, riz.id(), magasin,
                BigDecimal.ONE, 0, JOUR.plusDays(1), "", ""));
        assertThrows(ErreurMetier.class, () -> ventes.creerArticle(patron, new Article.Etat(null, null,
                Article.Type.SERVICE, "", "Pose", "h", 1, BigDecimal.ZERO, true, null, true, null)));
        Acces lecteur = new Acces(tenant, "Boutique", true, UUID.randomUUID(), "Kofi", "k@exemple.test",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)), Set.of(new Droit(PermissionsStock.CONSULTER,
                null)), List.of());
        assertEquals(0, stock.etat(lecteur, null, null, false).valeurTotale());
        assertThrows(AccesRefuse.class, () -> stock.saisir(lecteur, TypeMouvement.ENTREE, riz.id(), magasin,
                BigDecimal.ONE, 100, JOUR, "", ""));
        assertThrows(AccesRefuse.class, () -> stock.creerDepot(lecteur, "Pirate"));
    }
}
