package com.venach.kauris.paie;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.entreprise.domain.Pays;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.paie.application.PaieService;
import com.venach.kauris.paie.application.PortsPaie;
import com.venach.kauris.paie.domain.Bulletin;
import com.venach.kauris.paie.domain.ContratTravail;
import com.venach.kauris.paie.domain.Element;
import com.venach.kauris.paie.domain.ModePaiement;
import com.venach.kauris.paie.domain.Parametre;
import com.venach.kauris.paie.domain.Parametres;
import com.venach.kauris.paie.domain.PeriodePaie;
import com.venach.kauris.paie.domain.Salarie;
import com.venach.kauris.paie.domain.SituationFamiliale;
import com.venach.kauris.paie.domain.TypeContrat;
import com.venach.kauris.paie.domain.TypeElement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La paie de bout en bout, en mémoire : salariés, contrats, mois, clôture, paramètres, droits. */
class PaieServiceTest {

    /** Mercredi 18 novembre 2026. */
    private static final YearMonth NOVEMBRE = YearMonth.of(2026, 11);

    private final PaieEnMemoire s = new PaieEnMemoire();
    private final AtomicReference<Pays> pays = new AtomicReference<>(Pays.TOGO);
    private final List<String> verses = new ArrayList<>();
    private final PortsPaie.Versements versements = new PortsPaie.Versements() {
        @Override
        public boolean disponible(UUID tenantId) {
            return true;
        }

        @Override
        public String verser(Acces a, PeriodePaie p, List<Bulletin> b) {
            b.forEach(x -> verses.add(x.nomSalarie() + " " + x.net()));
            return b.size() + " salaire(s) envoyé(s)";
        }
    };
    private final PaieService paie = new PaieService(s, s, s, s, s, s, s, t -> Optional.ofNullable(pays.get()),
            versements, UniteDeTravail.DIRECTE, Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
    private final UUID tenant = UUID.randomUUID();
    private final Acces patron = new Acces(tenant, "Boulangerie du Port", true, UUID.randomUUID(), "Awa", "awa@exemple.test",
            Set.of(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));
    private final Acces gestionnaire = new Acces(tenant, "Boulangerie du Port", true, UUID.randomUUID(), "Kodjo",
            "kodjo@exemple.test", Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));

    private static Salarie.Saisie fiche(String nom, SituationFamiliale situation, int enfants, ModePaiement mode,
                                        String compte) {
        return new Salarie.Saisie(nom, "Akosua", LocalDate.of(1990, 3, 4), "+228 90 00 00 01", "", "Lomé", "",
                "CNSS-" + nom, situation, enfants, false, false, mode, "Ecobank", compte, true, "");
    }

    private static ContratTravail.Saisie cdi(String poste, long salaire, LocalDate debut, Element... elements) {
        return new ContratTravail.Saisie(TypeContrat.CDI, poste, "", debut, null, salaire, List.of(elements), "");
    }

