package com.venach.kauris.ventes.caisse;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Droit;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.ventes.EnMemoire;
import com.venach.kauris.ventes.application.PermissionsVentes;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.caisse.application.CaisseService;
import com.venach.kauris.ventes.caisse.application.CaisseService.Encaissement;
import com.venach.kauris.ventes.caisse.domain.SessionCaisse;
import com.venach.kauris.ventes.caisse.domain.TicketCaisse;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Reglement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La caisse : ouverture, tickets encaissés (facture émise et réglée), monnaie rendue, clôture et écart. */
class CaisseTest {

    private static final UUID ESPECES = UUID.randomUUID();
    private static final UUID MOMO = UUID.randomUUID();

    /** Une horloge qu'on avance. */
    static class Horloge extends Clock {
        Instant maintenant = Instant.parse("2026-11-12T09:00:00Z");

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

    /** Une trésorerie imitée : deux comptes, et le journal des règlements reportés. */
    static class Tresorerie implements Ports.Encaissements {
        final List<String> journal = new ArrayList<>();

        @Override
        public List<Compte> comptes(UUID tenantId) {
            return List.of(new Compte(ESPECES, "Caisse boutique"), new Compte(MOMO, "Mobile Money"));
        }

        @Override
        public List<Action> actions(Acces acces, DocumentVente facture) {
            return List.of();
        }

        @Override
        public void reglementEnregistre(DocumentVente facture, Reglement r) {
            journal.add(r.mode() + " " + r.montant() + "@" + (ESPECES.equals(r.compteId()) ? "caisse" : "momo"));
        }

        @Override
        public void reglementAnnule(DocumentVente facture, Reglement r) {
        }
    }

