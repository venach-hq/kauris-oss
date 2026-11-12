package com.venach.kauris.ventes.stock.application;

import com.venach.kauris.ventes.stock.domain.Depot;
import com.venach.kauris.ventes.stock.domain.MouvementStock;
import com.venach.kauris.ventes.stock.domain.Position;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Le stockage du stock. Toutes les lectures sont limitées au tenant. */
public final class PortsStock {

    private PortsStock() {
    }

    public interface Depots {
        void sauvegarder(Depot depot);

        List<Depot> depots(UUID tenantId);

        Optional<Depot> depot(UUID tenantId, UUID id);
    }

    public interface Stocks {
        /** La position de l'article, verrouillée jusqu'à la fin de la transaction ; vide si jamais bougé. */
        Optional<Position> verrouiller(UUID tenantId, UUID articleId);

        void sauvegarder(Position position);

        List<Position> positions(UUID tenantId);

        /** Quantité par dépôt d'un article. */
        Map<UUID, BigDecimal> parDepot(UUID tenantId, UUID articleId);

        /** Quantité par article dans un dépôt. */
        Map<UUID, BigDecimal> duDepot(UUID tenantId, UUID depotId);

        /** Ajoute (ou retire, si négatif) une quantité à un article dans un dépôt. */
        void ajouter(UUID tenantId, UUID articleId, UUID depotId, BigDecimal quantite);
    }

    public interface Mouvements {
        void sauvegarder(MouvementStock mouvement);

        /** Du plus récent au plus ancien ; {@code articleId} ou {@code depotId} nuls = tous. */
        List<MouvementStock> mouvements(UUID tenantId, UUID articleId, UUID depotId, LocalDate du, LocalDate au,
                                        int limite);

        List<MouvementStock> duDocument(UUID tenantId, UUID documentId);

        /** « MS-2026-000001 », sans trou ; verrouille le compteur jusqu'à la fin de la transaction. */
        String prochainNumero(UUID tenantId, int annee);
    }
}