    @Test
    void moisDePaieComplet() {
        Salarie awa = paie.enregistrerSalarie(patron, null, fiche("Mensah", SituationFamiliale.CELIBATAIRE, 0,
                ModePaiement.VIREMENT, "TG001 0000 1234"));
        Salarie kofi = paie.enregistrerSalarie(patron, null, fiche("Agbeko", SituationFamiliale.MARIE, 2,
                ModePaiement.ESPECES, ""));
        assertEquals("S-0001", awa.matricule());
        assertEquals("MENSAH", awa.nom());
        paie.creerContrat(patron, awa.id(), cdi("Comptable", 300_000, LocalDate.of(2025, 1, 1)));
        // Kofi arrive le 16 novembre, avec une prime de transport non soumise.
        ContratTravail ck = paie.creerContrat(patron, kofi.id(), cdi("Vendeur", 120_000, LocalDate.of(2026, 11, 16),
                new Element("Transport", TypeElement.GAIN_NON_SOUMIS, 10_000)));
        assertEquals("CTR-2026-0001", ck.numero());
        assertThrows(ErreurMetier.class, () -> paie.creerContrat(patron, awa.id(), cdi("Directrice", 500_000,
                LocalDate.of(2026, 12, 1))), "un seul contrat à la fois");

        PeriodePaie p = paie.ouvrirMois(patron, NOVEMBRE);
        assertThrows(ErreurMetier.class, () -> paie.ouvrirMois(patron, NOVEMBRE), "un mois n'existe qu'une fois");
        assertThrows(ErreurMetier.class, () -> paie.ouvrirMois(patron, YearMonth.of(2027, 2)), "trop loin");

        PaieService.Mois m = paie.mois(patron, p.id());
        assertEquals(2, m.bulletins().size());
        Bulletin bAwa = paie.bulletinDe(patron, p.id(), awa.id());
        assertTrue(bAwa.brouillon());
        assertEquals(269_355, bAwa.net());
        Bulletin bKofi = paie.bulletinDe(patron, p.id(), kofi.id());
        assertEquals(15, bKofi.joursPayes());
        assertEquals(60_000 + 10_000, bKofi.resultat().brut(), "moitié du mois et transport");

        // Les éléments du mois : 2 jours d'absence et une avance pour Awa.
        paie.enregistrerVariables(patron, p.id(), awa.id(), 2, List.of(new Element("Avance du 5",
                TypeElement.RETENUE, 50_000)));
        bAwa = paie.bulletinDe(patron, p.id(), awa.id());
        assertEquals(28, bAwa.joursPayes());
        assertEquals(280_000, bAwa.resultat().brut());
        assertEquals(50_000, bAwa.resultat().retenues());
        assertThrows(ErreurMetier.class, () -> paie.enregistrerVariables(patron, p.id(), kofi.id(), 0,
                List.of(new Element("Avance", TypeElement.RETENUE, 500_000))), "net négatif refusé à la saisie");

        // Un gestionnaire n'a pas accès aux salaires.
        assertThrows(AccesRefuse.class, () -> paie.mois(gestionnaire, p.id()));
        assertThrows(AccesRefuse.class, () -> paie.salaries(gestionnaire));

        // Clôture : bulletins numérotés et figés.
        PeriodePaie close = paie.clore(patron, p.id());
        assertTrue(close.close());
        List<Bulletin> figes = paie.mois(patron, p.id()).bulletins();
        assertEquals(List.of("BP-202611-0001", "BP-202611-0002"), figes.stream().map(Bulletin::numero).toList());
        long netAvant = paie.bulletinDe(patron, p.id(), awa.id()).net();

        // Un contrat qui change ne touche plus le mois clos ; les changements datés dans ce mois sont refusés.
        ContratTravail ca = paie.contratsDuSalarie(patron, awa.id()).get(0);
        paie.corrigerContrat(patron, ca.id(), cdi("Comptable", 350_000, LocalDate.of(2025, 1, 1)));
        assertEquals(netAvant, paie.bulletinDe(patron, p.id(), awa.id()).net());
        assertThrows(ErreurMetier.class, () -> paie.terminerContrat(patron, ck.id(), LocalDate.of(2026, 11, 30),
                "Fin"), "mois clos");
        assertThrows(ErreurMetier.class, () -> paie.enregistrerVariables(patron, p.id(), awa.id(), 0, List.of()));

        // Versement : seuls les salaires par virement partent.
        assertEquals("1 salaire(s) envoyé(s)", paie.verser(patron, p.id()));
        assertEquals(List.of("MENSAH Akosua " + netAvant), verses);

        // Réouverture : les bulletins redeviennent des brouillons, recalculés avec le nouveau salaire.
        paie.rouvrir(patron, p.id());
        assertTrue(s.bulletins.isEmpty());
        assertTrue(paie.bulletinDe(patron, p.id(), awa.id()).net() > netAvant);
        paie.clore(patron, p.id());
        assertEquals("BP-202611-0004", paie.bulletinDe(patron, p.id(), awa.id()).numero(),
                "numéros jamais réutilisés, par ordre alphabétique (AGBEKO, puis MENSAH)");

        // Payé : le mois ne se rouvre plus.
        paie.marquerPaye(patron, p.id(), LocalDate.of(2026, 11, 18));
        assertThrows(ErreurMetier.class, () -> paie.rouvrir(patron, p.id()));
        assertThrows(ErreurMetier.class, () -> paie.marquerPaye(patron, p.id(), null));
        assertEquals(2, paie.bulletinsDuSalarie(patron, awa.id()).size() + paie.bulletinsDuSalarie(patron,
                kofi.id()).size());
    }