    private final Horloge horloge = new Horloge();
    private final EnMemoire stockage = new EnMemoire();
    private final Tresorerie tresorerie = new Tresorerie();
    private final VentesService ventes = new VentesService(stockage, stockage, stockage, stockage, stockage,
            tresorerie, UniteDeTravail.DIRECTE, horloge);
    private final CaisseEnMemoire caisseStockee = new CaisseEnMemoire();
    private final CaisseService caisse = new CaisseService(ventes, caisseStockee, caisseStockee,
            UniteDeTravail.DIRECTE, horloge);
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Boutique", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));
    private final Acces caissier = caissier("Kofi");
    private final Article pain = ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.BIEN, "P1",
            "Pain", "", 250, new BigDecimal("18"), false, null, true, null));
    private final Article lait = ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.BIEN, "L1",
            "Lait 1 L", "", 1000, new BigDecimal("18"), false, null, true, null));

    /** Une personne qui ne peut que consulter et tenir une caisse. */
    private Acces caissier(String nom) {
        return new Acces(tenant, "Boutique", true, UUID.randomUUID(), nom, nom + "@exemple.test",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)),
                Set.of(new Droit(PermissionsVentes.CONSULTER, null), new Droit(PermissionsVentes.CAISSE, null)),
                List.of());
    }

    private static Map<UUID, BigDecimal> panier(Object... articleQuantite) {
        Map<UUID, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < articleQuantite.length; i += 2) {
            m.put(((Article) articleQuantite[i]).id(), new BigDecimal(articleQuantite[i + 1].toString()));
        }
        return m;
    }

    @Test
    void unTicketEmetUneFactureRegleeEtRendLaMonnaie() {
        assertThrows(ErreurMetier.class, () -> caisse.vendre(caissier, null, panier(pain, 2), null,
                new Encaissement(0, 0, "", 0)), "caisse fermée");
        assertThrows(ErreurMetier.class, () -> caisse.ouvrir(caissier, 10_000, null, null, null),
                "la trésorerie tient des comptes : celui des espèces est demandé");
        SessionCaisse s = caisse.ouvrir(caissier, 10_000, ESPECES, MOMO, null);
        assertEquals("CA-2026-0001", s.numero());
        assertThrows(ErreurMetier.class, () -> caisse.ouvrir(caissier, 0, ESPECES, null, null), "déjà ouverte");

        // 4 pains (1 000 HT) et 2 laits (2 000 HT) : 3 000 HT + 540 de TVA = 3 540 ; 1 000 en Mobile Money.
        TicketCaisse t = caisse.vendre(caissier, null, panier(pain, 4, lait, 2), null,
                new Encaissement(5_000, 1_000, "MM-123", 0));
        assertEquals(3_540, t.total());
        assertEquals(2_540, t.especes());
        assertEquals(2_460, t.rendu());
        DocumentVente f = ventes.document(patron, t.documentId());
        assertEquals(t.numero(), f.numero());
        assertEquals(0, f.resteAPayer());
        assertEquals(VentesService.CLIENT_COMPTOIR, f.client().nom());
        assertEquals(List.of("ESPECES 2540@caisse", "MOBILE_MONEY 1000@momo"), tresorerie.journal);

        // Le compte juste : espèces reçues laissées à zéro. Le client comptoir est le même.
        TicketCaisse t2 = caisse.vendre(caissier, null, panier(pain, 1), null, new Encaissement(0, 0, "", 0));
        assertEquals(295, t2.especes());
        assertEquals(0, t2.rendu());
        assertEquals(f.client().id(), ventes.document(patron, t2.documentId()).client().id());
        assertEquals(1, ventes.clients(patron, "comptoir", false).size());

        CaisseService.Bilan b = caisse.bilan(caissier, s);
        assertEquals(2, b.tickets());
        assertEquals(3_835, b.total());
        assertEquals(10_000 + 2_540 + 295, b.attendu());
    }

    @Test
    void unTicketMalPayeNeLaisseRienDerriereLui() {
        caisse.ouvrir(caissier, 0, ESPECES, MOMO, null);
        int avant = ventes.documents(patron, Ports.Filtre.tous(100)).size();
        assertThrows(ErreurMetier.class, () -> caisse.vendre(caissier, null, panier(lait, 1), null,
                new Encaissement(1_000, 0, "", 0)), "il manque 180");
        assertThrows(ErreurMetier.class, () -> caisse.vendre(caissier, null, panier(lait, 1), null,
                new Encaissement(0, 500, "", 0)), "référence Mobile Money obligatoire");
        assertThrows(ErreurMetier.class, () -> caisse.vendre(caissier, null, panier(lait, 1), null,
                new Encaissement(0, 2_000, "MM", 0)), "plus que le total");
        assertThrows(ErreurMetier.class, () -> caisse.vendre(caissier, null, panier(lait, 1), null,
                new Encaissement(0, 0, "", 1_180)), "pas de compte prévu pour la carte");
        assertThrows(ErreurMetier.class, () -> caisse.vendre(caissier, null, Map.of(), null,
                new Encaissement(0, 0, "", 0)), "ticket vide");
        assertEquals(avant, ventes.documents(patron, Ports.Filtre.tous(100)).size());
    }

    @Test
    void laClotureCompteLesEspecesEtLaCaisseDeLaVeilleNeVendPlus() {
        SessionCaisse s = caisse.ouvrir(caissier, 5_000, ESPECES, null, null);
        caisse.vendre(caissier, null, panier(lait, 1), null, new Encaissement(2_000, 0, "", 0));

        horloge.maintenant = horloge.maintenant.plus(Duration.ofDays(1));
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> caisse.vendre(caissier, null, panier(pain, 1), null,
                new Encaissement(0, 0, "", 0)));
        assertTrue(e.getMessage().contains("clôturez"), e.getMessage());

        // Une autre caissière ne voit pas cette caisse ; la patronne, si.
        Acces adjo = caissier("Adjo");
        assertThrows(AccesRefuse.class, () -> caisse.session(adjo, s.id()));
        assertThrows(AccesRefuse.class, () -> caisse.cloturer(adjo, s.id(), 0, ""));
        assertTrue(caisse.sessions(adjo, 10).isEmpty());
        assertEquals(1, caisse.sessions(patron, 10).size());

        // 5 000 de fond + 1 180 encaissés = 6 180 attendus ; 6 000 comptés : 180 manquent.
        SessionCaisse close = caisse.cloturer(caissier, s.id(), 6_000, "Pièces de 25 introuvables");
        assertEquals(-180, close.ecart());
        assertThrows(ErreurMetier.class, () -> caisse.cloturer(caissier, s.id(), 6_000, ""), "déjà clôturée");
        assertTrue(caisse.ouverte(caissier).isEmpty());
        assertEquals("CA-2026-0002", caisse.ouvrir(caissier, 0, ESPECES, null, null).numero());
    }
}
