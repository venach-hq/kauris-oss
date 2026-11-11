package com.venach.kauris.kernel.organisation;

import com.venach.kauris.kernel.commun.ErreurMetier;
import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.out.AvertirMembres;
import com.venach.kauris.kernel.organisation.application.port.out.LimitesOrganisation.Limites;
import com.venach.kauris.kernel.organisation.application.service.EtablissementsService;
import com.venach.kauris.kernel.organisation.application.service.MembresService;
import com.venach.kauris.kernel.organisation.domain.AttributionRole;
import com.venach.kauris.kernel.organisation.domain.Etablissement;
import com.venach.kauris.kernel.organisation.domain.Membre;
import com.venach.kauris.kernel.organisation.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les limites que l'application impose à une organisation (dans le cloud : sa formule). */
class LimitesTest {

    private static final Set<AttributionRole> LECTEUR = Set.of(AttributionRole.pourTouteLOrganisation(Role.LECTEUR));

    private final Monde monde = new Monde();
    private Limites limites = new Limites(2, 2);
    private final EtablissementsService etablissements = new EtablissementsService(monde.etablissementsStockes,
            monde.membresStockes, UniteDeTravail.DIRECTE, EnMemoire.HORLOGE, tenant -> limites);
    private final MembresService membres = new MembresService(monde.membresStockes, monde.etablissementsStockes,
            UniteDeTravail.DIRECTE, EnMemoire.HORLOGE, AvertirMembres.PERSONNE, tenant -> limites);

    @Test
    void onNOuvrePasPlusDEtablissementsQueLaLimite() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        Etablissement kara = etablissements.ajouter(o.tenantId(), o.membreId(), "Kara");

        ErreurMetier refus = assertThrows(ErreurMetier.class,
                () -> etablissements.ajouter(o.tenantId(), o.membreId(), "Sokodé"));
        assertTrue(refus.getMessage().contains("2 établissements"));

        // en fermer un libère une place ; le rouvrir la reprend
        etablissements.fermer(o.tenantId(), o.membreId(), kara.id());
        etablissements.ajouter(o.tenantId(), o.membreId(), "Sokodé");
        assertThrows(ErreurMetier.class, () -> etablissements.rouvrir(o.tenantId(), o.membreId(), kara.id()));
        assertEquals(3, etablissements.lister(o.tenantId()).size());
    }

    @Test
    void onNInvitePasPlusDeMembresQueLaLimiteEtLesDesactivesNeComptentPas() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        Membre kofi = membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi", LECTEUR);

        ErreurMetier refus = assertThrows(ErreurMetier.class,
                () -> membres.inviter(o.tenantId(), o.membreId(), "ama@exemple.test", "Ama", LECTEUR));
        assertTrue(refus.getMessage().contains("2 membres"));

        membres.desactiver(o.tenantId(), o.membreId(), kofi.id());
        membres.inviter(o.tenantId(), o.membreId(), "ama@exemple.test", "Ama", LECTEUR);
        assertThrows(ErreurMetier.class, () -> membres.reactiver(o.tenantId(), o.membreId(), kofi.id()));
    }

    @Test
    void uneLimiteAbaisseeNeRetireRienMaisBloqueLesAjouts() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");
        etablissements.ajouter(o.tenantId(), o.membreId(), "Kara");

        limites = new Limites(1, 1);

        assertEquals(2, etablissements.lister(o.tenantId()).size());
        assertThrows(ErreurMetier.class, () -> etablissements.ajouter(o.tenantId(), o.membreId(), "Sokodé"));
        assertThrows(ErreurMetier.class,
                () -> membres.inviter(o.tenantId(), o.membreId(), "kofi@exemple.test", "Kofi", LECTEUR));
    }

    @Test
    void sansLimiteToutEstPermis() {
        OrganisationOuverte o = monde.ouvrir("Boulangerie", "awa");

        for (int i = 0; i < 12; i++) {
            monde.etablissements.ajouter(o.tenantId(), o.membreId(), "Agence " + i);
        }

        assertEquals(13, monde.etablissements.lister(o.tenantId()).size());
        assertThrows(IllegalArgumentException.class, () -> new Limites(0, 5));
    }
}
