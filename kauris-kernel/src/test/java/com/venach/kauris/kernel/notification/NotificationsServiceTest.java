package com.venach.kauris.kernel.notification;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.notification.adapter.out.AvertirMembresParNotification;
import com.venach.kauris.kernel.notification.adapter.out.AvisJson;
import com.venach.kauris.kernel.notification.application.port.out.BoiteDEnvoi;
import com.venach.kauris.kernel.notification.application.port.out.NotificationRepository;
import com.venach.kauris.kernel.notification.application.service.NotificationsService;
import com.venach.kauris.kernel.notification.application.service.RelaisService;
import com.venach.kauris.kernel.notification.domain.Avis;
import com.venach.kauris.kernel.notification.domain.Notification;
import com.venach.kauris.kernel.organisation.Monde;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.service.MembresService;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsServiceTest {

    /** Horloge que le test avance à la main. */
    private static final class Horloge extends Clock {

        private Instant maintenant = Instant.parse("2026-11-11T08:00:00Z");

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public Instant instant() {
            return maintenant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class Stock implements NotificationRepository {

        private final Map<UUID, Notification> parId = new LinkedHashMap<>();

        public void sauvegarder(Notification n) {
            parId.put(n.id(), n);
        }

        public List<Notification> recentes(UUID tenantId, UUID membreId, int max) {
            return parId.values().stream()
                    .filter(n -> n.tenantId().equals(tenantId) && n.membreId().equals(membreId))
                    .sorted(Comparator.comparing(Notification::creeLe).reversed()).limit(max).toList();
        }

        public long compterNonLues(UUID tenantId, UUID membreId) {
            return recentes(tenantId, membreId, Integer.MAX_VALUE).stream().filter(n -> !n.estLue()).count();
        }

        public Optional<Notification> trouver(UUID tenantId, UUID membreId, UUID id) {
            return Optional.ofNullable(parId.get(id))
                    .filter(n -> n.tenantId().equals(tenantId) && n.membreId().equals(membreId));
        }

        public void marquerToutesLues(UUID tenantId, UUID membreId, Instant maintenant) {
            recentes(tenantId, membreId, Integer.MAX_VALUE).forEach(n -> n.marquerLue(maintenant));
        }
    }

    private static final class Boite implements BoiteDEnvoi {

        private static final class Ligne {
            final UUID id = UUID.randomUUID();
            Avis avis;
            int tentatives;
            Instant prochainEssai;
            Instant envoyeLe;
            String erreur;
        }

        private final List<Ligne> lignes = new ArrayList<>();

        public void deposer(Avis avis, Instant maintenant) {
            Ligne l = new Ligne();
            l.avis = avis;
            l.prochainEssai = maintenant;
            lignes.add(l);
        }

        public List<Depot> aEnvoyer(Instant maintenant, int max) {
            return lignes.stream().filter(l -> l.envoyeLe == null && !l.prochainEssai.isAfter(maintenant))
                    .limit(max).map(l -> new Depot(l.id, l.avis, l.tentatives)).toList();
        }

        private Ligne ligne(UUID id) {
            return lignes.stream().filter(l -> l.id.equals(id)).findFirst().orElseThrow();
        }

        public void marquerEnvoye(UUID depotId, Instant maintenant) {
            ligne(depotId).envoyeLe = maintenant;
        }

        public void marquerEchec(UUID depotId, String erreur, Instant prochainEssai) {
            Ligne l = ligne(depotId);
            l.tentatives++;
            l.erreur = erreur;
            l.prochainEssai = prochainEssai;
        }

        public int purgerEnvoyesAvant(Instant limite) {
            int avant = lignes.size();
            lignes.removeIf(l -> l.envoyeLe != null && l.envoyeLe.isBefore(limite));
            return avant - lignes.size();
        }
    }

    private final Monde monde = new Monde();
    private final Horloge horloge = new Horloge();
    private final Stock stock = new Stock();
    private final Boite boite = new Boite();
    private final NotificationsService service = new NotificationsService(stock, boite, monde.membresStockes,
            UniteDeTravail.DIRECTE, horloge);
    private final List<Avis> partis = new ArrayList<>();
    private boolean transportEnPanne;
    private final RelaisService relais = new RelaisService(boite, avis -> {
        if (transportEnPanne) {
            throw new IllegalStateException("courtier injoignable");
        }
        partis.add(avis);
    }, UniteDeTravail.DIRECTE, horloge);

    @Test
    void notifierEnregistreLaNotificationEtDeposeUnAvis() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");

        service.notifier(o.tenantId(), o.membreId(), "Paie prête", "La paie de novembre est calculée.", "/paie");

        assertEquals(1, service.nonLues(o.tenantId(), o.membreId()));
        Notification n = service.recentes(o.tenantId(), o.membreId(), 10).get(0);
        assertEquals("/paie", n.lien().orElseThrow());
        assertEquals(1, relais.relayer());
        assertEquals(n.id(), partis.get(0).notificationId());
        // rien ne part deux fois
        assertEquals(0, relais.relayer());
    }

    @Test
    void unLienQuiSortDeLApplicationEstRefuse() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");

        assertThrows(IllegalArgumentException.class,
                () -> service.notifier(o.tenantId(), o.membreId(), "Piège", "", "https://ailleurs.exemple"));
        assertThrows(IllegalArgumentException.class,
                () -> service.notifier(o.tenantId(), o.membreId(), "Piège", "", "//ailleurs.exemple"));
        assertThrows(ErreurMetier.class,
                () -> service.notifier(o.tenantId(), o.membreId(), "Trop long", "x".repeat(501), null));
        assertEquals(0, service.nonLues(o.tenantId(), o.membreId()));
        assertTrue(boite.lignes.isEmpty());
    }

    @Test
    void onNeLitEtNeMarqueQueSesPropresNotifications() {
        OrganisationOuverte a = monde.ouvrir("Organisation A", "awa");
        OrganisationOuverte b = monde.ouvrir("Organisation B", "kofi");
        service.notifier(a.tenantId(), a.membreId(), "Pour Awa", "", null);
        UUID id = service.recentes(a.tenantId(), a.membreId(), 10).get(0).id();

        assertTrue(service.recentes(b.tenantId(), b.membreId(), 10).isEmpty());
        assertTrue(service.marquerLue(b.tenantId(), b.membreId(), id).isEmpty());
        assertTrue(service.marquerLue(a.tenantId(), b.membreId(), id).isEmpty());
        assertEquals(1, service.nonLues(a.tenantId(), a.membreId()));

        assertTrue(service.marquerLue(a.tenantId(), a.membreId(), id).orElseThrow().estLue());
        assertEquals(0, service.nonLues(a.tenantId(), a.membreId()));
    }

    @Test
    void unAvisQuiNePartPasEstRetenteDePlusEnPlusTardSansEtrePerdu() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        service.notifier(o.tenantId(), o.membreId(), "Paie prête", "", null);
        transportEnPanne = true;

        assertEquals(0, relais.relayer());
        // pas avant une seconde
        assertEquals(0, relais.relayer());
        assertEquals(1, boite.lignes.get(0).tentatives);
        horloge.avancer(Duration.ofSeconds(1));
        assertEquals(0, relais.relayer());
        assertEquals(2, boite.lignes.get(0).tentatives);
        assertTrue(boite.lignes.get(0).erreur.contains("courtier injoignable"));

        transportEnPanne = false;
        horloge.avancer(Duration.ofSeconds(1));
        assertEquals(0, relais.relayer());
        horloge.avancer(Duration.ofSeconds(1));
        assertEquals(1, relais.relayer());
        assertEquals(1, partis.size());
        // la notification, elle, était visible depuis le début
        assertEquals(1, service.nonLues(o.tenantId(), o.membreId()));
    }

    @Test
    void lAttenteEntreDeuxEssaisDoubleEtPlafonneACinqMinutes() {
        assertEquals(Duration.ofSeconds(1), RelaisService.attente(0));
        assertEquals(Duration.ofSeconds(8), RelaisService.attente(3));
        assertEquals(Duration.ofMinutes(5), RelaisService.attente(9));
        assertEquals(Duration.ofMinutes(5), RelaisService.attente(5000));
    }

    @Test
    void lesAvisEnvoyesSontPurgesApresUneSemaine() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        service.notifier(o.tenantId(), o.membreId(), "Ancien", "", null);
        relais.relayer();

        horloge.avancer(Duration.ofDays(8));
        relais.relayer();

        assertTrue(boite.lignes.isEmpty());
        assertEquals(1, service.recentes(o.tenantId(), o.membreId(), 10).size());
    }

    @Test
    void unChangementDeRolesPrevientLeMembreEtUneArriveePrevientLesAdministrateurs() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        AvertirMembresParNotification avertir = new AvertirMembresParNotification(service);
        MembresService membres = new MembresService(monde.membresStockes, monde.etablissementsStockes,
                UniteDeTravail.DIRECTE, horloge, avertir);
        Membre kofi = membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));

        // invité, pas encore là : changer ses rôles ne prévient personne
        membres.definirRoles(o.tenantId(), o.membreId(), kofi.id(),
                Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));
        assertEquals(0, service.nonLues(o.tenantId(), kofi.id()));

        avertir.membreARejoint(kofi);
        assertEquals(1, service.nonLues(o.tenantId(), o.membreId()));
        assertFalse(service.recentes(o.tenantId(), o.membreId(), 1).get(0).message().isBlank());
    }

    @Test
    void unAvisSurvitAuPassageEnJson() {
        Avis avis = new Avis(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Paie « prête »",
                "Ligne 1\nLigne 2", null, Instant.parse("2026-11-11T08:00:00.123456Z"));

        assertEquals(avis, AvisJson.lire(AvisJson.ecrire(avis)));
        assertThrows(IllegalArgumentException.class, () -> AvisJson.lire("{\"titre\":"));
        assertThrows(IllegalArgumentException.class, () -> AvisJson.lire("{}"));
    }
}
