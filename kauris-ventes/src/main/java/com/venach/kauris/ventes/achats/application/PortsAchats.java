package com.venach.kauris.ventes.achats.application;

import com.venach.kauris.kernel.organisation.application.port.in.Acces;
import com.venach.kauris.ventes.achats.domain.Commande;
import com.venach.kauris.ventes.achats.domain.FactureFournisseur;
import com.venach.kauris.ventes.achats.domain.Fournisseur;
import com.venach.kauris.ventes.achats.domain.Reception;
import com.venach.kauris.ventes.achats.domain.ReglementFournisseur;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Le stockage des achats, et ce que l'application peut y brancher. Lectures limitées au tenant. */
public final class PortsAchats {

    private PortsAchats() {
    }

    public interface Fournisseurs {
        void sauvegarder(Fournisseur f);

        Optional<Fournisseur> fournisseur(UUID tenantId, UUID id);

        List<Fournisseur> fournisseurs(UUID tenantId);
    }

    public interface Commandes {
        void sauvegarder(Commande c);

        Optional<Commande> commande(UUID tenantId, UUID id);

        /** Verrouillée jusqu'à la fin de la transaction (deux réceptions simultanées). */
        Optional<Commande> verrouillerCommande(UUID tenantId, UUID id);

        void supprimerCommande(UUID tenantId, UUID id);

        /** De la plus récente à la plus ancienne ; {@code fournisseurId} nul = toutes. */
        List<Commande> commandes(UUID tenantId, UUID fournisseurId, int limite);
    }

    public interface Receptions {
        void sauvegarder(Reception r);

        List<Reception> deLaCommande(UUID tenantId, UUID commandeId);

        Optional<Reception> reception(UUID tenantId, UUID id);
    }

    public interface Factures {
        void sauvegarder(FactureFournisseur f);

        Optional<FactureFournisseur> facture(UUID tenantId, UUID id);

        Optional<FactureFournisseur> verrouillerFacture(UUID tenantId, UUID id);

        /** De la plus récente à la plus ancienne ; {@code fournisseurId} nul = toutes. */
        List<FactureFournisseur> factures(UUID tenantId, UUID fournisseurId, int limite);

        /** Déjà enregistrée ? (même fournisseur, même numéro de facture, non annulée). */
        boolean existe(UUID tenantId, UUID fournisseurId, String referenceFournisseur);
    }

    public interface Reglements {
        void sauvegarder(ReglementFournisseur r);

        Optional<ReglementFournisseur> reglement(UUID tenantId, UUID id);

        List<ReglementFournisseur> deLaFacture(UUID tenantId, UUID factureId);
    }

    public interface Numeros {
        /** « BC-2026-0001 », « BR-… », « FF-… » : sans trou, verrouille le compteur jusqu'à la fin de la transaction. */
        String prochain(UUID tenantId, String prefixe, int annee);
    }

    /**
     * Ce que l'application ajoute aux paiements des fournisseurs (Kauris Cloud : sa trésorerie). Le mode autonome
     * n'ajoute rien ({@link #AUCUN}).
     */
    public interface Decaissements {

        record Compte(UUID id, String libelle) {
        }

        record Action(String libelle, String chemin, String aide) {
        }

        /** Les comptes d'où un règlement peut partir ; vide = la question n'est pas posée. */
        List<Compte> comptes(UUID tenantId);

        /** Les autres façons de payer cette facture (par exemple « Payer par PI-SPI »). */
        List<Action> actions(Acces acces, FactureFournisseur facture);

        /** Dans la transaction du règlement. */
        void reglementEnregistre(FactureFournisseur facture, ReglementFournisseur reglement);

        /** Dans la transaction de l'annulation. */
        void reglementAnnule(FactureFournisseur facture, ReglementFournisseur reglement);

        Decaissements AUCUN = new Decaissements() {
            @Override
            public List<Compte> comptes(UUID tenantId) {
                return List.of();
            }

            @Override
            public List<Action> actions(Acces acces, FactureFournisseur facture) {
                return List.of();
            }

            @Override
            public void reglementEnregistre(FactureFournisseur facture, ReglementFournisseur reglement) {
            }

            @Override
            public void reglementAnnule(FactureFournisseur facture, ReglementFournisseur reglement) {
            }
        };
    }
}
