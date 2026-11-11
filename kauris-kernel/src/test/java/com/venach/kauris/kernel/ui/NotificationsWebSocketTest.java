package com.venach.kauris.kernel.ui;

import com.venach.kauris.kernel.module.ModuleNoyau;
import com.venach.kauris.kernel.module.RegistreModules;
import com.venach.kauris.kernel.notification.domain.Avis;
import com.venach.kauris.kernel.organisation.Monde;
import com.venach.kauris.kernel.organisation.application.port.in.GererOrganisationUseCase.OrganisationOuverte;
import com.venach.kauris.kernel.organisation.application.port.in.IdentiteConnectee;
import com.venach.kauris.kernel.organisation.domain.Identite;
import com.venach.kauris.kernel.ui.notification.NotificationsWebSocket;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationsWebSocketTest {

    private final Monde monde = new Monde();
    private Identite connectee;
    private final SessionKauris session = () -> Optional.ofNullable(connectee)
            .map(i -> new IdentiteConnectee(i, i.sujet() + "@exemple.test", true, i.sujet()));
    private final NotificationsWebSocket canal = new NotificationsWebSocket(new ContexteCourant(session,
            monde.acces, new RegistreModules(List.of(new ModuleNoyau("test")))));

    /** Ouvre le canal pour la personne connectée ; nul si l'ouverture est refusée. */
    private WebSocketSession ouvrir(String id) throws Exception {
        Map<String, Object> attributs = new HashMap<>();
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        boolean accepte = canal.beforeHandshake(new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(reponse), canal, attributs);
        if (!accepte) {
            assertEquals(403, reponse.getStatus());
            return null;
        }
        WebSocketSession ecran = mock(WebSocketSession.class);
        when(ecran.getId()).thenReturn(id);
        when(ecran.isOpen()).thenReturn(true);
        when(ecran.getAttributes()).thenReturn(attributs);
        canal.afterConnectionEstablished(ecran);
        return ecran;
    }

    private static Avis avisPour(OrganisationOuverte o) {
        return new Avis(UUID.randomUUID(), o.tenantId(), o.membreId(), "Paie prête", "Novembre", "/paie",
                Instant.parse("2026-11-11T08:00:00Z"));
    }

    @Test
    void sansConnexionOuSansOrganisationLeCanalNeSOuvrePas() throws Exception {
        assertTrue(ouvrir("a") == null);
        connectee = Identite.venach("inconnue");
        assertTrue(ouvrir("b") == null);
        assertEquals(0, canal.ecransOuverts());
    }

    @Test
    void unAvisNArriveQueSurLesEcransDuMembreConcerne() throws Exception {
        OrganisationOuverte boulangerie = monde.ouvrir("Boulangerie", "awa");
        OrganisationOuverte garage = monde.ouvrir("Garage", "kofi");
        connectee = Identite.venach("awa");
        WebSocketSession ecranAwa = ouvrir("awa-1");
        connectee = Identite.venach("kofi");
        WebSocketSession ecranKofi = ouvrir("kofi-1");

        canal.prevenir(avisPour(boulangerie));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WebSocketMessage<?>> recu = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(ecranAwa).sendMessage(recu.capture());
        String texte = ((TextMessage) recu.getValue()).getPayload();
        assertTrue(texte.contains("\"titre\":\"Paie prête\""));
        assertTrue(texte.contains("\"lien\":\"/paie\""));
        assertFalse(texte.contains("membre"));
        verify(ecranKofi, never()).sendMessage(any());

        // écran fermé : plus rien ne lui est envoyé
        canal.afterConnectionClosed(ecranKofi, CloseStatus.NORMAL);
        canal.prevenir(avisPour(garage));
        verify(ecranKofi, never()).sendMessage(any());
        assertEquals(1, canal.ecransOuverts());
    }
}
