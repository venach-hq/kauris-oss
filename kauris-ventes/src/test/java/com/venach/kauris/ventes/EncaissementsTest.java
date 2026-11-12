package com.venach.kauris.ventes;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.application.VentesService;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ce que l'application branche sur les encaissements : comptes de dépôt, actions, report des règlements. */
class EncaissementsTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);
    private static final UUID CAISSE = UUID.randomUUID();

    /** Une trésorerie imitée : un compte « Caisse », une action, et le journal des appels. */
    static class Tresorerie implements Ports.Encaissements {
        final List<String> journal = new ArrayList<>();

        @Override
        public List<Compte> comptes(UUID tenantId) {
            return List.of(new Compte(CAISSE, "Caisse"));
        }

        @Override
        public List<Action> actions(Acces acces, DocumentVente facture) {
            return List.of(new Action("Demander par PI-SPI", "/tresorerie/demandes/" + facture.id(), ""));
        }

        @Override
        public void reglementEnregistre(DocumentVente facture, Reglement r) {
            journal.add("+" + r.montant() + "@" + r.compteId());
        }

        @Override
        public void reglementAnnule(DocumentVente facture, Reglement r) {
            journal.add("-" + r.montant() + "@" + r.compteId());
        }
    }

    private final EnMemoire stockage = new EnMemoire();
    private final Tresorerie tresorerie = new Tresorerie();
    private final VentesService ventes = new VentesService(stockage, stockage, stockage, stockage, stockage,
            tresorerie, UniteDeTravail.DIRECTE, Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC));
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Entreprise", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));

    private DocumentVente facture(long prix) {
        Client c = ventes.creerClient(patron, new Client.Etat(null, null, Client.Type.ENTREPRISE,
                "Client " + UUID.randomUUID(), "", "", "", "", "", "", "", "", 0, "", true, null));
        DocumentVente f = ventes.creerBrouillon(patron, TypeDocument.FACTURE, c.id(), new DocumentVente.Contenu(JOUR,
                JOUR, "", "", List.of(new Ligne(null, "Service", BigDecimal.ONE, "", prix, null, null)), null, ""));
        return ventes.emettre(patron, f.id());
    }

    @Test
    void unReglementVaDansLeCompteChoisiEtSonAnnulationAussi() {
        DocumentVente f = facture(10_000);
        assertThrows(ErreurMetier.class, () -> ventes.encaisser(patron, f.id(), JOUR, 4_000, ModeReglement.ESPECES,
                "", null), "le compte est demandé quand l'application en tient");
        assertThrows(ErreurMetier.class, () -> ventes.encaisser(patron, f.id(), JOUR, 4_000, ModeReglement.ESPECES,
                "", UUID.randomUUID()), "un compte inconnu est refusé");
        Reglement r = ventes.encaisser(patron, f.id(), JOUR, 4_000, ModeReglement.ESPECES, "", CAISSE);
        ventes.annulerReglement(patron, r.id(), "Erreur");
        assertEquals(List.of("+4000@" + CAISSE, "-4000@" + CAISSE), tresorerie.journal);
        assertEquals(1, ventes.actionsDEncaissement(patron, ventes.document(patron, f.id())).size());
    }

    @Test
    void unPaiementConstateParLApplicationEstRameneAuResteAPayer() {
        DocumentVente f = facture(10_000);
        ventes.encaisser(patron, f.id(), JOUR, 7_000, ModeReglement.ESPECES, "", CAISSE);
        Optional<Reglement> r = ventes.encaisserAutomatiquement(tenant, f.id(), JOUR, 5_000, ModeReglement.PISPI,
                "E2E-1", CAISSE);
        assertEquals(3_000, r.orElseThrow().montant());
        assertEquals(0, ventes.document(patron, f.id()).resteAPayer());
        assertTrue(ventes.encaisserAutomatiquement(tenant, f.id(), JOUR, 5_000, ModeReglement.PISPI, "E2E-2",
                CAISSE).isEmpty(), "facture soldée : rien de plus");
        assertTrue(ventes.actionsDEncaissement(patron, ventes.document(patron, f.id())).isEmpty(),
                "plus rien à demander");
    }
}
