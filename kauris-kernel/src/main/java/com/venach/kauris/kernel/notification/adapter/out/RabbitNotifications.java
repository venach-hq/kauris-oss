package com.venach.kauris.kernel.notification.adapter.out;

import com.venach.kauris.kernel.notification.application.port.out.TransportNotifications;
import com.venach.kauris.kernel.notification.domain.Avis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Transport par RabbitMQ, pour plusieurs instances de l'application : chaque avis est publié sur un échange
 * « fanout » ; chaque instance a sa file à elle, qui disparaît avec elle, et reçoit donc tous les avis.
 * Elle prévient ceux de ses écrans qui sont concernés.
 * <p>
 * Si RabbitMQ est arrêté, l'application démarre quand même : la publication échoue, la boîte d'envoi
 * retente, et l'écoute se reconnecte seule.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitNotifications {

    public static final String ECHANGE = "kauris.notifications";

    private static final Logger log = LoggerFactory.getLogger(RabbitNotifications.class);

    @Bean
    public FanoutExchange echangeNotifications() {
        return new FanoutExchange(ECHANGE, true, false);
    }

    @Bean
    public Queue fileNotificationsDeCetteInstance() {
        return new AnonymousQueue();
    }

    @Bean
    public Declarables liaisonNotifications(FanoutExchange echangeNotifications,
                                            Queue fileNotificationsDeCetteInstance) {
        return new Declarables(BindingBuilder.bind(fileNotificationsDeCetteInstance).to(echangeNotifications));
    }

    @Bean
    public TransportNotifications transportNotifications(RabbitTemplate rabbit) {
        return avis -> {
            Message message = MessageBuilder.withBody(AvisJson.ecrire(avis).getBytes(StandardCharsets.UTF_8))
                    .setContentType("application/json")
                    .setContentEncoding("UTF-8")
                    // L'avis n'a de valeur que pour un écran ouvert maintenant : inutile de l'écrire sur disque.
                    .setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT)
                    .build();
            rabbit.send(ECHANGE, "", message);
        };
    }

    @Bean
    public SimpleMessageListenerContainer ecouteNotifications(ConnectionFactory connexion,
                                                              Queue fileNotificationsDeCetteInstance,
                                                              DiffusionLocale diffusion) {
        SimpleMessageListenerContainer ecoute = new SimpleMessageListenerContainer(connexion);
        ecoute.setQueues(fileNotificationsDeCetteInstance);
        ecoute.setMessageListener(message -> {
            Avis avis;
            try {
                avis = AvisJson.lire(new String(message.getBody(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException illisible) {
                // Jamais remis en file : un message illisible le resterait.
                log.warn("Avis RabbitMQ illisible, ignoré");
                return;
            }
            diffusion.diffuser(avis);
        });
        return ecoute;
    }
}
