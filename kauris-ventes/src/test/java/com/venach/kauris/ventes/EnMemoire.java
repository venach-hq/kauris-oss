package com.venach.kauris.ventes;

import com.venach.kauris.ventes.application.Ports;
import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Reglement;
import com.venach.kauris.ventes.domain.ReglagesVentes;
import com.venach.kauris.ventes.domain.StatutDocument;
import com.venach.kauris.ventes.domain.TypeDocument;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Le stockage des ventes en mémoire, pour les tests du service. Copie à chaque lecture, comme une base. */
public class EnMemoire implements Ports.Clients, Ports.Articles, Ports.Documents, Ports.Reglements, Ports.Reglages {

    private final Map<UUID, Client.Etat> clients = new HashMap<>();
    private final Map<UUID, Article.Etat> articles = new HashMap<>();
    private final Map<UUID, DocumentVente.Etat> documents = new HashMap<>();
    private final Map<UUID, Reglement> reglements = new HashMap<>();
    private final Map<UUID, ReglagesVentes> reglages = new HashMap<>();
    private final Map<String, Long> compteurs = new HashMap<>();

    @Override
    public void sauvegarder(Client client) {
        clients.put(client.id(), client.etat());
    }

    @Override
    public Optional<Client> client(UUID tenantId, UUID id) {
        return Optional.ofNullable(clients.get(id)).filter(e -> e.tenantId().equals(tenantId))
                .map(Client::reconstituer);
    }

    @Override
    public List<Client> clients(UUID tenantId) {
        return clients.values().stream().filter(e -> e.tenantId().equals(tenantId)).map(Client::reconstituer)
                .toList();
    }

    @Override
    public void sauvegarder(Article article) {
        articles.put(article.id(), article.etat());
    }

    @Override
    public Optional<Article> article(UUID tenantId, UUID id) {
        return Optional.ofNullable(articles.get(id)).filter(e -> e.tenantId().equals(tenantId))
                .map(Article::reconstituer);
    }

    @Override
    public List<Article> articles(UUID tenantId) {
        return articles.values().stream().filter(e -> e.tenantId().equals(tenantId)).map(Article::reconstituer)
                .toList();
    }

    @Override
    public void sauvegarder(DocumentVente document) {
        documents.put(document.id(), document.etat());
    }

    @Override
    public Optional<DocumentVente> document(UUID tenantId, UUID id) {
        return Optional.ofNullable(documents.get(id)).filter(e -> e.tenantId().equals(tenantId))
                .map(DocumentVente::reconstituer);
    }

    @Override
    public Optional<DocumentVente> verrouiller(UUID tenantId, UUID id) {
        return document(tenantId, id);
    }

    @Override
    public void supprimer(UUID tenantId, UUID id) {
        documents.remove(id);
    }

    @Override
    public List<DocumentVente> documents(UUID tenantId, Ports.Filtre f) {
        Stream<DocumentVente> s = documents.values().stream().filter(e -> e.tenantId().equals(tenantId))
                .map(DocumentVente::reconstituer)
                .filter(d -> f.type() == null || d.type() == f.type())
                .filter(d -> f.clientId() == null || d.client().id().equals(f.clientId()))
                .filter(d -> f.du() == null || !d.contenu().date().isBefore(f.du()))
                .filter(d -> f.au() == null || !d.contenu().date().isAfter(f.au()))
                .filter(d -> !f.impayes() || (d.statut() == StatutDocument.EMIS && d.resteAPayer() > 0))
                .filter(d -> f.recherche() == null || f.recherche().isBlank()
                        || (d.numero() + " " + d.client().nom() + " " + d.contenu().objet()).toLowerCase()
                        .contains(f.recherche().toLowerCase()))
                .sorted(Comparator.comparing((DocumentVente d) -> d.contenu().date())
                        .thenComparing(DocumentVente::creeLe).reversed());
        return f.limite() > 0 ? s.limit(f.limite()).toList() : s.toList();
    }

    @Override
    public List<DocumentVente> issusDe(UUID tenantId, UUID origineId) {
        return documents.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && Objects.equals(e.origineId(), origineId))
                .map(DocumentVente::reconstituer).toList();
    }

    @Override
    public long prochainRang(UUID tenantId, TypeDocument type, int annee) {
        return compteurs.merge(tenantId + "/" + type + "/" + annee, 1L, Long::sum);
    }

    @Override
    public Optional<LocalDate> derniereDateEmise(UUID tenantId, TypeDocument type) {
        return documents.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.type() == type && e.numero() != null)
                .map(e -> e.contenu().date()).max(Comparator.naturalOrder());
    }

    @Override
    public void sauvegarder(Reglement reglement) {
        reglements.put(reglement.id(), reglement);
    }

    @Override
    public Optional<Reglement> reglement(UUID tenantId, UUID id) {
        return Optional.ofNullable(reglements.get(id)).filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public List<Reglement> reglementsDe(UUID tenantId, UUID factureId) {
        return reglements.values().stream()
                .filter(r -> r.tenantId().equals(tenantId) && r.factureId().equals(factureId)).toList();
    }

    @Override
    public List<Reglement> reglementsEntre(UUID tenantId, LocalDate du, LocalDate au) {
        return reglements.values().stream()
                .filter(r -> r.tenantId().equals(tenantId) && !r.date().isBefore(du) && !r.date().isAfter(au))
                .toList();
    }

    @Override
    public Optional<ReglagesVentes> reglages(UUID tenantId) {
        return Optional.ofNullable(reglages.get(tenantId));
    }

    @Override
    public void sauvegarder(ReglagesVentes r) {
        reglages.put(r.tenantId(), r);
    }
}
