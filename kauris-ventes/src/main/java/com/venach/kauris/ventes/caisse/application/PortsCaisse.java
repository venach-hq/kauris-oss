package com.venach.kauris.ventes.caisse.application;

import com.venach.kauris.ventes.caisse.domain.SessionCaisse;
import com.venach.kauris.ventes.caisse.domain.TicketCaisse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ce dont la caisse a besoin pour garder ses sessions et ses tickets. */
public final class PortsCaisse {

    private PortsCaisse() {
    }

    public interface Sessions {
        void sauvegarder(SessionCaisse session);

        Optional<SessionCaisse> session(UUID tenantId, UUID id);

        /** La session, verrouillée jusqu'à la fin de la transaction (clôture et ventes simultanées). */
        Optional<SessionCaisse> verrouillerSession(UUID tenantId, UUID id);

        Optional<SessionCaisse> ouverteDe(UUID tenantId, UUID membreId);

        /** Les plus récentes d'abord ; {@code membreId} null : toutes. */
        List<SessionCaisse> sessions(UUID tenantId, UUID membreId, int limite);

        /** Numéro suivant de l'année, sans trou ; le compteur reste verrouillé jusqu'à la fin de la transaction. */
        long prochainNumero(UUID tenantId, int annee);
    }

    public interface Tickets {
        void sauvegarder(TicketCaisse ticket);

        List<TicketCaisse> ticketsDe(UUID tenantId, UUID sessionId);

        Optional<TicketCaisse> ticketDuDocument(UUID tenantId, UUID documentId);
    }
}
