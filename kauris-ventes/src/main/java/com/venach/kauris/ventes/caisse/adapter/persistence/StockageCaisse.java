package com.venach.kauris.ventes.caisse.adapter.persistence;

import com.venach.kauris.ventes.caisse.application.PortsCaisse;
import com.venach.kauris.ventes.caisse.domain.SessionCaisse;
import com.venach.kauris.ventes.caisse.domain.TicketCaisse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stockage JPA de la caisse (migration db/migration/caisse). */
public class StockageCaisse implements PortsCaisse.Sessions, PortsCaisse.Tickets {

    private final EntityManager em;

    public StockageCaisse(EntityManager em) {
        this.em = em;
    }

    @Entity(name = "CaisseSession")
    @Table(name = "caisse_session")
    static class SessionEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "numero", length = 20, nullable = false, updatable = false)
        String numero;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "membre_id", length = 36, nullable = false, updatable = false)
        UUID membreId;
        @Column(name = "caissier", length = 150, nullable = false)
        String caissier;
        @Column(name = "jour", nullable = false)
        LocalDate jour;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "ouverte_le", nullable = false)
        Instant ouverteLe;
        @Column(name = "fond", nullable = false)
        long fond;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "compte_especes", length = 36)
        UUID compteEspeces;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "compte_mobile", length = 36)
        UUID compteMobile;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "compte_carte", length = 36)
        UUID compteCarte;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "ouverte_par", length = 36)
        UUID ouvertePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "close_le")
        Instant closeLe;
        @Column(name = "close_par", length = 150)
        String closePar;
        @Column(name = "especes_comptees")
        Long especesComptees;
        @Column(name = "ecart")
        Long ecart;
        @Column(name = "note", length = 300, nullable = false)
        String note;
    }

    @Entity(name = "CaisseTicket")
    @Table(name = "caisse_ticket")
    static class TicketEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "session_id", length = 36, nullable = false, updatable = false)
        UUID sessionId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "document_id", length = 36, nullable = false, updatable = false)
        UUID documentId;
        @Column(name = "numero", length = 30, nullable = false)
        String numero;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "cree_le", nullable = false)
        Instant creeLe;
        @Column(name = "total", nullable = false)
        long total;
        @Column(name = "especes", nullable = false)
        long especes;
        @Column(name = "mobile", nullable = false)
        long mobile;
        @Column(name = "carte", nullable = false)
        long carte;
        @Column(name = "recu", nullable = false)
        long recu;
        @Column(name = "rendu", nullable = false)
        long rendu;
        @Column(name = "reference_mobile", length = 80, nullable = false)
        String referenceMobile;
    }

    // ------------------------------------------------------------------ sessions

    @Override
    public void sauvegarder(SessionCaisse s) {
        SessionEntity e = em.find(SessionEntity.class, s.id());
        boolean nouvelle = e == null;
        if (nouvelle) {
            e = new SessionEntity();
            e.id = s.id();
            e.tenantId = s.tenantId();
            e.numero = s.numero();
            e.membreId = s.membreId();
        }
        e.caissier = s.caissier();
        e.jour = s.jour();
        e.ouverteLe = s.ouverteLe();
        e.fond = s.fond();
        e.compteEspeces = s.compteEspeces();
        e.compteMobile = s.compteMobile();
        e.compteCarte = s.compteCarte();
        e.ouvertePar = s.ouverte() ? s.membreId() : null;
        e.closeLe = s.closeLe();
        e.closePar = s.closePar();
        e.especesComptees = s.especesComptees();
        e.ecart = s.ecart();
        e.note = s.note();
        if (nouvelle) {
            em.persist(e);
        }
        em.flush();
    }

    @Override
    public Optional<SessionCaisse> session(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(SessionEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageCaisse::session);
    }

    @Override
    public Optional<SessionCaisse> verrouillerSession(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(SessionEntity.class, id, LockModeType.PESSIMISTIC_WRITE))
                .filter(e -> e.tenantId.equals(tenantId)).map(StockageCaisse::session);
    }

    @Override
    public Optional<SessionCaisse> ouverteDe(UUID tenantId, UUID membreId) {
        return em.createQuery("select s from CaisseSession s where s.tenantId = :t and s.ouvertePar = :m",
                        SessionEntity.class)
                .setParameter("t", tenantId).setParameter("m", membreId).getResultStream().findFirst()
                .map(StockageCaisse::session);
    }

    @Override
    public List<SessionCaisse> sessions(UUID tenantId, UUID membreId, int limite) {
        String filtre = membreId == null ? "" : " and s.membreId = :m";
        var q = em.createQuery("select s from CaisseSession s where s.tenantId = :t" + filtre
                + " order by s.ouverteLe desc", SessionEntity.class).setParameter("t", tenantId);
        if (membreId != null) {
            q.setParameter("m", membreId);
        }
        return q.setMaxResults(Math.max(1, limite)).getResultList().stream().map(StockageCaisse::session).toList();
    }

    @Override
    public long prochainNumero(UUID tenantId, int annee) {
        em.createNativeQuery("insert into caisse_compteur (tenant_id, annee, dernier) values (?1, ?2, 1)"
                        + " on duplicate key update dernier = dernier + 1")
                .setParameter(1, tenantId.toString()).setParameter(2, annee).executeUpdate();
        Number dernier = (Number) em.createNativeQuery("select dernier from caisse_compteur"
                        + " where tenant_id = ?1 and annee = ?2")
                .setParameter(1, tenantId.toString()).setParameter(2, annee).getSingleResult();
        return dernier.longValue();
    }

    private static SessionCaisse session(SessionEntity e) {
        return new SessionCaisse(e.id, e.tenantId, e.numero, e.membreId, e.caissier, e.jour, e.ouverteLe, e.fond,
                e.compteEspeces, e.compteMobile, e.compteCarte, e.ouvertePar != null, e.closeLe, e.closePar,
                e.especesComptees, e.ecart, e.note);
    }

    // ------------------------------------------------------------------ tickets

    @Override
    public void sauvegarder(TicketCaisse t) {
        TicketEntity e = new TicketEntity();
        e.id = t.id();
        e.tenantId = t.tenantId();
        e.sessionId = t.sessionId();
        e.documentId = t.documentId();
        e.numero = t.numero();
        e.creeLe = t.creeLe();
        e.total = t.total();
        e.especes = t.especes();
        e.mobile = t.mobile();
        e.carte = t.carte();
        e.recu = t.recu();
        e.rendu = t.rendu();
        e.referenceMobile = t.referenceMobile();
        em.persist(e);
    }

    @Override
    public List<TicketCaisse> ticketsDe(UUID tenantId, UUID sessionId) {
        return em.createQuery("select t from CaisseTicket t where t.tenantId = :t and t.sessionId = :s"
                        + " order by t.creeLe", TicketEntity.class)
                .setParameter("t", tenantId).setParameter("s", sessionId).getResultList().stream()
                .map(StockageCaisse::ticket).toList();
    }

    @Override
    public Optional<TicketCaisse> ticketDuDocument(UUID tenantId, UUID documentId) {
        return em.createQuery("select t from CaisseTicket t where t.tenantId = :t and t.documentId = :d",
                        TicketEntity.class)
                .setParameter("t", tenantId).setParameter("d", documentId).getResultStream().findFirst()
                .map(StockageCaisse::ticket);
    }

    private static TicketCaisse ticket(TicketEntity e) {
        return new TicketCaisse(e.id, e.tenantId, e.sessionId, e.documentId, e.numero, e.creeLe, e.total, e.especes,
                e.mobile, e.carte, e.recu, e.rendu, e.referenceMobile);
    }
}
