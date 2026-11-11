package com.venach.kauris.kernel.persistance;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase;
import com.venach.kauris.kernel.connexion.local.application.port.in.GererComptesLocauxUseCase.CompteConnectable;
import com.venach.kauris.kernel.notification.application.port.in.ConsulterNotificationsUseCase;
import com.venach.kauris.kernel.notification.application.port.in.NotifierUseCase;
import com.venach.kauris.kernel.notification.application.port.in.RelayerBoiteDEnvoiUseCase;
import com.venach.kauris.kernel.notification.application.port.out.TransportNotifications;
import com.venach.kauris.kernel.notification.domain.Avis;
import com.venach.kauris.kernel.notification.domain.Notification;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.organisation.application.port.out.EtablissementRepository;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le noyau sur un vrai MySQL : les migrations Flyway passent, Hibernate valide le schéma au démarrage,
 * et les services relisent ce qu'ils ont écrit. Docker requis.
 */
@SpringBootTest
@Testcontainers
class PersistanceNoyauTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {

        /** À la place du transport : on regarde ce que le relais fait partir. */
        @Bean
        TransportNotifications transportObserve() {
            return PARTIS::add;
        }
    }

    private static final List<Avis> PARTIS = new CopyOnWriteArrayList<>();

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private GererOrganisationUseCase organisations;
    @Autowired
    private GererEtablissementsUseCase etablissements;
    @Autowired
    private GererMembresUseCase membres;
    @Autowired
    private ResoudreAccesUseCase acces;
    @Autowired
    private GererComptesLocauxUseCase comptes;
    @Autowired
    private NotifierUseCase notifier;
    @Autowired
    private ConsulterNotificationsUseCase notifications;
    @Autowired
    private RelayerBoiteDEnvoiUseCase relais;
    @Autowired
    private MembreRepository membresStockes;
    @Autowired
    private EtablissementRepository etablissementsStockes;

    private OrganisationOuverte ouvrir(String nom, String sujet) {
        return organisations.ouvrir(new Ouverture(nom, null, Identite.venach(sujet), sujet + "@exemple.test",
                "Personne " + sujet));
    }

    @Test
    void uneOrganisationSeRelitAvecSonEtablissementEtSonProprietaire() {
        String sujet = "sub-" + UUID.randomUUID();

        OrganisationOuverte o = ouvrir("Boulangerie du Port", sujet);

        assertEquals("Boulangerie du Port", organisations.consulter(o.tenantId()).nom());
        List<Etablissement> lus = etablissements.lister(o.tenantId());
        assertEquals(1, lus.size());
        assertTrue(lus.get(0).estPrincipal());
        assertEquals(Etablissement.NOM_PAR_DEFAUT, lus.get(0).nom());
        List<Acces> droits = acces.accesDe(Identite.venach(sujet));
        assertEquals(1, droits.size());
        assertEquals(o.tenantId(), droits.get(0).tenantId());
        assertTrue(droits.get(0).a(Role.PROPRIETAIRE));
    }

    @Test
    void unInviteRejointLOrganisationAvecSesRolesParEtablissement() {
        OrganisationOuverte o = ouvrir("Garage Lomé", "sub-" + UUID.randomUUID());
        Etablissement agence = etablissements.ajouter(o.tenantId(), o.membreId(), "Agence Kara");
        String email = "kofi-" + UUID.randomUUID() + "@exemple.test";
        Membre invite = membres.inviter(o.tenantId(), o.membreId(), email, "Kofi",
                Set.of(AttributionRole.pourEtablissement(Role.GESTIONNAIRE, agence.id()),
                        AttributionRole.pourEtablissement(Role.LECTEUR, o.etablissementId())));
        Identite kofi = Identite.venach("sub-" + UUID.randomUUID());

        // adresse non vérifiée : rien n'est rattaché
        assertEquals(0, acces.rattacherInvitations(new IdentiteConnectee(kofi, email, false, "Kofi")));
        assertEquals(1, acces.rattacherInvitations(new IdentiteConnectee(kofi, email, true, "Kofi")));

        Membre relu = membresStockes.trouver(o.tenantId(), invite.id()).orElseThrow();
        assertEquals(StatutMembre.ACTIF, relu.statut());
        assertEquals(2, relu.roles().size());
        Acces droit = acces.accesDe(kofi).get(0);
        assertTrue(droit.peutModifier(agence.id()));
        assertFalse(droit.peutModifier(o.etablissementId()));
        assertTrue(droit.peutConsulter(o.etablissementId()));

        // les rôles sont remplacés, pas ajoutés
        membres.definirRoles(o.tenantId(), o.membreId(), invite.id(),
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));
        assertEquals(Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)),
                membresStockes.trouver(o.tenantId(), invite.id()).orElseThrow().roles());
    }

    @Test
    void uneOrganisationNeVoitPasLesDonneesDUneAutre() {
        OrganisationOuverte a = ouvrir("Organisation A", "sub-" + UUID.randomUUID());
        OrganisationOuverte b = ouvrir("Organisation B", "sub-" + UUID.randomUUID());

        assertTrue(etablissementsStockes.trouver(b.tenantId(), a.etablissementId()).isEmpty());
        assertTrue(membresStockes.trouver(b.tenantId(), a.membreId()).isEmpty());
        assertEquals(1, membres.lister(a.tenantId()).size());
        // le propriétaire de B n'administre pas A
        assertThrows(ErreurMetier.class, () -> etablissements.ajouter(a.tenantId(), b.membreId(), "Intrus"));
    }

    @Test
    void unNomDEtablissementEnDoubleEstRefuseEtRienNEstEcrit() {
        OrganisationOuverte o = ouvrir("Pharmacie", "sub-" + UUID.randomUUID());
        etablissements.ajouter(o.tenantId(), o.membreId(), "Annexe");

        assertThrows(ErreurMetier.class, () -> etablissements.ajouter(o.tenantId(), o.membreId(), "annexe"));
        assertEquals(2, etablissements.lister(o.tenantId()).size());
    }

    @Test
    void unCompteLocalSeCreePourUnMembreInviteEtSeRetrouveALaConnexion() {
        OrganisationOuverte o = ouvrir("Quincaillerie", "sub-" + UUID.randomUUID());
        String email = "ama-" + UUID.randomUUID() + "@exemple.test";
        Membre ama = membres.inviter(o.tenantId(), o.membreId(), email, "Ama",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));

        comptes.creerComptePourMembre(o.tenantId(), o.membreId(), ama.id(), "premier-passage");

        CompteConnectable compte = comptes.pourConnexion(email).orElseThrow();
        assertTrue(compte.actif());
        assertTrue(compte.empreinteMotDePasse().startsWith("$2"));
        comptes.changerMotDePasse(compte.compteId(), "premier-passage", "second-passage");
        assertThrows(ErreurMetier.class,
                () -> comptes.changerMotDePasse(compte.compteId(), "premier-passage", "troisieme-passage"));
        assertEquals(1, acces.accesDe(Identite.locale(compte.compteId().toString())).size());
    }

    @Test
    void uneNotificationEstGardeeEtSonAvisPartUneSeuleFois() {
        OrganisationOuverte o = ouvrir("Librairie", "sub-" + UUID.randomUUID());

        notifier.notifier(o.tenantId(), o.membreId(), "Paie prête", "La paie de novembre est calculée.", "/paie");

        assertEquals(1, notifications.nonLues(o.tenantId(), o.membreId()));
        Notification lue = notifications.recentes(o.tenantId(), o.membreId(), 10).get(0);
        assertEquals("Paie prête", lue.titre());
        assertEquals("/paie", lue.lien().orElseThrow());

        relais.relayer();
        assertEquals(1, PARTIS.stream().filter(a -> a.notificationId().equals(lue.id())).count());
        relais.relayer();
        assertEquals(1, PARTIS.stream().filter(a -> a.notificationId().equals(lue.id())).count());

        assertTrue(notifications.marquerLue(o.tenantId(), o.membreId(), lue.id()).orElseThrow().estLue());
        assertEquals(0, notifications.nonLues(o.tenantId(), o.membreId()));
        notifier.notifier(o.tenantId(), o.membreId(), "Autre", "", null);
        notifications.toutMarquerLu(o.tenantId(), o.membreId());
        assertEquals(0, notifications.nonLues(o.tenantId(), o.membreId()));
    }

    @Test
    void lArriveeDUnInvitePrevientLeProprietaireDansLaMemeTransaction() {
        OrganisationOuverte o = ouvrir("Menuiserie", "sub-" + UUID.randomUUID());
        String email = "yao-" + UUID.randomUUID() + "@exemple.test";
        Membre yao = membres.inviter(o.tenantId(), o.membreId(), email, "Yao",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));

        acces.rattacherInvitations(new IdentiteConnectee(Identite.venach("sub-" + UUID.randomUUID()), email, true,
                "Yao"));

        List<Notification> recues = notifications.recentes(o.tenantId(), o.membreId(), 10);
        assertEquals(1, recues.size());
        assertTrue(recues.get(0).message().contains("Yao"));
        // changer ses rôles le prévient, lui
        membres.definirRoles(o.tenantId(), o.membreId(), yao.id(),
                Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));
        assertEquals(1, notifications.nonLues(o.tenantId(), yao.id()));
    }
}
