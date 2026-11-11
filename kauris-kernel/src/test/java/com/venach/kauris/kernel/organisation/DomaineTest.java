package com.venach.kauris.kernel.organisation;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Organisation;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.organisation.domain.StatutMembre;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomaineTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-11T08:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void lOrganisationEstSonPropreTenant() {
        Organisation o = Organisation.creer("  Boulangerie   du Port ", MAINTENANT);

        assertEquals("Boulangerie du Port", o.nom());
        assertEquals(o.id(), o.tenantId());
        assertTrue(o.estActive());
        assertThrows(ErreurMetier.class, () -> Organisation.creer("B", MAINTENANT));
    }

    @Test
    void lEtablissementPrincipalNeFermePas() {
        Etablissement principal = Etablissement.creer(TENANT, "Siège", true, MAINTENANT);
        Etablissement agence = Etablissement.creer(TENANT, "Agence Kara", false, MAINTENANT);

        assertThrows(ErreurMetier.class, principal::fermer);
        agence.fermer();
        assertFalse(agence.estOuvert());
        assertThrows(ErreurMetier.class, agence::devenirPrincipal);
    }

    @Test
    void proprietaireEtAdministrateurValentPourTouteLOrganisation() {
        UUID etablissement = UUID.randomUUID();

        assertThrows(ErreurMetier.class, () -> new AttributionRole(Role.PROPRIETAIRE, etablissement));
        assertThrows(ErreurMetier.class, () -> new AttributionRole(Role.ADMINISTRATEUR, etablissement));
        assertTrue(AttributionRole.pourEtablissement(Role.GESTIONNAIRE, etablissement).couvre(etablissement));
        assertFalse(AttributionRole.pourEtablissement(Role.GESTIONNAIRE, etablissement).couvre(UUID.randomUUID()));
        assertTrue(AttributionRole.pourTouteLOrganisation(Role.LECTEUR).couvre(etablissement));
    }

    @Test
    void leFondateurEstProprietaireActif() {
        Membre m = Membre.fondateur(TENANT, Identite.venach("abc"), " Awa@Exemple.Test ", "Awa Mensah", MAINTENANT);

        assertEquals("awa@exemple.test", m.email());
        assertTrue(m.estActif());
        assertTrue(m.estProprietaire());
        assertTrue(m.administre());
    }

    @Test
    void lInviteDevientActifQuandIlEstRattache() {
        Membre m = Membre.inviter(TENANT, "kofi@exemple.test", "Kofi", Set.of(
                AttributionRole.pourTouteLOrganisation(Role.LECTEUR)), MAINTENANT);
        assertEquals(StatutMembre.INVITE, m.statut());
        assertTrue(m.identite().isEmpty());

        m.rattacher(Identite.venach("kofi-1"));

        assertTrue(m.estActif());
        assertThrows(ErreurMetier.class, () -> m.rattacher(Identite.venach("quelqu-un-d-autre")));
    }

    @Test
    void unMembreGardeAuMoinsUnRoleEtUnEmailValide() {
        assertThrows(ErreurMetier.class,
                () -> Membre.inviter(TENANT, "kofi@exemple.test", "Kofi", Set.of(), MAINTENANT));
        assertThrows(ErreurMetier.class, () -> Membre.inviter(TENANT, "pas-un-email", "Kofi",
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)), MAINTENANT));
    }

    @Test
    void droitsParEtablissement() {
        UUID kara = UUID.randomUUID();
        UUID lome = UUID.randomUUID();
        Membre m = Membre.inviter(TENANT, "kofi@exemple.test", "Kofi", Set.of(
                AttributionRole.pourEtablissement(Role.GESTIONNAIRE, kara),
                AttributionRole.pourEtablissement(Role.LECTEUR, lome)), MAINTENANT);

        assertTrue(m.peutModifier(kara));
        assertFalse(m.peutModifier(lome));
        assertTrue(m.peutConsulter(lome));
        assertFalse(m.peutConsulter(UUID.randomUUID()));
        assertFalse(m.administre());
    }

    @Test
    void reactiverRendLEtatDAvant() {
        Membre invite = Membre.inviter(TENANT, "kofi@exemple.test", "Kofi", Set.of(
                AttributionRole.pourTouteLOrganisation(Role.LECTEUR)), MAINTENANT);
        invite.desactiver();
        invite.reactiver();
        assertEquals(StatutMembre.INVITE, invite.statut());

        Membre actif = Membre.fondateur(TENANT, Identite.venach("abc"), "awa@exemple.test", "Awa", MAINTENANT);
        actif.desactiver();
        assertFalse(actif.estActif());
        actif.reactiver();
        assertTrue(actif.estActif());
    }

    @Test
    void identiteRefuseLesValeursVides() {
        assertThrows(IllegalArgumentException.class, () -> new Identite("venach", " "));
        assertThrows(IllegalArgumentException.class, () -> new Identite("Pas Valide", "abc"));
    }

    @Test
    void lesRolesSeResumentSansRepetition() {
        UUID lome = UUID.randomUUID();
        UUID dapaong = UUID.randomUUID();

        assertEquals("Propriétaire", acces(AttributionRole.pourTouteLOrganisation(Role.PROPRIETAIRE)));
        assertEquals("Gestionnaire (2 établissements)", acces(
                AttributionRole.pourEtablissement(Role.GESTIONNAIRE, lome),
                AttributionRole.pourEtablissement(Role.GESTIONNAIRE, dapaong)));
        assertEquals("Administrateur, Lecteur (1 établissement)", acces(
                AttributionRole.pourEtablissement(Role.LECTEUR, lome),
                AttributionRole.pourTouteLOrganisation(Role.ADMINISTRATEUR)));
        assertEquals("Gestionnaire", acces(
                AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE),
                AttributionRole.pourEtablissement(Role.GESTIONNAIRE, lome)));
    }

    private static String acces(AttributionRole... roles) {
        return new Acces(UUID.randomUUID(), "Organisation",
                true, UUID.randomUUID(), "Membre", "membre@exemple.test", Set.of(roles)).resumeRoles();
    }
}
