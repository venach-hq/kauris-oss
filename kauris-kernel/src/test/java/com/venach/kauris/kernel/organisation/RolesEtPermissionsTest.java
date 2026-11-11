package com.venach.kauris.kernel.organisation;

import com.venach.kauris.kernel.commun.AccesRefuse;
import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.module.PermissionsNoyau;
import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import com.venach.kauris.kernel.organisation.domain.RolePersonnalise;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolesEtPermissionsTest {

    private final Monde monde = new Monde();

    /** Invite une personne avec ces rôles, la fait se connecter, et rend son accès. */
    private Acces rejoindre(OrganisationOuverte o, String sujet, Set<AttributionRole> roles) {
        monde.membres.inviter(o.tenantId(), o.membreId(), sujet + "@exemple.test", "Personne " + sujet, roles);
        monde.acces.rattacherInvitations(new IdentiteConnectee(Identite.venach(sujet), sujet + "@exemple.test",
                true, "Personne " + sujet));
        return monde.acces.accesDe(Identite.venach(sujet)).get(0);
    }

    @Test
    void leProprietaireALesPermissionsSansQuOnLesLuiDonne() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");

        Acces awa = monde.acces.accesDe(Identite.venach("awa")).get(0);

        assertTrue(awa.peut(PermissionsNoyau.ENTREPRISE_MODIFIER));
        assertTrue(awa.peut("module.pas.encore.installe"));
        assertEquals(o.tenantId(), awa.tenantId());
    }

    @Test
    void lesRolesDeBaseDonnentLesPermissionsDeLeurNiveau() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");

        Acces lecteur = rejoindre(o, "kofi", Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));
        Acces gestionnaire = rejoindre(o, "ama", Set.of(AttributionRole.pourTouteLOrganisation(Role.GESTIONNAIRE)));

        assertTrue(lecteur.peut(PermissionsNoyau.ENTREPRISE_CONSULTER));
        assertFalse(lecteur.peut(PermissionsNoyau.ENTREPRISE_MODIFIER));
        assertTrue(gestionnaire.peut(PermissionsNoyau.MEMBRES_CONSULTER));
        // modifier l'entreprise est de niveau « administrer » : au-dessus du gestionnaire
        assertFalse(gestionnaire.peut(PermissionsNoyau.ENTREPRISE_MODIFIER));
    }

    @Test
    void unRolePersonnaliseNeDonneQueCeQuiEstCoche() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        RolePersonnalise comptable = monde.roles.creer(o.tenantId(), o.membreId(), " Comptable ", "Tient les comptes",
                Set.of(PermissionsNoyau.ENTREPRISE_CONSULTER, PermissionsNoyau.ENTREPRISE_MODIFIER));

        Acces kofi = rejoindre(o, "kofi", Set.of(AttributionRole.personnalise(comptable.id(), null)));

        assertEquals("Comptable", comptable.nom());
        assertTrue(kofi.peut(PermissionsNoyau.ENTREPRISE_CONSULTER));
        assertTrue(kofi.peut(PermissionsNoyau.ENTREPRISE_MODIFIER));
        assertFalse(kofi.peut(PermissionsNoyau.MEMBRES_CONSULTER));
        assertFalse(kofi.peut(PermissionsNoyau.ORGANISATION_CONSULTER));
        assertFalse(kofi.administre());
        assertEquals("Comptable", kofi.resumeRoles());
    }

    @Test
    void unRoleLimiteAUnEtablissementNeVautQueLa() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        UUID agence = monde.etablissements.ajouter(o.tenantId(), o.membreId(), "Agence").id();
        RolePersonnalise caissier = monde.roles.creer(o.tenantId(), o.membreId(), "Caissier", "",
                Set.of(PermissionsNoyau.ORGANISATION_CONSULTER));

        Acces kofi = rejoindre(o, "kofi", Set.of(AttributionRole.personnalise(caissier.id(), agence)));

        assertTrue(kofi.peut(PermissionsNoyau.ORGANISATION_CONSULTER));
        assertTrue(kofi.peut(PermissionsNoyau.ORGANISATION_CONSULTER, agence));
        assertFalse(kofi.peut(PermissionsNoyau.ORGANISATION_CONSULTER, UUID.randomUUID()));
    }

    @Test
    void changerLesPermissionsDUnRoleChangeLesDroitsDeSesTitulaires() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        RolePersonnalise role = monde.roles.creer(o.tenantId(), o.membreId(), "Assistant", "",
                Set.of(PermissionsNoyau.MEMBRES_CONSULTER));
        rejoindre(o, "kofi", Set.of(AttributionRole.personnalise(role.id(), null)));

        monde.roles.modifier(o.tenantId(), o.membreId(), role.id(), "Assistant de direction", "",
                Set.of(PermissionsNoyau.ENTREPRISE_CONSULTER));

        Acces kofi = monde.acces.accesDe(Identite.venach("kofi")).get(0);
        assertFalse(kofi.peut(PermissionsNoyau.MEMBRES_CONSULTER));
        assertTrue(kofi.peut(PermissionsNoyau.ENTREPRISE_CONSULTER));
        assertEquals("Assistant de direction", kofi.resumeRoles());
    }

    @Test
    void seulUnProprietaireDefinitLesRoles() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        Acces admin = rejoindre(o, "kofi", Set.of(AttributionRole.pourTouteLOrganisation(Role.ADMINISTRATEUR)));

        assertThrows(AccesRefuse.class, () -> monde.roles.creer(o.tenantId(), admin.membreId(), "Comptable", "",
                Set.of()));
        assertTrue(monde.roles.lister(o.tenantId()).isEmpty());
    }

    @Test
    void unePermissionInconnueOuUnNomDejaPrisSontRefuses() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        monde.roles.creer(o.tenantId(), o.membreId(), "Comptable", "", Set.of());

        assertThrows(ErreurMetier.class, () -> monde.roles.creer(o.tenantId(), o.membreId(), "Caissier", "",
                Set.of("paie.tout.voir")));
        assertThrows(ErreurMetier.class, () -> monde.roles.creer(o.tenantId(), o.membreId(), "comptable", "",
                Set.of()));
        assertEquals(1, monde.roles.lister(o.tenantId()).size());
    }

    @Test
    void onNeSupprimePasUnRoleEncoreDonne() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        RolePersonnalise role = monde.roles.creer(o.tenantId(), o.membreId(), "Comptable", "", Set.of());
        Membre kofi = monde.membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi",
                Set.of(AttributionRole.personnalise(role.id(), null)));

        assertThrows(ErreurMetier.class, () -> monde.roles.supprimer(o.tenantId(), o.membreId(), role.id()));

        monde.membres.definirRoles(o.tenantId(), o.membreId(), kofi.id(),
                Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR)));
        monde.roles.supprimer(o.tenantId(), o.membreId(), role.id());
        assertTrue(monde.roles.lister(o.tenantId()).isEmpty());
    }

    @Test
    void onNeDonnePasLeRoleDUneAutreOrganisation() {
        OrganisationOuverte boulangerie = monde.ouvrir("Boulangerie", "awa");
        OrganisationOuverte garage = monde.ouvrir("Garage", "yao");
        RolePersonnalise ailleurs = monde.roles.creer(garage.tenantId(), garage.membreId(), "Mécanicien", "",
                Set.of());

        assertThrows(ErreurMetier.class, () -> monde.membres.inviter(boulangerie.tenantId(),
                boulangerie.membreId(), "kofi@exemple.test", "Kofi",
                Set.of(AttributionRole.personnalise(ailleurs.id(), null))));
    }
}
