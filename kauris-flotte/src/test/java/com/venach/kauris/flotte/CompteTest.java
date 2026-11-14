package com.venach.kauris.flotte;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.flotte.domain.Ajustement;
import com.venach.kauris.flotte.domain.Compte;
import com.venach.kauris.flotte.domain.Compte.Etat;
import com.venach.kauris.flotte.domain.Contrat;
import com.venach.kauris.flotte.domain.ModeVersement;
import com.venach.kauris.flotte.domain.StatutContrat;
import com.venach.kauris.flotte.domain.TypeContrat;
import com.venach.kauris.flotte.domain.Versement;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le compte du conducteur : jours dus, repos, fériés, pauses, paiements du plus ancien au plus récent. */
class CompteTest {

    private static final UUID T = UUID.randomUUID();
    /** Samedi. */
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 14);
    private static final LocalDate LUNDI_2 = LocalDate.of(2026, 11, 2);

    private final Map<LocalDate, String> feries = new HashMap<>();
    private final List<Versement> versements = new ArrayList<>();
    private final List<Ajustement> ajustements = new ArrayList<>();

    static Contrat contrat(TypeContrat type, LocalDate debut, long parJour, Long prix) {
        return new Contrat(UUID.randomUUID(), T, "CT-2026-0001", UUID.randomUUID(), UUID.randomUUID(), type, debut,
                null, List.of(new Contrat.Tarif(debut, parJour)), Set.of(DayOfWeek.SUNDAY), List.of(), prix, null, "",
                "", Instant.EPOCH);
    }

    private void payer(LocalDate jour, long montant) {
        versements.add(new Versement(UUID.randomUUID(), T, UUID.randomUUID(), "VR-" + versements.size(), jour, montant,
                ModeVersement.ESPECES, "", "Caissière", Instant.EPOCH, false, "", "", null));
    }

    private Compte.Situation compte(Contrat c) {
        return Compte.calculer(c, feries, versements, ajustements, AUJOURDHUI);
    }

    @Test
    void lesPaiementsCouvrentLesJoursDansLOrdreEtLeJourMemeNEstPasEnRetard() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null);
        payer(LocalDate.of(2026, 11, 6), 30000);
        payer(LocalDate.of(2026, 11, 10), 10000);
        Compte.Situation s = compte(c);
        // 12 jours dus (dimanches de repos), 8 payés : du 2 au 7, le 9 et le 10.
        assertEquals(60000, s.totalDu());
        assertEquals(LocalDate.of(2026, 11, 10), s.payeJusquAu());
        assertEquals(3, s.joursEnRetard());
        assertEquals(15000, s.montantEnRetard());
        assertEquals(5000, s.resteAujourdhui());
        assertEquals(20000, s.aEncaisser());
        assertEquals(Etat.EN_RETARD, s.etat());
        assertEquals(-20000, s.solde());
    }

    @Test
    void unPaiementPartielNeCouvrePasLeJourEtLeResteEstReporte() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null);
        payer(LocalDate.of(2026, 11, 3), 7000);
        Compte.Situation s = compte(c);
        assertEquals(LUNDI_2, s.payeJusquAu());
        assertEquals(10, s.joursEnRetard(), "le 3 n'est payé qu'à moitié : il compte en retard");
        assertEquals(48000, s.montantEnRetard());
    }

    @Test
    void joursFeriesEtPausesNeSontPasDus() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null)
                .ajouterPause(new Contrat.Pause(UUID.randomUUID(), LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 13), "Moto au garage", false));
        feries.put(LocalDate.of(2026, 11, 11), "Fête");
        payer(LocalDate.of(2026, 11, 10), 40000);
        Compte.Situation s = compte(c);
        assertEquals(45000, s.totalDu());
        assertEquals(0, s.joursEnRetard());
        assertEquals(Etat.A_JOUR, s.etat());
        assertEquals(5000, s.aEncaisser(), "seulement le versement du jour");
        assertTrue(s.calendrier().stream().anyMatch(j -> j.date().equals(LocalDate.of(2026, 11, 11))
                && j.statut().equals("FERIE") && j.detail().equals("Fête")));
    }

    @Test
    void unePauseALaChargeDuConducteurResteDue() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null)
                .ajouterPause(new Contrat.Pause(UUID.randomUUID(), LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 13), "Accident par sa faute", true));
        assertEquals(60000, compte(c).totalDu());
    }

    @Test
    void unPaiementDAvanceCouvreLesJoursSuivantsSansCompterLeDimanche() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null);
        payer(AUJOURDHUI, 75000);
        Compte.Situation s = compte(c);
        assertEquals(LocalDate.of(2026, 11, 18), s.payeJusquAu());
        assertEquals(Etat.EN_AVANCE, s.etat());
        assertEquals(0, s.aEncaisser());
        assertEquals(15000, s.solde());
    }

    @Test
    void unNouveauTarifNeChangePasLesJoursPasses() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null).changerTarif(LocalDate.of(2026, 11, 9), 6000);
        assertEquals(6 * 5000 + 6 * 6000, compte(c).totalDu());
        assertEquals(6000, compte(c).tarifDuJour());
    }

    @Test
    void lesAutresSommesSePaientDansLOrdreDesDates() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null);
        ajustements.add(new Ajustement(UUID.randomUUID(), T, c.id(), LocalDate.of(2026, 11, 3),
                Ajustement.Type.AMENDE, 3000, "Stationnement", "", Instant.EPOCH, false, ""));
        ajustements.add(new Ajustement(UUID.randomUUID(), T, c.id(), LocalDate.of(2026, 11, 4),
                Ajustement.Type.REMISE, 1000, "Geste", "", Instant.EPOCH, false, ""));
        payer(LUNDI_2, 12000);
        Compte.Situation s = compte(c);
        // 13 000 de paiements et remise : jours du 2 et du 3 (10 000), amende (3 000) ; le 4 n'est pas couvert.
        assertEquals(LocalDate.of(2026, 11, 3), s.payeJusquAu());
        assertEquals(63000 - 13000, s.montantEnRetard() + s.resteAujourdhui());
    }

    @Test
    void unVersementAnnuleNeCompteplus() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null);
        payer(LUNDI_2, 5000);
        versements.set(0, versements.get(0).annuler("Doublon", "Gérant", Instant.EPOCH));
        assertEquals(0, compte(c).totalPaye());
        assertThrows(ErreurMetier.class, () -> versements.get(0).annuler("encore", "x", Instant.EPOCH));
    }

    @Test
    void unRetardDePlusDUnMoisSeCompteEnJoursEtPasEnJoursDuMois() {
        // L'ancien Kauris disait « 4 jours » pour 1 mois et 3 jours de retard (Period.getDays).
        Contrat c = contrat(TypeContrat.LOCATION, LocalDate.of(2026, 9, 1), 5000, null);
        Compte.Situation s = compte(c);
        assertEquals(64, s.joursEnRetard(), "du mardi 1er septembre au vendredi 13 novembre, sans les dimanches");
        assertEquals(64 * 5000, s.montantEnRetard());
    }

    @Test
    void laLocationVenteSArreteAuTotalEtLesJoursRatesRepoussentLaFin() {
        Contrat c = contrat(TypeContrat.LOCATION_VENTE, LUNDI_2, 5000, 100000L);
        payer(AUJOURDHUI, 60000);
        Compte.Situation s = compte(c);
        assertEquals(40000, s.resteSurPrix());
        assertEquals(8, s.versementsRestants());
        assertEquals(LocalDate.of(2026, 11, 24), s.finEstimee(), "du lundi 16 au mardi 24, sans le dimanche");

        versements.clear();
        Compte.Situation enRetard = compte(c);
        assertEquals(20, enRetard.versementsRestants());
        assertEquals(Etat.EN_RETARD, enRetard.etat());

        payer(AUJOURDHUI, 100000);
        Compte.Situation solde = compte(c);
        assertEquals(Etat.SOLDE, solde.etat());
        assertEquals(0, solde.resteSurPrix());
        assertEquals(0, solde.versementsRestants());
    }

    @Test
    void laLocationVenteNeDoitPlusRienAuDelaDuTotal() {
        Contrat c = contrat(TypeContrat.LOCATION_VENTE, LocalDate.of(2026, 9, 1), 5000, 52000L);
        Compte.Situation s = compte(c);
        assertEquals(52000, s.totalDu(), "10 jours pleins et 2 000 le 11e");
    }

    @Test
    void unContratTermineNeDoitPlusRienApresSaFin() {
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null)
                .terminer(LocalDate.of(2026, 11, 7), true, "Conducteur parti");
        assertEquals(StatutContrat.RESILIE, c.statut());
        assertEquals(30000, compte(c).totalDu());
        assertEquals(Etat.EN_RETARD, compte(c).etat());
        payer(AUJOURDHUI, 30000);
        assertEquals(Etat.TERMINE, compte(c).etat());
    }

    @Test
    void unSalarieNeVersePas() {
        Contrat c = contrat(TypeContrat.SALARIE, LUNDI_2, 5000, null);
        assertEquals(Etat.SANS_VERSEMENT, compte(c).etat());
        assertEquals(0, compte(c).totalDu());
    }

    @Test
    void lesContratsMalSaisisSontRefuses() {
        assertThrows(ErreurMetier.class, () -> contrat(TypeContrat.LOCATION_VENTE, LUNDI_2, 5000, null));
        assertThrows(ErreurMetier.class, () -> contrat(TypeContrat.LOCATION, LUNDI_2, 0, null));
        Contrat c = contrat(TypeContrat.LOCATION, LUNDI_2, 5000, null).ajouterPause(new Contrat.Pause(
                UUID.randomUUID(), LocalDate.of(2026, 11, 10), null, "Garage", false));
        assertThrows(ErreurMetier.class, () -> c.ajouterPause(new Contrat.Pause(UUID.randomUUID(),
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), "Autre", false)));
        assertThrows(ErreurMetier.class, () -> c.changerTarif(LocalDate.of(2026, 10, 1), 6000));
        Contrat repris = c.finirPause(c.pauses().get(0).id(), LocalDate.of(2026, 11, 11));
        assertEquals(Etat.EN_RETARD, compte(repris).etat());
        // Garage depuis le 10 : seuls les 7 jours du 2 au 9 sont dus.
        assertEquals(7, compte(c).joursEnRetard());
    }
}
