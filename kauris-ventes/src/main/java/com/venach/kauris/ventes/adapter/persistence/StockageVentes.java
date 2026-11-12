package com.venach.kauris.ventes.adapter.persistence;

import com.venach.kauris.ventes.adapter.persistence.Entites.ArticleEntity;
import com.venach.kauris.ventes.adapter.persistence.Entites.ClientEntity;
import com.venach.kauris.ventes.adapter.persistence.Entites.DocumentEntity;
import com.venach.kauris.ventes.adapter.persistence.Entites.LigneEntity;
import com.venach.kauris.ventes.adapter.persistence.Entites.ReglagesEntity;
import com.venach.kauris.ventes.adapter.persistence.Entites.ReglementEntity;
import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.ClientFige;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Ligne;
import com.venach.kauris.ventes.domain.ModeReglement;
import com.venach.kauris.ventes.domain.Reglement;
import com.venach.kauris.ventes.domain.ReglagesVentes;
import com.venach.kauris.ventes.domain.StatutDocument;
import com.venach.kauris.ventes.domain.Totaux;
import com.venach.kauris.ventes.domain.TypeDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stockage JPA des ventes : un seul adaptateur pour tous ses ports. */
public class StockageVentes implements Ports.Clients, Ports.Articles, Ports.Documents, Ports.Reglements,
        Ports.Reglages {

    private final EntityManager em;

    public StockageVentes(EntityManager em) {
        this.em = em;
    }

    // ================================================================== clients

    @Override
    public void sauvegarder(Client client) {
        Client.Etat c = client.etat();
        ClientEntity e = new ClientEntity();
        e.id = c.id();
        e.tenantId = c.tenantId();
        e.type = c.type().name();
        e.nom = c.nom();
        e.contact = c.contact();
        e.email = c.email();
        e.telephone = c.telephone();
        e.adresse = c.adresse();
        e.ville = c.ville();
        e.pays = c.pays();
        e.nif = c.nif();
        e.rccm = c.rccm();
        e.delaiPaiement = c.delaiPaiement();
        e.notes = c.notes();
        e.actif = c.actif();
        e.creeLe = c.creeLe();
        em.merge(e);
    }

    @Override
    public Optional<Client> client(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(ClientEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageVentes::client);
    }

    @Override
    public List<Client> clients(UUID tenantId) {
        return em.createQuery("select c from VentesClient c where c.tenantId = :t", ClientEntity.class)
                .setParameter("t", tenantId).getResultList().stream().map(StockageVentes::client).toList();
    }

    private static Client client(ClientEntity e) {
        return Client.reconstituer(new Client.Etat(e.id, e.tenantId, Client.Type.valueOf(e.type), e.nom, e.contact,
                e.email, e.telephone, e.adresse, e.ville, e.pays, e.nif, e.rccm, e.delaiPaiement, e.notes, e.actif,
                e.creeLe));
    }

    // ================================================================== catalogue

    @Override
    public void sauvegarder(Article article) {
        Article.Etat a = article.etat();
        ArticleEntity e = new ArticleEntity();
        e.id = a.id();
        e.tenantId = a.tenantId();
        e.type = a.type().name();
        e.reference = a.reference();
        e.designation = a.designation();
        e.unite = a.unite();
        e.prixHt = a.prixHt();
        e.tauxTva = a.tauxTva();
        e.actif = a.actif();
        e.creeLe = a.creeLe();
        em.merge(e);
    }

    @Override
    public Optional<Article> article(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(ArticleEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageVentes::article);
    }

    @Override
    public List<Article> articles(UUID tenantId) {
        return em.createQuery("select a from VentesArticle a where a.tenantId = :t", ArticleEntity.class)
                .setParameter("t", tenantId).getResultList().stream().map(StockageVentes::article).toList();
    }

    private static Article article(ArticleEntity e) {
        return Article.reconstituer(new Article.Etat(e.id, e.tenantId, Article.Type.valueOf(e.type), e.reference,
                e.designation, e.unite, e.prixHt, e.tauxTva, e.actif, e.creeLe));
    }

    // ================================================================== documents

    @Override
    public void sauvegarder(DocumentVente document) {
        DocumentVente.Etat d = document.etat();
        DocumentEntity e = new DocumentEntity();
        e.id = d.id();
        e.tenantId = d.tenantId();
        e.type = d.type().name();
        e.numero = d.numero();
        e.statut = d.statut().name();
        e.date = d.contenu().date();
        e.echeance = d.contenu().echeance();
        e.objet = d.contenu().objet();
        e.reference = d.contenu().reference();
        e.remiseGlobale = d.contenu().remiseGlobale();
        e.notes = d.contenu().notes();
        e.mentionTva = d.mentionTva();
        e.conditions = d.conditions();
        e.origineId = d.origineId();
        e.origineNumero = d.origineNumero();
        e.regle = d.regle();
        e.credite = d.credite();
        Totaux t = document.totaux();
        e.totalHt = t.totalHt();
        e.totalTva = t.totalTva();
        e.totalTtc = t.totalTtc();
        ClientFige c = d.client();
        e.clientId = c.id();
        e.clientType = c.type().name();
        e.clientNom = c.nom();
        e.clientContact = c.contact();
        e.clientEmail = c.email();
        e.clientTelephone = c.telephone();
        e.clientAdresse = c.adresse();
        e.clientVille = c.ville();
        e.clientPays = c.pays();
        e.clientNif = c.nif();
        e.clientRccm = c.rccm();
        e.creePar = d.creePar();
        e.creeLe = d.creeLe();
        e.emisLe = d.emisLe();
        em.merge(e);
        // Les lignes ne changent que dans un brouillon ; on les réécrit alors toutes, dans l'ordre.
        if (d.statut() == StatutDocument.BROUILLON) {
            em.flush();
            em.createQuery("delete from VentesLigne l where l.documentId = :d").setParameter("d", d.id())
                    .executeUpdate();
            int rang = 0;
            for (Ligne l : d.contenu().lignes()) {
                LigneEntity le = new LigneEntity();
                le.id = UUID.randomUUID();
                le.tenantId = d.tenantId();
                le.documentId = d.id();
                le.rang = rang++;
                le.articleId = l.articleId();
                le.designation = l.designation();
                le.quantite = l.quantite();
                le.unite = l.unite();
                le.prixUnitaire = l.prixUnitaire();
                le.remise = l.remise();
                le.tauxTva = l.tauxTva();
                em.persist(le);
            }
        }
    }

    @Override
    public Optional<DocumentVente> document(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(DocumentEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(e -> documents(List.of(e)).get(0));
    }

    @Override
    public Optional<DocumentVente> verrouiller(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(DocumentEntity.class, id, LockModeType.PESSIMISTIC_WRITE))
                .filter(e -> e.tenantId.equals(tenantId))
                .map(e -> documents(List.of(e)).get(0));
    }

    @Override
    public void supprimer(UUID tenantId, UUID id) {
        em.createQuery("delete from VentesLigne l where l.documentId = :d and l.tenantId = :t")
                .setParameter("d", id).setParameter("t", tenantId).executeUpdate();
        em.createQuery("delete from VentesDocument d where d.id = :d and d.tenantId = :t")
                .setParameter("d", id).setParameter("t", tenantId).executeUpdate();
    }

    @Override
    public List<DocumentVente> documents(UUID tenantId, Ports.Filtre f) {
        StringBuilder jpql = new StringBuilder("select d from VentesDocument d where d.tenantId = :t");
        Map<String, Object> p = new HashMap<>();
        p.put("t", tenantId);
        if (f.type() != null) {
            jpql.append(" and d.type = :type");
            p.put("type", f.type().name());
        }
        if (f.clientId() != null) {
            jpql.append(" and d.clientId = :client");
            p.put("client", f.clientId());
        }
        if (f.du() != null) {
            jpql.append(" and d.date >= :du");
            p.put("du", f.du());
        }
        if (f.au() != null) {
            jpql.append(" and d.date <= :au");
            p.put("au", f.au());
        }
        if (f.recherche() != null && !f.recherche().isBlank()) {
            jpql.append(" and (lower(d.numero) like :r escape '!' or lower(d.clientNom) like :r escape '!'"
                    + " or lower(d.objet) like :r escape '!')");
            String echappe = f.recherche().strip().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            p.put("r", "%" + echappe + "%");
        }
        if (f.impayes()) {
            jpql.append(" and d.statut = 'EMIS' and d.totalTtc - d.regle - d.credite > 0");
        }
        jpql.append(" order by d.date desc, d.creeLe desc");
        TypedQuery<DocumentEntity> q = em.createQuery(jpql.toString(), DocumentEntity.class);
        p.forEach(q::setParameter);
        if (f.limite() > 0) {
            q.setMaxResults(f.limite());
        }
        return documents(q.getResultList());
    }

    @Override
    public List<DocumentVente> issusDe(UUID tenantId, UUID origineId) {
        return documents(em.createQuery("select d from VentesDocument d where d.tenantId = :t and d.origineId = :o"
                        + " order by d.creeLe", DocumentEntity.class)
                .setParameter("t", tenantId).setParameter("o", origineId).getResultList());
    }

    @Override
    public long prochainRang(UUID tenantId, TypeDocument type, int annee) {
        // Crée le compteur ou l'augmente ; la ligne reste verrouillée jusqu'à la fin de la transaction.
        em.createNativeQuery("insert into ventes_compteur (tenant_id, type, annee, dernier) values (?1, ?2, ?3, 1)"
                        + " on duplicate key update dernier = dernier + 1")
                .setParameter(1, tenantId.toString()).setParameter(2, type.name()).setParameter(3, annee)
                .executeUpdate();
        Number dernier = (Number) em.createNativeQuery("select dernier from ventes_compteur"
                        + " where tenant_id = ?1 and type = ?2 and annee = ?3")
                .setParameter(1, tenantId.toString()).setParameter(2, type.name()).setParameter(3, annee)
                .getSingleResult();
        return dernier.longValue();
    }

    @Override
    public Optional<LocalDate> derniereDateEmise(UUID tenantId, TypeDocument type) {
        return Optional.ofNullable(em.createQuery("select max(d.date) from VentesDocument d where d.tenantId = :t"
                        + " and d.type = :type and d.numero is not null", LocalDate.class)
                .setParameter("t", tenantId).setParameter("type", type.name()).getSingleResult());
    }

    /** Les documents avec leurs lignes, lues en une seule requête. */
    private List<DocumentVente> documents(List<DocumentEntity> entites) {
        if (entites.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Ligne>> lignes = new HashMap<>();
        for (LigneEntity l : lignesDe(entites.stream().map(e -> e.id).toList())) {
            lignes.computeIfAbsent(l.documentId, k -> new ArrayList<>()).add(new Ligne(l.articleId, l.designation,
                    l.quantite, l.unite, l.prixUnitaire, l.remise, l.tauxTva));
        }
        return entites.stream().map(e -> document(e, lignes.getOrDefault(e.id, List.of()))).toList();
    }

    private List<LigneEntity> lignesDe(List<UUID> documents) {
        List<LigneEntity> toutes = new ArrayList<>();
        // Par paquets : une liste « in » trop longue ralentit MySQL.
        for (int i = 0; i < documents.size(); i += 500) {
            toutes.addAll(em.createQuery("select l from VentesLigne l where l.documentId in :ids"
                            + " order by l.documentId, l.rang", LigneEntity.class)
                    .setParameter("ids", documents.subList(i, Math.min(documents.size(), i + 500)))
                    .getResultList());
        }
        return toutes;
    }

    private static DocumentVente document(DocumentEntity e, List<Ligne> lignes) {
        ClientFige client = new ClientFige(e.clientId, Client.Type.valueOf(e.clientType), e.clientNom,
                e.clientContact, e.clientEmail, e.clientTelephone, e.clientAdresse, e.clientVille, e.clientPays,
                e.clientNif, e.clientRccm);
        DocumentVente.Contenu contenu = new DocumentVente.Contenu(e.date, e.echeance, e.objet, e.reference, lignes,
                e.remiseGlobale, e.notes);
        return DocumentVente.reconstituer(new DocumentVente.Etat(e.id, e.tenantId, TypeDocument.valueOf(e.type),
                e.numero, StatutDocument.valueOf(e.statut), client, contenu, e.mentionTva, e.conditions, e.origineId,
                e.origineNumero, e.regle, e.credite, e.creePar, e.creeLe, e.emisLe));
    }

    // ================================================================== règlements

    @Override
    public void sauvegarder(Reglement r) {
        ReglementEntity e = new ReglementEntity();
        e.id = r.id();
        e.tenantId = r.tenantId();
        e.factureId = r.factureId();
        e.date = r.date();
        e.montant = r.montant();
        e.mode = r.mode().name();
        e.reference = r.reference();
        e.annule = r.annule();
        e.motifAnnulation = r.motifAnnulation();
        e.creePar = r.creePar();
        e.creeLe = r.creeLe();
        em.merge(e);
    }

    @Override
    public Optional<Reglement> reglement(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(ReglementEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageVentes::reglement);
    }

    @Override
    public List<Reglement> reglementsDe(UUID tenantId, UUID factureId) {
        return em.createQuery("select r from VentesReglement r where r.tenantId = :t and r.factureId = :f",
                        ReglementEntity.class)
                .setParameter("t", tenantId).setParameter("f", factureId).getResultList().stream()
                .map(StockageVentes::reglement).toList();
    }

    @Override
    public List<Reglement> reglementsEntre(UUID tenantId, LocalDate du, LocalDate au) {
        return em.createQuery("select r from VentesReglement r where r.tenantId = :t and r.date between :du and :au",
                        ReglementEntity.class)
                .setParameter("t", tenantId).setParameter("du", du).setParameter("au", au).getResultList().stream()
                .map(StockageVentes::reglement).toList();
    }

    private static Reglement reglement(ReglementEntity e) {
        return new Reglement(e.id, e.tenantId, e.factureId, e.date, e.montant, ModeReglement.valueOf(e.mode),
                e.reference, e.annule, e.motifAnnulation, e.creePar, e.creeLe);
    }

    // ================================================================== réglages

    @Override
    public Optional<ReglagesVentes> reglages(UUID tenantId) {
        return Optional.ofNullable(em.find(ReglagesEntity.class, tenantId))
                .map(e -> new ReglagesVentes(e.tenantId, e.assujettiTva, e.tauxTva, e.mentionTva, e.delaiPaiement,
                        e.validiteDevis, e.conditions));
    }

    @Override
    public void sauvegarder(ReglagesVentes r) {
        ReglagesEntity e = new ReglagesEntity();
        e.tenantId = r.tenantId();
        e.assujettiTva = r.assujettiTva();
        e.tauxTva = r.tauxTva();
        e.mentionTva = r.mentionTva();
        e.delaiPaiement = r.delaiPaiement();
        e.validiteDevis = r.validiteDevis();
        e.conditions = r.conditions();
        em.merge(e);
    }
}
