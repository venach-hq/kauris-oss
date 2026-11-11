package com.venach.kauris.kernel.ui.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venach.kauris.kernel.notification.domain.Avis;
import com.venach.kauris.kernel.ui.ContexteCourant;
import com.venach.kauris.kernel.ui.ContexteKauris;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le canal WebSocket des notifications : le serveur parle, le navigateur écoute.
 * <p>
 * À l'ouverture, on retient de quelles organisations la personne est membre ; elle ne reçoit ensuite que les
 * avis adressés à l'un de ses « membres ». Le navigateur ne choisit rien : il n'y a pas d'abonnement à
 * demander, donc rien à usurper. Si les droits changent, c'est à la reconnexion suivante qu'ils sont relus ;
 * la liste des notifications, elle, est toujours relue en base.
 */
public class NotificationsWebSocket extends TextWebSocketHandler implements HandshakeInterceptor {

    public static final String CHEMIN = "/ws/notifications";

    private static final String MEMBRES = "kauris.membres";
    private static final int ENVOI_MAX_MS = 5_000;
    private static final int TAMPON_MAX_OCTETS = 64 * 1024;

    private final ContexteCourant contexte;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<UUID, Set<WebSocketSession>> parMembre = new ConcurrentHashMap<>();

    public NotificationsWebSocket(ContexteCourant contexte) {
        this.contexte = contexte;
    }

    // ------------------------------------------------------------------ ouverture

    @Override
    public boolean beforeHandshake(ServerHttpRequest requete, ServerHttpResponse reponse, WebSocketHandler handler,
                                   Map<String, Object> attributs) {
        if (!(requete instanceof ServletServerHttpRequest servlet)) {
            reponse.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        HttpServletRequest http = servlet.getServletRequest();
        Optional<ContexteKauris> connu = contexte.contexte(http);
        if (connu.isEmpty() || connu.get().acces() == null) {
            reponse.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        Set<UUID> membres = new HashSet<>();
        membres.add(connu.get().acces().membreId());
        connu.get().autresAcces().forEach(a -> membres.add(a.membreId()));
        attributs.put(MEMBRES, membres);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest requete, ServerHttpResponse reponse, WebSocketHandler handler,
                               Exception erreur) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession sure = new ConcurrentWebSocketSessionDecorator(session, ENVOI_MAX_MS, TAMPON_MAX_OCTETS);
        membres(session).forEach(m -> parMembre.computeIfAbsent(m, cle -> ConcurrentHashMap.newKeySet()).add(sure));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus statut) {
        membres(session).forEach(m -> parMembre.computeIfPresent(m, (cle, sessions) -> {
            sessions.removeIf(s -> s.getId().equals(session.getId()));
            return sessions.isEmpty() ? null : sessions;
        }));
    }

    /** Le navigateur n'a rien à dire sur ce canal : ce qu'il envoie est ignoré. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> membres(WebSocketSession session) {
        Object membres = session.getAttributes().get(MEMBRES);
        return membres instanceof Set<?> ensemble ? (Set<UUID>) ensemble : Set.of();
    }

    // ------------------------------------------------------------------ envoi

    /** Prévient les écrans ouverts du membre concerné, s'il y en a sur cette instance. */
    public void prevenir(Avis avis) {
        Set<WebSocketSession> sessions = parMembre.get(avis.membreId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(texte(avis));
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | RuntimeException coupee) {
                // Écran fermé ou trop lent : il relira ses notifications à la prochaine page.
            }
        }
    }

    public int ecransOuverts() {
        return parMembre.values().stream().mapToInt(Set::size).sum();
    }

    private String texte(Avis avis) {
        Map<String, String> corps = new LinkedHashMap<>();
        corps.put("id", avis.notificationId().toString());
        corps.put("organisation", avis.tenantId().toString());
        corps.put("titre", avis.titre());
        corps.put("message", avis.message());
        corps.put("lien", avis.lien());
        try {
            return json.writeValueAsString(corps);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
