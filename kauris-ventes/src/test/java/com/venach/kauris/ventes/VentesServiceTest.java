package com.venach.kauris.ventes;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ElementIntrouvable;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Droit;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.ventes.application.PermissionsVentes;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.DocumentVente.Contenu;
import com.venach.kauris.ventes.domain.DocumentVente.Situation;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.domain.Reglement;
import com.venach.kauris.ventes.domain.StatutDocument;
import com.venach.kauris.ventes.domain.TypeDocument;
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

/** Le parcours des ventes avec un stockage en mémoire : du devis au règlement, et ce qui est refusé. */
class VentesServiceTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);
    private final EnMemoire stockage = new EnMemoire();
    private final VentesService ventes = new VentesService(stockage, stockage, stockage, stockage, stockage,
            UniteDeTravail.DIRECTE, Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC));
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = acces(tenant, Role.PROPRIETAIRE, Set.of());

    private static Acces acces(UUID tenant, Role role, Set<String> permissions) {
        return new Acces(tenant, "Entreprise", true, UUID.randomUUID(), "Awa Mensah", "awa@exemple.test",
                Set.of(AttributionRole.pourTouteLOrganisation(role)),
                permissions.stream().map(p -> new Droit(p, null)).collect(java.util.stream.Collectors.toSet()),
                List.of());
    }

    private Client client(String nom, int delai) {
        return ventes.creerClient(patron, new Client.Etat(null, null, Client.Type.ENTREPRISE, nom, "M. Koffi",
                "compta@exemple.test", "+228 90 00 00 00", "Rue du Commerce", "Lomé", "Togo", "1000123456", "",
                delai, "", true, null));
    }

    private static Contenu contenu(LocalDate date, LocalDate echeance, Ligne... lignes) {
        return new Contenu(date, echeance, "Installation", "BC-77", List.of(lignes), BigDecimal.ZERO, "");
    }

    private static Ligne ligne(String designation, String qte, long pu) {
        return new Ligne(null, designation, new BigDecimal(qte), "u", pu, BigDecimal.ZERO, new BigDecimal("18"));
    }

    @Test
    void duDevisALaFacturePuisAuxReglementsEtALAvoir() {
        Client c = client("Société Alpha", 30);
        DocumentVente devis = ventes.creerBrouillon(patron, TypeDocument.DEVIS, c.id(),
                contenu(JOUR, JOUR.plusDays(30), ligne("Ordinateur", "2", 250_000), ligne("Pose", "1", 50_000)));
        assertEquals(null, devis.numero());
        assertEquals(Situation.BROUILLON, devis.situation(JOUR));

        devis = ventes.emettre(patron, devis.id());
        assertEquals("DE-2026-0001", devis.numero());
        ventes.accepterDevis(patron, devis.id());

        DocumentVente facture = ventes.facturerDevis(patron, devis.id());
        assertEquals(TypeDocument.FACTURE, facture.type());
        assertEquals(JOUR.plusDays(30), facture.contenu().echeance(), "échéance selon le délai du client");
        assertEquals(devis.id(), facture.origineId());
        UUID devisId = devis.id();
        assertThrows(ErreurMetier.class, () -> ventes.facturerDevis(patron, devisId), "un devis ne donne qu'une facture");

        DocumentVente emise = ventes.emettre(patron, facture.id());
        assertEquals("FA-2026-0001", emise.numero());
        // 550 000 HT + 99 000 TVA
        assertEquals(649_000, emise.totaux().totalTtc());
        assertEquals(StatutDocument.FACTURE, ventes.document(patron, devis.id()).statut());
        assertThrows(ErreurMetier.class, () -> ventes.modifierBrouillon(patron, facture.id(), c.id(),
                contenu(JOUR, JOUR, ligne("X", "1", 1))), "une facture émise ne se modifie plus");
        assertThrows(ErreurMetier.class, () -> ventes.supprimerBrouillon(patron, facture.id()));

        Reglement r1 = ventes.encaisser(patron, facture.id(), JOUR, 300_000, ModeReglement.MOBILE_MONEY, "TX1");
        assertEquals(349_000, ventes.document(patron, facture.id()).resteAPayer());
        assertEquals(Situation.PARTIELLEMENT_PAYEE, ventes.document(patron, facture.id()).situation(JOUR));
        assertThrows(ErreurMetier.class, () -> ventes.encaisser(patron, facture.id(), JOUR, 349_001,
                ModeReglement.ESPECES, ""), "pas plus que le reste à payer");

        ventes.annulerReglement(patron, r1.id(), "Erreur de montant");
        assertEquals(649_000, ventes.document(patron, facture.id()).resteAPayer());
        assertThrows(ErreurMetier.class, () -> ventes.annulerReglement(patron, r1.id(), "encore"));
        ventes.encaisser(patron, facture.id(), JOUR, 400_000, ModeReglement.VIREMENT, "VIR-9");

        // Avoir : on retire la pose (50 000 HT + 9 000 TVA)
        DocumentVente avoir = ventes.avoirSur(patron, facture.id());
        assertEquals(2, avoir.contenu().lignes().size());
        ventes.modifierBrouillon(patron, avoir.id(), c.id(), new Contenu(JOUR, null, avoir.contenu().objet(), "",
                List.of(ligne("Pose", "1", 50_000)), BigDecimal.ZERO, ""));
        avoir = ventes.emettre(patron, avoir.id());
        assertEquals("AV-2026-0001", avoir.numero());
        DocumentVente apres = ventes.document(patron, facture.id());
        assertEquals(59_000, apres.credite());
        assertEquals(649_000 - 400_000 - 59_000, apres.resteAPayer());

        // Relevé : facture au débit, règlement et avoir au crédit ; le règlement annulé n'y est pas.
        VentesService.Releve releve = ventes.releve(patron, c.id(), JOUR.withDayOfMonth(1), JOUR);
        assertEquals(3, releve.lignes().size());
        assertEquals(649_000, releve.totalDebit());
        assertEquals(459_000, releve.totalCredit());
        assertEquals(190_000, releve.soldeFinal());

        VentesService.Bilan bilan = ventes.bilan(patron);
        assertEquals(550_000 - 50_000, bilan.factureMoisHt());
        assertEquals(400_000, bilan.encaisseMois());
        assertEquals(190_000, bilan.resteDu());
        assertEquals(1, ventes.duParClient(patron).size());
    }

    @Test
    void laNumerotationEstSansTrouEtSuitLesDates() {
        Client c = client("Client Beta", 0);
        DocumentVente b1 = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR.minusDays(1), JOUR, ligne("A", "1", 1_000)));
        DocumentVente b2 = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("B", "1", 1_000)));
        DocumentVente brouillonSupprime = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("C", "1", 1_000)));
        ventes.supprimerBrouillon(patron, brouillonSupprime.id());

        assertEquals("FA-2026-0001", ventes.emettre(patron, b2.id()).numero());
        // b1 est daté d'avant la dernière facture émise : refusé (il garderait un numéro plus grand qu'une
        // facture plus récente).
        assertThrows(ErreurMetier.class, () -> ventes.emettre(patron, b1.id()));
        ventes.modifierBrouillon(patron, b1.id(), c.id(), contenu(JOUR, JOUR, ligne("A", "1", 1_000)));
        assertEquals("FA-2026-0002", ventes.emettre(patron, b1.id()).numero());

        DocumentVente futur = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR.plusDays(1), JOUR.plusDays(1), ligne("D", "1", 1_000)));
        assertThrows(ErreurMetier.class, () -> ventes.emettre(patron, futur.id()));
        DocumentVente vide = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(), contenu(JOUR, JOUR));
        assertThrows(ErreurMetier.class, () -> ventes.emettre(patron, vide.id()));
    }

    @Test
    void uneEntrepriseSansTvaNEnMetSurAucuneLigneEtImprimeSaMention() {
        ventes.definirReglages(patron, false, new BigDecimal("18"), "TVA non applicable, régime TPU", 15, 30,
                "Paiement à réception.");
        Client c = client("Client Gamma", 0);
        DocumentVente f = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("Conseil", "1", 100_000)));
        assertEquals(BigDecimal.ZERO.setScale(2), f.contenu().lignes().get(0).tauxTva());
        f = ventes.emettre(patron, f.id());
        assertEquals(100_000, f.totaux().totalTtc());
        assertEquals("TVA non applicable, régime TPU", f.mentionTva());
        assertEquals("Paiement à réception.", f.conditions());
    }

    @Test
    void leClientEstFigeALEmission() {
        Client c = client("Client Delta", 0);
        DocumentVente f = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("A", "1", 5_000)));
        Client.Etat e = c.etat();
        ventes.modifierClient(patron, c.id(), new Client.Etat(null, null, e.type(), "Delta SARL", e.contact(),
                e.email(), e.telephone(), "Nouvelle adresse", e.ville(), e.pays(), e.nif(), e.rccm(), 0, "", true,
                null));
        f = ventes.emettre(patron, f.id());
        assertEquals("Delta SARL", f.client().nom(), "relu à l'émission");
        ventes.modifierClient(patron, c.id(), new Client.Etat(null, null, e.type(), "Delta SA", e.contact(),
                e.email(), e.telephone(), "Autre", e.ville(), e.pays(), e.nif(), e.rccm(), 0, "", true, null));
        assertEquals("Delta SARL", ventes.document(patron, f.id()).client().nom(), "puis figé");
    }

    @Test
    void lesPermissionsSontVerifieesParLeService() {
        Client c = client("Client Epsilon", 0);
        Acces lecteur = acces(tenant, Role.LECTEUR, Set.of(PermissionsVentes.CONSULTER));
        Acces vendeur = acces(tenant, Role.GESTIONNAIRE, Set.of(PermissionsVentes.CONSULTER, PermissionsVentes.SAISIR));
        assertEquals(1, ventes.clients(lecteur, "epsilon", false).size());
        assertThrows(AccesRefuse.class, () -> ventes.creerBrouillon(lecteur, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("A", "1", 1_000))));
        DocumentVente f = ventes.creerBrouillon(vendeur, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("A", "1", 1_000)));
        assertThrows(AccesRefuse.class, () -> ventes.emettre(vendeur, f.id()), "émettre est une autre permission");
        assertThrows(AccesRefuse.class, () -> ventes.creerArticle(vendeur, new Article.Etat(null, null,
                Article.Type.SERVICE, "", "Conseil", "h", 10_000, BigDecimal.TEN, true, null)));
        assertThrows(AccesRefuse.class, () -> ventes.definirReglages(vendeur, true, BigDecimal.TEN, "", 0, 30, ""));
    }

    @Test
    void uneOrganisationNeVoitPasLesVentesDUneAutre() {
        Client c = client("Client Zeta", 0);
        DocumentVente f = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("A", "1", 1_000)));
        Acces autre = acces(UUID.randomUUID(), Role.PROPRIETAIRE, Set.of());
        assertThrows(ElementIntrouvable.class, () -> ventes.document(autre, f.id()));
        assertThrows(ElementIntrouvable.class, () -> ventes.client(autre, c.id()));
        assertTrue(ventes.clients(autre, "", true).isEmpty());
        assertThrows(ElementIntrouvable.class, () -> ventes.creerBrouillon(autre, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("A", "1", 1_000))));
    }

    @Test
    void lesDoublonsEtLesClientsArchivesSontRefuses() {
        Client c = client("Société Êta", 0);
        assertThrows(ErreurMetier.class, () -> client("societe eta", 0));
        ventes.archiverClient(patron, c.id());
        assertThrows(ErreurMetier.class, () -> ventes.creerBrouillon(patron, TypeDocument.DEVIS, c.id(),
                contenu(JOUR, JOUR, ligne("A", "1", 1_000))));
        Article a = ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.BIEN, "REF-1", "Câble",
                "m", 1_500, new BigDecimal("18"), true, null));
        assertThrows(ErreurMetier.class, () -> ventes.creerArticle(patron, new Article.Etat(null, null,
                Article.Type.BIEN, "ref-1", "Autre câble", "m", 1_500, new BigDecimal("18"), true, null)));
        assertEquals(1_500, a.ligne(BigDecimal.ONE).montantHt());
    }

    @Test
    void unAvoirNeDepassePasLaFacture() {
        Client c = client("Client Theta", 0);
        DocumentVente f = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(),
                contenu(JOUR, JOUR, ligne("A", "1", 10_000)));
        ventes.emettre(patron, f.id());
        DocumentVente a1 = ventes.avoirSur(patron, f.id());
        DocumentVente a2 = ventes.avoirSur(patron, f.id());
        ventes.emettre(patron, a1.id());
        assertEquals(Situation.ANNULEE_PAR_AVOIR, ventes.document(patron, f.id()).situation(JOUR));
        assertThrows(ErreurMetier.class, () -> ventes.emettre(patron, a2.id()), "la facture est déjà annulée");
        assertThrows(ErreurMetier.class, () -> ventes.avoirSur(patron, f.id()));
    }
}
