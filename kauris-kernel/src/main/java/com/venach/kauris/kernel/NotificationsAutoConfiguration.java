package com.venach.kauris.kernel;

import com.venach.kauris.kernel.commun.UniteDeTravail;
import com.venach.kauris.kernel.notification.adapter.out.AvertirMembresParNotification;
import com.venach.kauris.kernel.notification.adapter.out.BoiteDEnvoiJpaAdapter;
import com.venach.kauris.kernel.notification.adapter.out.DiffusionLocale;
import com.venach.kauris.kernel.notification.adapter.out.NotificationJpaAdapter;
import com.venach.kauris.kernel.notification.adapter.out.PlanificateurRelais;
import com.venach.kauris.kernel.notification.adapter.out.RabbitNotifications;
import com.venach.kauris.kernel.notification.adapter.out.TransportDirect;
import com.venach.kauris.kernel.notification.application.port.in.NotifierUseCase;
import com.venach.kauris.kernel.notification.application.port.in.RelayerBoiteDEnvoiUseCase;
import com.venach.kauris.kernel.notification.application.port.out.BoiteDEnvoi;
import com.venach.kauris.kernel.notification.application.port.out.NotificationRepository;
import com.venach.kauris.kernel.notification.application.port.out.TransportNotifications;
import com.venach.kauris.kernel.notification.application.service.NotificationsService;
import com.venach.kauris.kernel.notification.application.service.RelaisService;
import com.venach.kauris.kernel.organisation.application.port.out.AvertirMembres;
import com.venach.kauris.kernel.organisation.application.port.out.MembreRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.time.Clock;
import java.time.Duration;

/**
 * Notifications : enregistrement, boîte d'envoi et relais. Réglages :
 * <ul>
 *   <li>{@code kauris.notifications.transport} : {@code direct} (par défaut, une seule instance) ou
 *       {@code rabbitmq} ;</li>
 *   <li>{@code kauris.notifications.relais.actif} ({@code true}) et
 *       {@code kauris.notifications.relais.intervalle} ({@code 2s}).</li>
 * </ul>
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class,
        afterName = "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration")
public class NotificationsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotificationRepository notificationRepository(EntityManagerFactory fabrique) {
        return new NotificationJpaAdapter(SharedEntityManagerCreator.createSharedEntityManager(fabrique));
    }

    @Bean
    @ConditionalOnMissingBean
    public BoiteDEnvoi boiteDEnvoi(EntityManagerFactory fabrique) {
        return new BoiteDEnvoiJpaAdapter(SharedEntityManagerCreator.createSharedEntityManager(fabrique));
    }

    /** Une seule instance pour les deux ports : notifier et consulter. */
    @Bean
    @ConditionalOnMissingBean
    public NotificationsService notificationsService(NotificationRepository notifications, BoiteDEnvoi boite,
                                                     MembreRepository membres, UniteDeTravail transaction,
                                                     Clock horloge) {
        return new NotificationsService(notifications, boite, membres, transaction, horloge);
    }

    @Bean
    @ConditionalOnMissingBean
    public AvertirMembres avertirMembres(NotifierUseCase notifier) {
        return new AvertirMembresParNotification(notifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public DiffusionLocale diffusionLocale() {
        return new DiffusionLocale();
    }

    @Bean
    @ConditionalOnMissingBean
    public RelayerBoiteDEnvoiUseCase relayerBoiteDEnvoi(BoiteDEnvoi boite, TransportNotifications transport,
                                                        UniteDeTravail transaction, Clock horloge) {
        return new RelaisService(boite, transport, transaction, horloge);
    }

    @Bean
    @ConditionalOnProperty(name = "kauris.notifications.relais.actif", havingValue = "true", matchIfMissing = true)
    public PlanificateurRelais planificateurRelais(
            RelayerBoiteDEnvoiUseCase relais,
            @Value("${kauris.notifications.relais.intervalle:2s}") Duration intervalle) {
        return new PlanificateurRelais(relais, intervalle);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
    @ConditionalOnProperty(name = "kauris.notifications.transport", havingValue = "rabbitmq")
    @Import(RabbitNotifications.class)
    static class ParRabbit {
    }

    /** Sans réglage : une seule instance, pas de courtier. L'application peut aussi fournir son transport. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "kauris.notifications.transport", havingValue = "direct", matchIfMissing = true)
    static class ParDefaut {

        @Bean
        @ConditionalOnMissingBean(TransportNotifications.class)
        public TransportNotifications transportDirect(DiffusionLocale diffusion) {
            return new TransportDirect(diffusion);
        }
    }
}
