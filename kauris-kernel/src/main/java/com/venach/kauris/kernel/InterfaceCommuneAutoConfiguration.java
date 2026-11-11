package com.venach.kauris.kernel;

import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.notification.adapter.out.DiffusionLocale;
import com.venach.kauris.kernel.notification.application.port.in.ConsulterNotificationsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.ui.ContexteController;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.ModeleCommun;
import com.venach.kauris.kernel.ui.SessionKauris;
import com.venach.kauris.kernel.ui.notification.NotificationsController;
import com.venach.kauris.kernel.ui.notification.NotificationsWebSocket;
import com.venach.kauris.kernel.ui.organisation.OrganisationController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Interface commune : contexte de la personne connectée, gabarit et écrans du noyau.
 * Active seulement si l'application dit qui est connecté (un bean {@link SessionKauris}).
 */
@AutoConfiguration(after = {KernelAutoConfiguration.class, OrganisationAutoConfiguration.class,
        NotificationsAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(SessionKauris.class)
public class InterfaceCommuneAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ContexteCourant contexteCourant(SessionKauris session, ResoudreAccesUseCase acces,
                                           RegistreModules modules,
                                           ObjectProvider<ConsulterNotificationsUseCase> notifications) {
        return new ContexteCourant(session, acces, modules, notifications.getIfAvailable());
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

    // ------------------------------------------------------------------ notifications

    @Bean
    public NotificationsController notificationsController(ContexteCourant contexte,
                                                           ConsulterNotificationsUseCase notifications) {
        return new NotificationsController(contexte, notifications);
    }

    /** Le canal WebSocket écoute les avis qui arrivent dans cette instance. */
    @Bean
    public NotificationsWebSocket notificationsWebSocket(ContexteCourant contexte, DiffusionLocale diffusion) {
        NotificationsWebSocket canal = new NotificationsWebSocket(contexte);
        diffusion.ecouter(canal::prevenir);
        return canal;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSocket
    static class CanalNotifications implements WebSocketConfigurer {

        private final NotificationsWebSocket canal;

        CanalNotifications(NotificationsWebSocket canal) {
            this.canal = canal;
        }

        /** Même origine seulement (réglage par défaut de Spring) : une autre page web ne peut pas s'y brancher. */
        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registre) {
            registre.addHandler(canal, NotificationsWebSocket.CHEMIN).addInterceptors(canal);
        }
    }
}
