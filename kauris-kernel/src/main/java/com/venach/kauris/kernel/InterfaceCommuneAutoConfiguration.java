package com.venach.kauris.kernel;

import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.ui.ContexteController;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.ModeleCommun;
import com.venach.kauris.kernel.ui.SessionKauris;
import com.venach.kauris.kernel.ui.organisation.OrganisationController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Interface commune : contexte de la personne connectée, gabarit et écrans du noyau.
 * Active seulement si l'application dit qui est connecté (un bean {@link SessionKauris}).
 */
@AutoConfiguration(after = {KernelAutoConfiguration.class, OrganisationAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(SessionKauris.class)
public class InterfaceCommuneAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ContexteCourant contexteCourant(SessionKauris session, ResoudreAccesUseCase acces,
                                           RegistreModules modules) {
        return new ContexteCourant(session, acces, modules);
    }

    @Bean
    public ModeleCommun modeleCommun(ContexteCourant contexte) {
        return new ModeleCommun(contexte);
    }

    @Bean
    public ContexteController contexteController(ContexteCourant contexte) {
        return new ContexteController(contexte);
    }

    @Bean
    public OrganisationController organisationController(ContexteCourant contexte,
                                                         GererOrganisationUseCase organisations,
                                                         GererEtablissementsUseCase etablissements,
                                                         GererMembresUseCase membres) {
        return new OrganisationController(contexte, organisations, etablissements, membres);
    }
}