    @Test
    void sortieEnCoursDeMois() {
        Salarie s1 = paie.enregistrerSalarie(patron, null, fiche("Dossou", SituationFamiliale.CELIBATAIRE, 0,
                ModePaiement.ESPECES, ""));
        ContratTravail c = paie.creerContrat(patron, s1.id(), cdi("Gardien", 90_000, LocalDate.of(2026, 1, 1)));
        Salarie.Saisie archive = new Salarie.Saisie("Dossou", "", null, "", "", "", "", "",
                SituationFamiliale.CELIBATAIRE, 0, false, false, ModePaiement.ESPECES, "", "", false, "");
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, s1.id(), archive),
                "contrat en cours : la fiche ne s'archive pas");
        paie.terminerContrat(patron, c.id(), LocalDate.of(2026, 11, 10), "Démission");
        assertFalse(paie.enregistrerSalarie(patron, s1.id(), archive).actif(), "parti le 10 : archivé");
        PeriodePaie p = paie.ouvrirMois(patron, NOVEMBRE);
        assertEquals(30_000, paie.bulletinDe(patron, p.id(), s1.id()).resultat().brut(), "10 jours sur 30, archivé "
                + "ou non");
        PeriodePaie decembre = paie.ouvrirMois(patron, NOVEMBRE.plusMonths(1));
        assertTrue(paie.mois(patron, decembre.id()).bulletins().isEmpty());
        assertTrue(paie.alertes(patron).aCloturer().isEmpty(), "novembre n'est pas encore passé");
        assertThrows(ErreurMetier.class, () -> paie.clore(patron, decembre.id()), "rien à clôturer");
    }

    @Test
    void coteDIvoireEtCorrections() {
        pays.set(Pays.COTE_D_IVOIRE);
        Salarie a = paie.enregistrerSalarie(patron, null, fiche("Kouassi", SituationFamiliale.MARIE, 2,
                ModePaiement.MOBILE_MONEY, "07 00 00 00 00"));
        paie.creerContrat(patron, a.id(), cdi("Commercial", 500_000, LocalDate.of(2024, 5, 1)));
        PeriodePaie p = paie.ouvrirMois(patron, NOVEMBRE);
        assertEquals("CMU", paie.bulletinDe(patron, p.id(), a.id()).lignes().stream()
                .filter(l -> l.organisme().equals("CMU")).findFirst().orElseThrow().organisme());
        assertEquals(407_500, paie.bulletinDe(patron, p.id(), a.id()).net());

        // Taux d'accident du travail de l'entreprise : 3 % à partir de novembre.
        long avant = paie.bulletinDe(patron, p.id(), a.id()).resultat().chargesEmployeur();
        paie.corriger(patron, new Parametres.Correction(tenant, Parametre.CI_ACCIDENT_EMPLOYEUR,
                LocalDate.of(2026, 11, 1), new BigDecimal("3"), "Notification CNPS"));
        assertEquals(avant + 700, paie.bulletinDe(patron, p.id(), a.id()).resultat().chargesEmployeur());
        assertThrows(ErreurMetier.class, () -> paie.corriger(patron, new Parametres.Correction(tenant,
                Parametre.CI_ACCIDENT_EMPLOYEUR, LocalDate.of(2026, 11, 15), new BigDecimal("3"), "")));
        assertThrows(AccesRefuse.class, () -> paie.corriger(gestionnaire, new Parametres.Correction(tenant,
                Parametre.CI_ACCIDENT_EMPLOYEUR, LocalDate.of(2026, 12, 1), new BigDecimal("4"), "")));
        paie.retirerCorrection(patron, Parametre.CI_ACCIDENT_EMPLOYEUR, LocalDate.of(2026, 11, 1));
        assertEquals(avant, paie.bulletinDe(patron, p.id(), a.id()).resultat().chargesEmployeur());
    }

    @Test
    void sansPaysGere() {
        pays.set(Pays.SENEGAL);
        assertTrue(paie.pays(tenant).isEmpty());
        ErreurMetier e = assertThrows(ErreurMetier.class, () -> paie.ouvrirMois(patron, NOVEMBRE));
        assertTrue(e.getMessage().contains("Togo"));
        pays.set(null);
        assertFalse(paie.pays(tenant).isPresent());
    }

    @Test
    void fichesInvalides() {
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, null, fiche("X",
                SituationFamiliale.CELIBATAIRE, 0, ModePaiement.ESPECES, "")), "nom trop court");
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, null, fiche("Amouzou",
                SituationFamiliale.CELIBATAIRE, 0, ModePaiement.VIREMENT, "")), "virement sans compte");
        paie.enregistrerSalarie(patron, null, fiche("Amouzou", SituationFamiliale.CELIBATAIRE, 0,
                ModePaiement.ESPECES, ""));
        assertThrows(ErreurMetier.class, () -> paie.enregistrerSalarie(patron, null, fiche("Amouzou",
                SituationFamiliale.CELIBATAIRE, 0, ModePaiement.ESPECES, "")), "même numéro CNSS");
    }
}
