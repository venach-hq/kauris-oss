package com.venach.kauris.kernel;

import com.venach.kauris.kernel.document.CataloguePolices;
import com.venach.kauris.kernel.document.PolicesController;
import com.venach.kauris.kernel.document.ProducteurDocuments;
import com.venach.kauris.kernel.entreprise.application.port.in.GererEntrepriseUseCase;
import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.notification.adapter.out.DiffusionLocale;
import com.venach.kauris.kernel.notification.application.port.in.ConsulterNotificationsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererEtablissementsUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererMembresUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererRolesUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.ResoudreAccesUseCase;
import com.venach.kauris.kernel.ui.ContexteController;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.LiensEnPlus;
import com.venach.kauris.kernel.ui.LiensKauris;
import com.venach.kauris.kernel.ui.ModeleCommun;
import com.venach.kauris.kernel.ui.SessionKauris;
import com.venach.kauris.kernel.ui.entreprise.EntrepriseController;
import com.venach.kauris.kernel.ui.notification.NotificationsController;
import com.venach.kauris.kernel.ui.notification.NotificationsWebSocket;
import com.venach.kauris.kernel.ui.organisation.OrganisationController;
import com.venach.kauris.kernel.ui.organisation.ProfilController;
import com.venach.kauris.kernel.ui.organisation.RolesController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.thymeleaf.ITemplateEngine;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * {@code kauris.ui.page-sans-organisation} : où envoyer une personne connectée qui n'est membre d'aucune
 * organisation (par défaut la page du noyau, « /sans-organisation »).
 */
@AutoConfiguration(after = {KernelAutoConfiguration.class, OrganisationAutoConfiguration.class,
        NotificationsAutoConfiguration.class, ThymeleafAutoConfiguration.class})
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
    public ModeleCommun modeleCommun(
            ContexteCourant contexte,
            @Value("${kauris.ui.page-sans-organisation:" + ModeleCommun.PAGE_SANS_ORGANISATION + "}") String page,
            @Value("${kauris.ui.guide:}") String guide, @Value("${kauris.ui.code-source:}") String codeSource,
            ObjectProvider<LiensEnPlus> liensEnPlus) {
        return new ModeleCommun(contexte, page, new LiensKauris(guide, codeSource),
                liensEnPlus.orderedStream().toList());
    }

    @Bean
    public ContexteController contexteController(ContexteCourant contexte) {
        return new ContexteController(contexte);
    }

    @Bean
    public OrganisationController organisationController(ContexteCourant contexte,
                                                         GererOrganisationUseCase organisations,
                                                         GererEtablissementsUseCase etablissements,
                                                         GererMembresUseCase membres, GererRolesUseCase roles) {
        return new OrganisationController(contexte, organisations, etablissements, membres, roles);
    }

    @Bean
    public RolesController rolesController(ContexteCourant contexte, GererRolesUseCase roles) {
        return new RolesController(contexte, roles);
    }

    @Bean
    public EntrepriseController entrepriseController(ContexteCourant contexte, GererEntrepriseUseCase entreprises,
                                                     ProducteurDocuments documents) {
        return new EntrepriseController(contexte, entreprises, documents);
    }

    /**
     * Les polices des documents : fichiers TrueType du dossier {@code kauris.documents.polices}
     * (« Famille-Style.ttf »). Sans dossier, les polices de base du PDF.
     */
    @Bean
    @ConditionalOnMissingBean
    public CataloguePolices cataloguePolices(@Value("${kauris.documents.polices:}") String dossier) {
        return dossier.isBlank() ? CataloguePolices.vide() : CataloguePolices.depuis(Path.of(dossier));
    }

    @Bean
    @ConditionalOnMissingBean
    public ProducteurDocuments producteurDocuments(ITemplateEngine moteur, GererEntrepriseUseCase entreprises,
                                                   CataloguePolices polices, Clock horloge) {
        return new ProducteurDocuments(moteur, entreprises, polices, horloge);
    }

    @Bean
    public PolicesController policesController(CataloguePolices polices) {
        return new PolicesController(polices);
    }

    @Bean
    public ProfilController profilController(ContexteCourant contexte, GererMembresUseCase membres) {
        return new ProfilController(contexte, membres);
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
