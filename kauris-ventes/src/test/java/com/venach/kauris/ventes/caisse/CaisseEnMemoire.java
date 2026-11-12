package com.venach.kauris.ventes.caisse;

import com.venach.kauris.ventes.caisse.application.PortsCaisse;
import com.venach.kauris.ventes.caisse.domain.SessionCaisse;
import com.venach.kauris.ventes.caisse.domain.TicketCaisse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Les sessions et tickets de caisse en mémoire, pour les tests. */
public class CaisseEnMemoire implements PortsCaisse.Sessions, PortsCaisse.Tickets {

    private final Map<UUID, SessionCaisse> sessions = new HashMap<>();
    private final List<TicketCaisse> tickets = new ArrayList<>();
    private final Map<String, Long> compteurs = new HashMap<>();

    @Override
    public void sauvegarder(SessionCaisse session) {
        sessions.put(session.id(), session);
    }

    @Override
    public Optional<SessionCaisse> session(UUID tenantId, UUID id) {
        return Optional.ofNullable(sessions.get(id)).filter(s -> s.tenantId().equals(tenantId));
    }

    @Override
    public Optional<SessionCaisse> verrouillerSession(UUID tenantId, UUID id) {
        return session(tenantId, id);
    }

    @Override
    public Optional<SessionCaisse> ouverteDe(UUID tenantId, UUID membreId) {
        return sessions.values().stream().filter(s -> s.tenantId().equals(tenantId) && s.membreId().equals(membreId)
                && s.ouverte()).findFirst();
    }

    @Override
    public List<SessionCaisse> sessions(UUID tenantId, UUID membreId, int limite) {
        return sessions.values().stream().filter(s -> s.tenantId().equals(tenantId))
                .filter(s -> membreId == null || s.membreId().equals(membreId))
                .sorted(Comparator.comparing(SessionCaisse::ouverteLe).reversed()).limit(limite).toList();
    }

    @Override
    public long prochainNumero(UUID tenantId, int annee) {
        return compteurs.merge(tenantId + "/" + annee, 1L, Long::sum);
    }

    @Override
    public void sauvegarder(TicketCaisse ticket) {
        tickets.add(ticket);
    }

    @Override
    public List<TicketCaisse> ticketsDe(UUID tenantId, UUID sessionId) {
        return tickets.stream().filter(t -> t.tenantId().equals(tenantId) && t.sessionId().equals(sessionId)).toList();
    }

    @Override
    public Optional<TicketCaisse> ticketDuDocument(UUID tenantId, UUID documentId) {
        return tickets.stream().filter(t -> t.tenantId().equals(tenantId) && t.documentId().equals(documentId))
                .findFirst();
    }
}
