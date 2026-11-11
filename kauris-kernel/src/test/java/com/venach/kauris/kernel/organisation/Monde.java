package com.venach.kauris.kernel.organisation;

import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.application.service.AccesService;
import com.venach.kauris.kernel.organisation.application.service.EtablissementsService;
import com.venach.kauris.kernel.organisation.application.service.MembresService;
import com.venach.kauris.kernel.organisation.application.service.OrganisationService;
import com.venach.kauris.kernel.organisation.domain.Identite;

/** Les services du noyau branchés sur les doubles en mémoire : un « monde » neuf par test. */
public final class Monde {

    public final EnMemoire.Organisations organisationsStockees = new EnMemoire.Organisations();
    public final EnMemoire.Etablissements etablissementsStockes = new EnMemoire.Etablissements();
    public final EnMemoire.Membres membresStockes = new EnMemoire.Membres();

    public final OrganisationService organisations = new OrganisationService(organisationsStockees,
            etablissementsStockes, membresStockes, UniteDeTravail.DIRECTE, EnMemoire.HORLOGE);
    public final EtablissementsService etablissements = new EtablissementsService(etablissementsStockes,
            membresStockes, UniteDeTravail.DIRECTE, EnMemoire.HORLOGE);
    public final MembresService membres = new MembresService(membresStockes, etablissementsStockes,
            UniteDeTravail.DIRECTE, EnMemoire.HORLOGE);
    public final AccesService acces = new AccesService(membresStockes, organisationsStockees,
            UniteDeTravail.DIRECTE);

    /** Ouvre une organisation dont la personne « sujet » (compte venach) est propriétaire. */
    public OrganisationOuverte ouvrir(String nom, String sujet) {
        return organisations.ouvrir(new Ouverture(nom, null, Identite.venach(sujet), sujet + "@exemple.test",
                "Personne " + sujet));
    }
}
