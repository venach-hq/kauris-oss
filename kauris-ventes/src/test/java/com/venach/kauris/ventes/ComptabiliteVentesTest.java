package com.venach.kauris.ventes;

import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.comptabilite.Comptabilisation;
import com.venach.kauris.kernel.comptabilite.OperationComptable;
import com.venach.kauris.kernel.comptabilite.OperationComptable.Role;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.DocumentVente.Contenu;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.domain.Reglement;
import com.venach.kauris.ventes.domain.TypeDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ce que les ventes transmettent à la comptabilité : factures, avoirs, règlements et leurs annulations. */
class ComptabiliteVentesTest {

    /** Une comptabilité qui note ce qu'elle reçoit. */
    static final class Recue implements Comptabilisation {
        final List<OperationComptable> operations = new ArrayList<>();
        final List<String> annulations = new ArrayList<>();

        @Override
        public void enregistrer(OperationComptable o) {
            operations.add(o);
        }

        @Override
        public void annuler(UUID tenantId, String origine, String reference, LocalDate date, String motif) {
            annulations.add(origine + "/" + reference);
        }
    }

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);
    private final EnMemoire stockage = new EnMemoire();
    private final VentesService ventes = new VentesService(stockage, stockage, stockage, stockage, stockage,
            UniteDeTravail.DIRECTE, Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC));
    private final Recue compta = new Recue();
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Entreprise", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(com.venach.kauris.kernel.organisation.domain.Role.PROPRIETAIRE)));

    private static long montant(OperationComptable o, Role role, boolean debit) {
        return o.lignes().stream().filter(l -> l.role() == role).mapToLong(l -> debit ? l.debit() : l.credit()).sum();
    }

    @Test
    void factureReglementAvoir() {
        ventes.brancherComptabilite(compta);
        Client c = ventes.creerClient(patron, new Client.Etat(null, null, Client.Type.ENTREPRISE, "Ets Lawson", "",
                "", "", "", "Lomé", "Togo", "", "", 30, "", true, null));
        Article cable = ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.BIEN, "C1", "Câble",
                "m", 15_000, new BigDecimal("18"), false, null, true, null));
        Article pose = ventes.creerArticle(patron, new Article.Etat(null, null, Article.Type.SERVICE, "P1", "Pose",
                "u", 50_000, new BigDecimal("18"), false, null, true, null));
        DocumentVente f = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(), new Contenu(JOUR, JOUR.plusDays(30),
                "Chantier", "", List.of(
                new Ligne(cable.id(), "Câble", new BigDecimal("10"), "m", 15_000, BigDecimal.ZERO, new BigDecimal("18")),
                new Ligne(pose.id(), "Pose", BigDecimal.ONE, "u", 50_000, BigDecimal.ZERO, new BigDecimal("18")),
                new Ligne(null, "Déplacement", BigDecimal.ONE, "u", 10_000, BigDecimal.ZERO, BigDecimal.ZERO)),
                BigDecimal.TEN, ""));
        f = ventes.emettre(patron, f.id());

        // 210 000 HT − 10 % = 189 000 HT ; TVA 18 % sur 180 000 = 32 400 ; TTC 221 400.
        OperationComptable o = compta.operations.get(0);
        assertEquals("FACTURE:" + f.id(), o.reference());
        assertEquals("FA-2026-0001", o.piece());
        assertEquals(OperationComptable.Journal.VENTES, o.journal());
        assertEquals(221_400, montant(o, Role.CLIENT, true));
        assertEquals("CLI:" + c.id(), o.lignes().get(0).tiers());
        assertEquals(135_000, montant(o, Role.VENTE_MARCHANDISES, false), "câble : 150 000 − 10 %");
        assertEquals(54_000, montant(o, Role.VENTE_SERVICES, false), "pose et déplacement : 60 000 − 10 %");
        assertEquals(32_400, montant(o, Role.TVA_COLLECTEE, false));
        assertTrue(o.lignes().stream().anyMatch(l -> l.role() == Role.TVA_COLLECTEE && l.precision().equals("18")));

        Reglement r = ventes.encaisser(patron, f.id(), JOUR, 100_000, ModeReglement.ESPECES, "");
        OperationComptable reg = compta.operations.get(1);
        assertEquals("REGLEMENT:" + r.id(), reg.reference());
        assertEquals("FA-2026-0001", reg.piece(), "même pièce que la facture, pour le lettrage");
        assertEquals("ESPECES", reg.lignes().get(0).precision());
        assertEquals(100_000, montant(reg, Role.TRESORERIE, true));

        ventes.annulerReglement(patron, r.id(), "erreur");
        assertEquals(List.of("ventes/REGLEMENT:" + r.id()), compta.annulations);

        DocumentVente avoir = ventes.emettre(patron, ventes.avoirSur(patron, f.id()).id());
        OperationComptable av = compta.operations.get(2);
        assertEquals("AVOIR:" + avoir.id(), av.reference());
        assertEquals("FA-2026-0001", av.piece());
        assertEquals(221_400, montant(av, Role.CLIENT, false), "l'avoir crédite le client");
        assertEquals(32_400, montant(av, Role.TVA_COLLECTEE, true));
        assertEquals(3, compta.operations.size());
    }
}
