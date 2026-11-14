package com.venach.kauris.flotte;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Droit;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.flotte.application.FlotteService;
import com.venach.kauris.flotte.application.PermissionsFlotte;
import com.venach.kauris.flotte.application.PortsFlotte;
import com.venach.kauris.flotte.domain.Compte;
import com.venach.kauris.flotte.domain.Conducteur;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.DocumentVehicule;
import com.venach.kauris.flotte.domain.ModeVersement;
import com.venach.kauris.flotte.domain.StatutVehicule;
import com.venach.kauris.flotte.domain.TypeContrat;
import com.venach.kauris.flotte.domain.TypeDocument;
import com.venach.kauris.flotte.domain.TypeVehicule;
import com.venach.kauris.flotte.domain.Vehicule;
import com.venach.kauris.flotte.domain.Versement;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La flotte de bout en bout, en mémoire : contrats, caisse, versements, fériés, papiers, droits. */
class FlotteServiceTest {

    /** Samedi 14 novembre 2026. */
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 14);
    private static final LocalDate LUNDI_2 = LocalDate.of(2026, 11, 2);

    private final FlotteEnMemoire s = new FlotteEnMemoire();
    private final List<String> tresorerie = new ArrayList<>();
    private final PortsFlotte.Encaissements encaissements = new PortsFlotte.Encaissements() {
        @Override
        public List<Compte> comptes(UUID tenantId) {
            return List.of();
        }

        @Override
        public void versementEnregistre(Contrat c, Vehicule v, Conducteur d, Versement x, UUID compteId) {
            tresorerie.add("+" + x.montant() + " " + v.immatriculation());
        }

        @Override
        public void versementAnnule(Contrat c, Versement v) {
            tresorerie.add("-" + v.montant());
        }
    };
    private final FlotteService flotte = new FlotteService(s, s, s, s, s, s, s, s, encaissements,
            PortsFlotte.Demandes.AUCUN, UniteDeTravail.DIRECTE,
            Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC));
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Motos du Lac", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));
    private final Acces caissiere = new Acces(tenant, "Motos du Lac", true, UUID.randomUUID(), "Adjo",
            "adjo@exemple.test", Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)),
            Set.of(new Droit(PermissionsFlotte.CONSULTER, null), new Droit(PermissionsFlotte.ENCAISSER, null)),
            List.of());

    private Vehicule moto(String immat) {
        return flotte.enregistrerVehicule(patron, null, new Vehicule.Saisie(TypeVehicule.MOTO, immat, "Haojue", "",
                "", null, "", null, 650_000L, null, "", null, ""));
    }

    private Conducteur conducteur(String nom, LocalDate permis) {
        return flotte.enregistrerConducteur(patron, null, new Conducteur.Saisie(nom, "+228 90 00 00 00", "", "", "A",
                permis, "", "", true, ""));
    }

    private Contrat location(Vehicule v, Conducteur c) {
        return flotte.creerContrat(patron, v.id(), c.id(), TypeContrat.LOCATION, LUNDI_2, 5000,
                Set.of(DayOfWeek.SUNDAY), null, "");
    }

    @Test
    void laCaissiereEncaisseEtLaCaisseMontreLesRetardsDAbord() {
        Contrat koffi = location(moto("ab 1234 rt"), conducteur("Koffi", null));
        Contrat yao = location(moto("AC-5678"), conducteur("Yao", null));
        assertEquals("CT-2026-0001", koffi.numero());
        assertEquals("AB-1234-RT", flotte.vehicule(patron, koffi.vehiculeId()).immatriculation());

        Versement v = flotte.encaisser(caissiere, koffi.id(), 60000, ModeVersement.ESPECES, "", null, null);
        assertEquals("VR-2026-000001", v.numero());
        assertEquals("Adjo", v.saisiPar());
        assertEquals(List.of("+60000 AB-1234-RT"), tresorerie);

        List<FlotteService.LigneCaisse> caisse = flotte.caisse(caissiere);
        assertEquals(yao.id(), caisse.get(0).contrat().id(), "Yao doit 11 jours : en tête");
        assertEquals(55000, caisse.get(0).compte().montantEnRetard());
        assertEquals(Compte.Etat.A_JOUR, caisse.get(1).compte().etat());

        assertThrows(ErreurMetier.class, () -> flotte.encaisser(caissiere, koffi.id(), 5000, ModeVersement.ESPECES,
                "", AUJOURDHUI.plusDays(1), null), "pas de versement dans le futur");
        flotte.encaisser(caissiere, yao.id(), 5000, ModeVersement.MOBILE_MONEY, "MM-1", null, null);
        assertThrows(ErreurMetier.class, () -> flotte.encaisser(caissiere, yao.id(), 5000, ModeVersement.MOBILE_MONEY,
                "MM-1", null, null), "même transaction Mobile Money deux fois");
        assertThrows(ErreurMetier.class, () -> flotte.encaisser(caissiere, yao.id(), 5000, ModeVersement.MOBILE_MONEY,
                "", null, null), "référence obligatoire");

        // La caissière ne gère pas les contrats ni n'annule ; le patron annule avec un motif.
        assertThrows(AccesRefuse.class, () -> flotte.annulerVersement(caissiere, v.id(), "x"));
        flotte.annulerVersement(patron, v.id(), "Erreur de contrat");
        assertEquals(Compte.Etat.EN_RETARD, flotte.compte(patron, koffi.id()).etat());
        assertEquals("-60000", tresorerie.get(tresorerie.size() - 1));
    }

    @Test
    void unPaiementRecuParAliasNeSEnregistreQuUneFois() {
        Contrat c = location(moto("AB-1"), conducteur("Koffi", null));
        assertTrue(flotte.recevoir(tenant, c.id(), 5000, ModeVersement.PISPI, "TX-1", AUJOURDHUI, null).isPresent());
        assertTrue(flotte.recevoir(tenant, c.id(), 5000, ModeVersement.PISPI, "TX-1", AUJOURDHUI, null).isEmpty());
        assertEquals(5000, flotte.compte(patron, c.id()).totalPaye());
    }

    @Test
    void lesJoursFeriesDeclaresParLaCaissiereNeSontPlusDus() {
        Contrat c = location(moto("AB-1"), conducteur("Koffi", null));
        flotte.encaisser(caissiere, c.id(), 50000, ModeVersement.ESPECES, "", null, null);
        assertEquals(5000, flotte.compte(caissiere, c.id()).montantEnRetard());
        flotte.declarerFerie(caissiere, LocalDate.of(2026, 11, 11), "Fête de l'indépendance");
        assertEquals(0, flotte.compte(caissiere, c.id()).montantEnRetard());
        assertThrows(ErreurMetier.class, () -> flotte.declarerFerie(caissiere, LocalDate.of(2026, 11, 11), "Encore"));
        flotte.retirerFerie(caissiere, LocalDate.of(2026, 11, 11));
        assertEquals(5000, flotte.compte(caissiere, c.id()).montantEnRetard());
    }

    @Test
    void unVehiculeOuUnConducteurNAQuUnContratEnCours() {
        Vehicule v = moto("AB-1");
        Conducteur k = conducteur("Koffi", null);
        Contrat c = location(v, k);
        assertThrows(ErreurMetier.class, () -> location(v, conducteur("Yao", null)));
        assertThrows(ErreurMetier.class, () -> location(moto("AB-2"), k));
        assertThrows(ErreurMetier.class, () -> moto("ab 1"), "immatriculation déjà prise");
        assertThrows(ErreurMetier.class, () -> location(moto("AB-3"), conducteur("Ama", LocalDate.of(2026, 1, 1))),
                "permis expiré");
        assertThrows(AccesRefuse.class, () -> flotte.creerContrat(caissiere, v.id(), k.id(), TypeContrat.LOCATION,
                LUNDI_2, 5000, Set.of(), null, ""));
        assertThrows(ErreurMetier.class, () -> flotte.enregistrerConducteur(patron, k.id(), new Conducteur.Saisie(
                k.nom(), k.telephone(), "", "", "", null, "", "", false, "")), "archivé avec un contrat en cours");

        flotte.changerContrat(patron, c.id(), x -> x.terminer(AUJOURDHUI, true, "Départ"));
        Contrat nouveau = location(v, conducteur("Yao", null));
        assertThrows(ErreurMetier.class, () -> flotte.changerContrat(patron, c.id(), Contrat::rouvrir),
                "le véhicule a un autre contrat");
        assertEquals("CT-2026-0002", nouveau.numero());
    }

    @Test
    void laLocationVenteSoldeeTransfereLaPropriete() {
        Vehicule v = moto("AB-1");
        Contrat c = flotte.creerContrat(patron, v.id(), conducteur("Koffi", null).id(), TypeContrat.LOCATION_VENTE,
                LUNDI_2, 5000, Set.of(DayOfWeek.SUNDAY), 50000L, "");
        assertThrows(ErreurMetier.class, () -> flotte.transfererPropriete(patron, c.id()));
        flotte.encaisser(caissiere, c.id(), 50000, ModeVersement.ESPECES, "", null, null);
        assertEquals(Compte.Etat.SOLDE, flotte.compte(patron, c.id()).etat());
        flotte.transfererPropriete(patron, c.id());
        assertEquals(StatutVehicule.CEDE, flotte.vehicule(patron, v.id()).statut());
        assertTrue(flotte.caisse(caissiere).isEmpty());
    }

    @Test
    void lesAlertesNeGardentQueLeDernierPapierDeChaqueType() {
        Vehicule v = moto("AB-1");
        flotte.enregistrerDocument(patron, null, new DocumentVehicule.Saisie(v.id(), TypeDocument.ASSURANCE, "A1",
                null, LocalDate.of(2026, 11, 20), 30000L, ""));
        assertEquals(1, flotte.alertes(patron).papiers().size());
        flotte.enregistrerDocument(patron, null, new DocumentVehicule.Saisie(v.id(), TypeDocument.ASSURANCE, "A2",
                null, LocalDate.of(2027, 11, 20), 30000L, ""));
        assertEquals(0, flotte.alertes(patron).papiers().size(), "renouvelée : plus d'alerte");
        conducteur("Ama", LocalDate.of(2026, 12, 1));
        assertEquals(1, flotte.alertes(patron).permis().size());
    }

    @Test
    void sansBranchementLaDemandeDePaiementRenvoieAuMessage() {
        Contrat c = location(moto("AB-1"), conducteur("Koffi", null));
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> flotte.demanderPaiement(caissiere, c.id()));
        assertTrue(e.getMessage().contains("message"), e.getMessage());
    }
}
