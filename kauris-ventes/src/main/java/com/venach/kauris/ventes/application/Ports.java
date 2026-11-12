package com.venach.kauris.ventes.application;

import com.venach.kauris.ventes.domain.Article;
import com.venach.kauris.ventes.domain.Client;
import com.venach.kauris.ventes.domain.DocumentVente;
import com.venach.kauris.ventes.domain.Reglement;
import com.venach.kauris.ventes.domain.ReglagesVentes;
import com.venach.kauris.ventes.domain.TypeDocument;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ce dont les ventes ont besoin pour garder leurs informations. Toutes les lectures sont limitées au tenant. */
public final class Ports {

    private Ports() {
    }

    public interface Clients {
        void sauvegarder(Client client);

        Optional<Client> client(UUID tenantId, UUID id);

        List<Client> clients(UUID tenantId);
    }

    public interface Articles {
        void sauvegarder(Article article);

        Optional<Article> article(UUID tenantId, UUID id);

        List<Article> articles(UUID tenantId);
    }

    /**
     * Recherche de documents ; un critère nul est ignoré.
     *
     * @param impayes seulement les factures émises qui ont un reste à payer
     */
    public record Filtre(TypeDocument type, UUID clientId, LocalDate du, LocalDate au, String recherche,
                         boolean impayes, int limite) {

        public static Filtre tous(int limite) {
            return new Filtre(null, null, null, null, null, false, limite);
        }
    }

    public interface Documents {
        void sauvegarder(DocumentVente document);

        Optional<DocumentVente> document(UUID tenantId, UUID id);

        /** Le document, verrouillé jusqu'à la fin de la transaction (règlements et avoirs simultanés). */
        Optional<DocumentVente> verrouiller(UUID tenantId, UUID id);

        void supprimer(UUID tenantId, UUID id);

        /** Du plus récent au plus ancien. */
        List<DocumentVente> documents(UUID tenantId, Filtre filtre);

        /** Les documents faits à partir de celui-ci (factures d'un devis, avoirs d'une facture). */
        List<DocumentVente> issusDe(UUID tenantId, UUID origineId);

        /** Le rang suivant de la numérotation de ce type et de cette année ; verrouille le compteur. */
        long prochainRang(UUID tenantId, TypeDocument type, int annee);

        /** Date du dernier document émis de ce type, s'il y en a. */
        Optional<LocalDate> derniereDateEmise(UUID tenantId, TypeDocument type);
    }

    public interface Reglements {
        void sauvegarder(Reglement reglement);

        Optional<Reglement> reglement(UUID tenantId, UUID id);

        List<Reglement> reglementsDe(UUID tenantId, UUID factureId);

        /** Règlements datés entre ces deux jours (inclus), annulés compris. */
        List<Reglement> reglementsEntre(UUID tenantId, LocalDate du, LocalDate au);
    }

    public interface Reglages {
        Optional<ReglagesVentes> reglages(UUID tenantId);

        void sauvegarder(ReglagesVentes reglages);
    }
}
