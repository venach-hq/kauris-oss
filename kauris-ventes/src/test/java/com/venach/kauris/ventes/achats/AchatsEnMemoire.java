package com.venach.kauris.ventes.achats;

import com.venach.kauris.ventes.achats.application.PortsAchats;
import com.venach.kauris.ventes.achats.domain.Commande;
import com.venach.kauris.ventes.achats.domain.FactureFournisseur;
import com.venach.kauris.ventes.achats.domain.Fournisseur;
import com.venach.kauris.ventes.achats.domain.Reception;
import com.venach.kauris.ventes.achats.domain.ReglementFournisseur;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Le stockage des achats en mémoire, pour les tests du service. */
public class AchatsEnMemoire implements PortsAchats.Fournisseurs, PortsAchats.Commandes, PortsAchats.Receptions,
        PortsAchats.Factures, PortsAchats.Reglements, PortsAchats.Numeros {

    private final Map<UUID, Fournisseur.Etat> fournisseurs = new HashMap<>();
    private final Map<UUID, Commande.Etat> commandes = new HashMap<>();
    private final Map<UUID, Reception> receptions = new HashMap<>();
    private final Map<UUID, FactureFournisseur.Etat> factures = new HashMap<>();
    private final Map<UUID, ReglementFournisseur> reglements = new HashMap<>();
    private final Map<String, Long> compteurs = new HashMap<>();

    @Override
    public void sauvegarder(Fournisseur f) {
        fournisseurs.put(f.id(), f.etat());
    }

    @Override
    public Optional<Fournisseur> fournisseur(UUID tenantId, UUID id) {
        return Optional.ofNullable(fournisseurs.get(id)).filter(e -> e.tenantId().equals(tenantId))
                .map(Fournisseur::reconstituer);
    }

    @Override
    public List<Fournisseur> fournisseurs(UUID tenantId) {
        return fournisseurs.values().stream().filter(e -> e.tenantId().equals(tenantId))
                .map(Fournisseur::reconstituer).toList();
    }

    @Override
    public void sauvegarder(Commande c) {
        commandes.put(c.id(), c.etat());
    }

    @Override
    public Optional<Commande> commande(UUID tenantId, UUID id) {
        return Optional.ofNullable(commandes.get(id)).filter(e -> e.tenantId().equals(tenantId))
                .map(Commande::reconstituer);
    }

    @Override
    public Optional<Commande> verrouillerCommande(UUID tenantId, UUID id) {
        return commande(tenantId, id);
    }

    @Override
    public void supprimerCommande(UUID tenantId, UUID id) {
        commandes.remove(id);
    }

    @Override
    public List<Commande> commandes(UUID tenantId, UUID fournisseurId, int limite) {
        return commandes.values().stream().filter(e -> e.tenantId().equals(tenantId))
                .filter(e -> fournisseurId == null || e.fournisseurId().equals(fournisseurId))
                .map(Commande::reconstituer).sorted(Comparator.comparing(Commande::creeLe).reversed()).toList();
    }

    @Override
    public void sauvegarder(Reception r) {
        receptions.put(r.id(), r);
    }

    @Override
    public List<Reception> deLaCommande(UUID tenantId, UUID commandeId) {
        return receptions.values().stream().filter(r -> r.tenantId().equals(tenantId)
                && r.commandeId().equals(commandeId)).toList();
    }

    @Override
    public Optional<Reception> reception(UUID tenantId, UUID id) {
        return Optional.ofNullable(receptions.get(id)).filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public void sauvegarder(FactureFournisseur f) {
        factures.put(f.id(), f.etat());
    }

    @Override
    public Optional<FactureFournisseur> facture(UUID tenantId, UUID id) {
        return Optional.ofNullable(factures.get(id)).filter(e -> e.tenantId().equals(tenantId))
                .map(FactureFournisseur::reconstituer);
    }

    @Override
    public Optional<FactureFournisseur> verrouillerFacture(UUID tenantId, UUID id) {
        return facture(tenantId, id);
    }

    @Override
    public List<FactureFournisseur> factures(UUID tenantId, UUID fournisseurId, int limite) {
        return factures.values().stream().filter(e -> e.tenantId().equals(tenantId))
                .filter(e -> fournisseurId == null || e.fournisseurId().equals(fournisseurId))
                .map(FactureFournisseur::reconstituer).toList();
    }

    @Override
    public boolean existe(UUID tenantId, UUID fournisseurId, String reference) {
        return factures.values().stream().anyMatch(e -> e.tenantId().equals(tenantId)
                && e.fournisseurId().equals(fournisseurId) && e.referenceFournisseur().equalsIgnoreCase(reference)
                && e.statut() != FactureFournisseur.Statut.ANNULEE);
    }

    @Override
    public void sauvegarder(ReglementFournisseur r) {
        reglements.put(r.id(), r);
    }

    @Override
    public Optional<ReglementFournisseur> reglement(UUID tenantId, UUID id) {
        return Optional.ofNullable(reglements.get(id)).filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public List<ReglementFournisseur> deLaFacture(UUID tenantId, UUID factureId) {
        return reglements.values().stream().filter(r -> r.tenantId().equals(tenantId)
                && r.factureId().equals(factureId)).toList();
    }

    @Override
    public String prochain(UUID tenantId, String prefixe, int annee) {
        long n = compteurs.merge(tenantId + prefixe + annee, 1L, Long::sum);
        return prefixe + "-" + annee + "-" + String.format("%04d", n);
    }
}
