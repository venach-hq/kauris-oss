package com.venach.kauris.kernel.notification;

import com.venach.kauris.kernel.notification.adapter.out.DiffusionLocale;
import com.venach.kauris.kernel.notification.application.port.in.NotifierUseCase;
import com.venach.kauris.kernel.notification.application.port.in.RelayerBoiteDEnvoiUseCase;
import com.venach.kauris.kernel.notification.domain.Avis;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.Ouverture;
import com.venach.kauris.kernel.organisation.domain.Identite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Le chemin complet de Kauris Cloud sur de vrais MySQL et RabbitMQ : notification → boîte d'envoi → relais →
 * échange RabbitMQ → file de l'instance → diffusion locale. Docker requis.
 */
@SpringBootTest(properties = "kauris.notifications.transport=rabbitmq")
@Testcontainers
class TransportRabbitTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
    }

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management");

    @Autowired
    private GererOrganisationUseCase organisations;
    @Autowired
    private NotifierUseCase notifier;
    @Autowired
    private RelayerBoiteDEnvoiUseCase relais;
    @Autowired
    private DiffusionLocale diffusion;

    @Test
    void unAvisPasseParRabbitMqEtRevientDansLInstance() throws Exception {
        BlockingQueue<Avis> recus = new LinkedBlockingQueue<>();
        diffusion.ecouter(recus::add);
        OrganisationOuverte o = organisations.ouvrir(new Ouverture("Boulangerie", null,
                Identite.venach("sub-" + UUID.randomUUID()), "awa@exemple.test", "Awa"));

        notifier.notifier(o.tenantId(), o.membreId(), "Paie prête", "Novembre « calculé »", "/paie");
        assertEquals(1, relais.relayer());

        Avis recu = recus.poll(20, TimeUnit.SECONDS);
        assertNotNull(recu, "l'avis n'est pas revenu de RabbitMQ");
        assertEquals(o.membreId(), recu.membreId());
        assertEquals("Paie prête", recu.titre());
        assertEquals("Novembre « calculé »", recu.message());
        assertEquals("/paie", recu.lien());
    }
}
