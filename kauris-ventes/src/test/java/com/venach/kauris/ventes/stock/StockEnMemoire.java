package com.venach.kauris.ventes.stock;

import com.venach.kauris.ventes.stock.application.PortsStock;
import com.venach.kauris.ventes.stock.domain.Depot;
import com.venach.kauris.ventes.stock.domain.MouvementStock;
import com.venach.kauris.ventes.stock.domain.Position;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Le stockage du stock en mémoire, pour les tests du service. */
public class StockEnMemoire implements PortsStock.Depots, PortsStock.Stocks, PortsStock.Mouvements {

    private final Map<UUID, Depot.Etat> depots = new HashMap<>();
    private final Map<String, Position> positions = new HashMap<>();
    private final Map<String, BigDecimal> quantites = new HashMap<>();
    public final List<MouvementStock> journal = new ArrayList<>();
    private final Map<String, Long> compteurs = new HashMap<>();

    @Override
    public void sauvegarder(Depot depot) {
        depots.put(depot.id(), depot.etat());
    }

    @Override
    public List<Depot> depots(UUID tenantId) {
        return depots.values().stream().filter(d -> d.tenantId().equals(tenantId)).map(Depot::reconstituer).toList();
    }

    @Override
    public Optional<Depot> depot(UUID tenantId, UUID id) {
        return Optional.ofNullable(depots.get(id)).filter(d -> d.tenantId().equals(tenantId)).map(Depot::reconstituer);
    }

    @Override
    public Optional<Position> verrouiller(UUID tenantId, UUID articleId) {
        return Optional.ofNullable(positions.get(tenantId + "/" + articleId));
    }

    @Override
    public void sauvegarder(Position p) {
        positions.put(p.tenantId() + "/" + p.articleId(), p);
    }

    @Override
    public List<Position> positions(UUID tenantId) {
        return positions.values().stream().filter(p -> p.tenantId().equals(tenantId)).toList();
    }

    @Override
    public Map<UUID, BigDecimal> parDepot(UUID tenantId, UUID articleId) {
        String debut = tenantId + "/" + articleId + "/";
        return quantites.entrySet().stream().filter(e -> e.getKey().startsWith(debut))
                .collect(Collectors.toMap(e -> UUID.fromString(e.getKey().substring(debut.length())),
                        Map.Entry::getValue));
    }

    @Override
    public Map<UUID, BigDecimal> duDepot(UUID tenantId, UUID depotId) {
        Map<UUID, BigDecimal> r = new HashMap<>();
        quantites.forEach((cle, q) -> {
            String[] p = cle.split("/");
            if (p[0].equals(tenantId.toString()) && p[2].equals(depotId.toString())) {
                r.put(UUID.fromString(p[1]), q);
            }
        });
        return r;
    }

    @Override
    public void ajouter(UUID tenantId, UUID articleId, UUID depotId, BigDecimal quantite) {
        quantites.merge(tenantId + "/" + articleId + "/" + depotId, quantite, BigDecimal::add);
    }

    @Override
    public void sauvegarder(MouvementStock m) {
        journal.add(m);
    }

    @Override
    public List<MouvementStock> mouvements(UUID tenantId, UUID articleId, UUID depotId, LocalDate du, LocalDate au,
                                           int limite) {
        return journal.stream().filter(m -> m.tenantId().equals(tenantId))
                .filter(m -> articleId == null || m.articleId().equals(articleId))
                .filter(m -> depotId == null || m.depotId().equals(depotId))
                .filter(m -> du == null || !m.date().isBefore(du))
                .filter(m -> au == null || !m.date().isAfter(au))
                .sorted(Comparator.comparing(MouvementStock::date).thenComparing(MouvementStock::numero).reversed())
                .limit(limite > 0 ? limite : Long.MAX_VALUE).toList();
    }

    @Override
    public List<MouvementStock> duDocument(UUID tenantId, UUID documentId) {
        return journal.stream().filter(m -> m.tenantId().equals(tenantId) && Objects.equals(m.documentId(),
                documentId)).toList();
    }

    @Override
    public String prochainNumero(UUID tenantId, int annee) {
        long n = compteurs.merge(tenantId + "/" + annee, 1L, Long::sum);
        return "MS-" + annee + "-" + String.format("%06d", n);
    }
}
