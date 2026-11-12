package com.venach.kauris.ventes.stock.adapter.persistence;

import com.venach.kauris.ventes.stock.application.PortsStock;
import com.venach.kauris.ventes.stock.domain.Depot;
import com.venach.kauris.ventes.stock.domain.MouvementStock;
import com.venach.kauris.ventes.stock.domain.Position;
import com.venach.kauris.ventes.stock.domain.TypeMouvement;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stockage JPA du stock (migration db/migration/stock). */
public class StockageStock implements PortsStock.Depots, PortsStock.Stocks, PortsStock.Mouvements {

    private final EntityManager em;

    public StockageStock(EntityManager em) {
        this.em = em;
    }

    // ------------------------------------------------------------------ entités

    @Entity(name = "StockDepot")
    @Table(name = "stock_depot")
    static class DepotEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "nom", length = 80, nullable = false)
        String nom;
        @Column(name = "principal", nullable = false)
        boolean principal;
        @Column(name = "actif", nullable = false)
        boolean actif;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    @Embeddable
    static class CleArticle implements Serializable {
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false)
        UUID tenantId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "article_id", length = 36, nullable = false)
        UUID articleId;

        CleArticle() {
        }

        CleArticle(UUID tenantId, UUID articleId) {
            this.tenantId = tenantId;
            this.articleId = articleId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CleArticle c && Objects.equals(tenantId, c.tenantId)
                    && Objects.equals(articleId, c.articleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, articleId);
        }
    }

    @Entity(name = "StockArticle")
    @Table(name = "stock_article")
    static class PositionEntity {
        @EmbeddedId
        CleArticle cle;
        @Column(name = "quantite", precision = 15, scale = 3, nullable = false)
        BigDecimal quantite;
        @Column(name = "valeur", nullable = false)
        long valeur;
        @Column(name = "dernier_cout", nullable = false)
        long dernierCout;
    }

    @Entity(name = "StockMouvement")
    @Table(name = "stock_mouvement")
    static class MouvementEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "numero", length = 30, nullable = false, updatable = false)
        String numero;
        @Column(name = "date_mouvement", nullable = false, updatable = false)
        LocalDate date;
        @Column(name = "type", length = 30, nullable = false, updatable = false)
        String type;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "article_id", length = 36, nullable = false, updatable = false)
        UUID articleId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "depot_id", length = 36, nullable = false, updatable = false)
        UUID depotId;
        @Column(name = "quantite", precision = 15, scale = 3, nullable = false, updatable = false)
        BigDecimal quantite;
        @Column(name = "cout_unitaire", nullable = false, updatable = false)
        long coutUnitaire;
        @Column(name = "valeur", nullable = false, updatable = false)
        long valeur;
        @Column(name = "libelle", length = 200, nullable = false, updatable = false)
        String libelle;
        @Column(name = "reference", length = 60, nullable = false, updatable = false)
        String reference;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "document_id", length = 36, updatable = false)
        UUID documentId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "lien_id", length = 36, updatable = false)
        UUID lienId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "cree_par", length = 36, updatable = false)
        UUID creePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    // ------------------------------------------------------------------ dépôts

    @Override
    public void sauvegarder(Depot depot) {
        Depot.Etat d = depot.etat();
        DepotEntity e = new DepotEntity();
        e.id = d.id();
        e.tenantId = d.tenantId();
        e.nom = d.nom();
        e.principal = d.principal();
        e.actif = d.actif();
        e.creeLe = d.creeLe();
        em.merge(e);
    }

    @Override
    public List<Depot> depots(UUID tenantId) {
        return em.createQuery("select d from StockDepot d where d.tenantId = :t", DepotEntity.class)
                .setParameter("t", tenantId).getResultList().stream().map(StockageStock::depot).toList();
    }

    @Override
    public Optional<Depot> depot(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(DepotEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageStock::depot);
    }

    private static Depot depot(DepotEntity e) {
        return Depot.reconstituer(new Depot.Etat(e.id, e.tenantId, e.nom, e.principal, e.actif, e.creeLe));
    }

    // ------------------------------------------------------------------ positions et quantités

    @Override
    public Optional<Position> verrouiller(UUID tenantId, UUID articleId) {
        // La ligne existe toujours avant le verrou : deux premiers mouvements simultanés passent l'un après l'autre.
        em.createNativeQuery("insert ignore into stock_article (tenant_id, article_id, quantite, valeur, dernier_cout)"
                        + " values (?1, ?2, 0, 0, 0)")
                .setParameter(1, tenantId.toString()).setParameter(2, articleId.toString()).executeUpdate();
        PositionEntity e = em.find(PositionEntity.class, new CleArticle(tenantId, articleId),
                LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(e).map(StockageStock::position);
    }

    @Override
    public void sauvegarder(Position p) {
        PositionEntity e = new PositionEntity();
        e.cle = new CleArticle(p.tenantId(), p.articleId());
        e.quantite = p.quantite();
        e.valeur = p.valeur();
        e.dernierCout = p.dernierCout();
        em.merge(e);
    }

    @Override
    public List<Position> positions(UUID tenantId) {
        return em.createQuery("select p from StockArticle p where p.cle.tenantId = :t", PositionEntity.class)
                .setParameter("t", tenantId).getResultList().stream().map(StockageStock::position).toList();
    }

    private static Position position(PositionEntity e) {
        return new Position(e.cle.tenantId, e.cle.articleId, e.quantite, e.valeur, e.dernierCout);
    }

    // Les quantités par dépôt sont lues et écrites en SQL direct : jamais d'entité gardée en mémoire, toujours
    // la valeur de la base, même juste après un mouvement de la même transaction.

    @Override
    public Map<UUID, BigDecimal> parDepot(UUID tenantId, UUID articleId) {
        return lire("select depot_id, quantite from stock_quantite where tenant_id = ?1 and article_id = ?2",
                tenantId, articleId);
    }

    @Override
    public Map<UUID, BigDecimal> duDepot(UUID tenantId, UUID depotId) {
        return lire("select article_id, quantite from stock_quantite where tenant_id = ?1 and depot_id = ?2",
                tenantId, depotId);
    }

    private Map<UUID, BigDecimal> lire(String sql, UUID tenantId, UUID autre) {
        Map<UUID, BigDecimal> r = new HashMap<>();
        for (Object ligne : em.createNativeQuery(sql).setParameter(1, tenantId.toString())
                .setParameter(2, autre.toString()).getResultList()) {
            Object[] l = (Object[]) ligne;
            r.put(UUID.fromString(l[0].toString()), new BigDecimal(l[1].toString()));
        }
        return r;
    }

    @Override
    public void ajouter(UUID tenantId, UUID articleId, UUID depotId, BigDecimal quantite) {
        em.flush();
        em.createNativeQuery("insert into stock_quantite (tenant_id, article_id, depot_id, quantite)"
                        + " values (?1, ?2, ?3, ?4) on duplicate key update quantite = quantite + ?4")
                .setParameter(1, tenantId.toString()).setParameter(2, articleId.toString())
                .setParameter(3, depotId.toString()).setParameter(4, quantite).executeUpdate();
    }

    // ------------------------------------------------------------------ mouvements

    @Override
    public void sauvegarder(MouvementStock m) {
        MouvementEntity e = new MouvementEntity();
        e.id = m.id();
        e.tenantId = m.tenantId();
        e.numero = m.numero();
        e.date = m.date();
        e.type = m.type().name();
        e.articleId = m.articleId();
        e.depotId = m.depotId();
        e.quantite = m.quantite();
        e.coutUnitaire = m.coutUnitaire();
        e.valeur = m.valeur();
        e.libelle = m.libelle();
        e.reference = m.reference();
        e.documentId = m.documentId();
        e.lienId = m.lienId();
        e.creePar = m.creePar();
        e.creeLe = m.creeLe();
        em.persist(e);
    }

    @Override
    public List<MouvementStock> mouvements(UUID tenantId, UUID articleId, UUID depotId, LocalDate du, LocalDate au,
                                           int limite) {
        StringBuilder jpql = new StringBuilder("select m from StockMouvement m where m.tenantId = :t");
        if (articleId != null) {
            jpql.append(" and m.articleId = :a");
        }
        if (depotId != null) {
            jpql.append(" and m.depotId = :d");
        }
        if (du != null) {
            jpql.append(" and m.date >= :du");
        }
        if (au != null) {
            jpql.append(" and m.date <= :au");
        }
        jpql.append(" order by m.date desc, m.numero desc");
        TypedQuery<MouvementEntity> q = em.createQuery(jpql.toString(), MouvementEntity.class).setParameter("t", tenantId);
        if (articleId != null) {
            q.setParameter("a", articleId);
        }
        if (depotId != null) {
            q.setParameter("d", depotId);
        }
        if (du != null) {
            q.setParameter("du", du);
        }
        if (au != null) {
            q.setParameter("au", au);
        }
        if (limite > 0) {
            q.setMaxResults(limite);
        }
        return q.getResultList().stream().map(StockageStock::mouvement).toList();
    }

    @Override
    public List<MouvementStock> duDocument(UUID tenantId, UUID documentId) {
        return em.createQuery("select m from StockMouvement m where m.tenantId = :t and m.documentId = :d",
                        MouvementEntity.class)
                .setParameter("t", tenantId).setParameter("d", documentId).getResultList().stream()
                .map(StockageStock::mouvement).toList();
    }

    @Override
    public String prochainNumero(UUID tenantId, int annee) {
        em.createNativeQuery("insert into stock_compteur (tenant_id, annee, dernier) values (?1, ?2, 1)"
                        + " on duplicate key update dernier = dernier + 1")
                .setParameter(1, tenantId.toString()).setParameter(2, annee).executeUpdate();
        Number n = (Number) em.createNativeQuery("select dernier from stock_compteur where tenant_id = ?1 and annee = ?2")
                .setParameter(1, tenantId.toString()).setParameter(2, annee).getSingleResult();
        return "MS-" + annee + "-" + String.format("%06d", n.longValue());
    }

    private static MouvementStock mouvement(MouvementEntity e) {
        return new MouvementStock(e.id, e.tenantId, e.numero, e.date, TypeMouvement.valueOf(e.type), e.articleId,
                e.depotId, e.quantite, e.coutUnitaire, e.valeur, e.libelle, e.reference, e.documentId, e.lienId,
                e.creePar, e.creeLe);
    }
}
