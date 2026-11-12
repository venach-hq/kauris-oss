package com.venach.kauris.ventes.achats.adapter.persistence;

import com.venach.kauris.ventes.achats.adapter.persistence.EntitesAchats.CommandeEntity;
import com.venach.kauris.ventes.achats.adapter.persistence.EntitesAchats.FactureEntity;
import com.venach.kauris.ventes.achats.adapter.persistence.EntitesAchats.FournisseurEntity;
import com.venach.kauris.ventes.achats.adapter.persistence.EntitesAchats.LigneCommandeEntity;
import com.venach.kauris.ventes.achats.adapter.persistence.EntitesAchats.LigneReceptionEntity;
import com.venach.kauris.ventes.achats.adapter.persistence.EntitesAchats.ReceptionEntity;
import com.venach.kauris.ventes.achats.adapter.persistence.EntitesAchats.ReglementEntity;
import com.venach.kauris.ventes.achats.application.PortsAchats;
import com.venach.kauris.ventes.achats.domain.Commande;
import com.venach.kauris.ventes.achats.domain.FactureFournisseur;
import com.venach.kauris.ventes.achats.domain.Fournisseur;
import com.venach.kauris.ventes.achats.domain.Reception;
import com.venach.kauris.ventes.achats.domain.ReglementFournisseur;
import com.venach.kauris.ventes.domain.ModeReglement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stockage JPA des achats : un adaptateur pour tous ses ports. */
public class StockageAchats implements PortsAchats.Fournisseurs, PortsAchats.Commandes, PortsAchats.Receptions,
        PortsAchats.Factures, PortsAchats.Reglements, PortsAchats.Numeros {

    private final EntityManager em;

    public StockageAchats(EntityManager em) {
        this.em = em;
    }

    // ================================================================== fournisseurs

    @Override
    public void sauvegarder(Fournisseur fournisseur) {
        Fournisseur.Etat f = fournisseur.etat();
        FournisseurEntity e = new FournisseurEntity();
        e.id = f.id();
        e.tenantId = f.tenantId();
        e.nom = f.nom();
        e.contact = f.contact();
        e.email = f.email();
        e.telephone = f.telephone();
        e.adresse = f.adresse();
        e.ville = f.ville();
        e.pays = f.pays();
        e.nif = f.nif();
        e.rccm = f.rccm();
        e.delaiPaiement = f.delaiPaiement();
        e.notes = f.notes();
        e.actif = f.actif();
        e.creeLe = f.creeLe();
        em.merge(e);
    }

    @Override
    public Optional<Fournisseur> fournisseur(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(FournisseurEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageAchats::fournisseur);
    }

    @Override
    public List<Fournisseur> fournisseurs(UUID tenantId) {
        return em.createQuery("select f from AchatsFournisseur f where f.tenantId = :t", FournisseurEntity.class)
                .setParameter("t", tenantId).getResultList().stream().map(StockageAchats::fournisseur).toList();
    }

    private static Fournisseur fournisseur(FournisseurEntity e) {
        return Fournisseur.reconstituer(new Fournisseur.Etat(e.id, e.tenantId, e.nom, e.contact, e.email, e.telephone,
                e.adresse, e.ville, e.pays, e.nif, e.rccm, e.delaiPaiement, e.notes, e.actif, e.creeLe));
    }

    // ================================================================== commandes

    @Override
    public void sauvegarder(Commande commande) {
        Commande.Etat c = commande.etat();
        CommandeEntity e = new CommandeEntity();
        e.id = c.id();
        e.tenantId = c.tenantId();
        e.numero = c.numero();
        e.statut = c.statut().name();
        e.fournisseurId = c.fournisseurId();
        e.fournisseurNom = c.fournisseurNom();
        e.date = c.date();
        e.livraisonPrevue = c.livraisonPrevue();
        e.objet = c.objet();
        e.notes = c.notes();
        e.totalTtc = commande.totaux().totalTtc();
        e.creePar = c.creePar();
        e.creeLe = c.creeLe();
        e.emiseLe = c.emiseLe();
        em.merge(e);
        em.flush();
        // Les lignes changent avec le brouillon et avec chaque réception : on les réécrit toutes, dans l'ordre.
        em.createQuery("delete from AchatsCommandeLigne l where l.commandeId = :c").setParameter("c", c.id())
                .executeUpdate();
        int rang = 0;
        for (Commande.LigneCommande l : c.lignes()) {
            LigneCommandeEntity le = new LigneCommandeEntity();
            le.id = UUID.randomUUID();
            le.tenantId = c.tenantId();
            le.commandeId = c.id();
            le.rang = rang++;
            le.articleId = l.articleId();
            le.designation = l.designation();
            le.quantite = l.quantite();
            le.unite = l.unite();
            le.prixUnitaire = l.prixUnitaire();
            le.tauxTva = l.tauxTva();
            le.recue = l.recue();
            em.persist(le);
        }
    }

    @Override
    public Optional<Commande> commande(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(CommandeEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(e -> commandes(List.of(e)).get(0));
    }

    @Override
    public Optional<Commande> verrouillerCommande(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(CommandeEntity.class, id, LockModeType.PESSIMISTIC_WRITE))
                .filter(e -> e.tenantId.equals(tenantId)).map(e -> commandes(List.of(e)).get(0));
    }

    @Override
    public void supprimerCommande(UUID tenantId, UUID id) {
        em.createQuery("delete from AchatsCommandeLigne l where l.commandeId = :c and l.tenantId = :t")
                .setParameter("c", id).setParameter("t", tenantId).executeUpdate();
        em.createQuery("delete from AchatsCommande c where c.id = :c and c.tenantId = :t")
                .setParameter("c", id).setParameter("t", tenantId).executeUpdate();
    }

    @Override
    public List<Commande> commandes(UUID tenantId, UUID fournisseurId, int limite) {
        TypedQuery<CommandeEntity> q = em.createQuery("select c from AchatsCommande c where c.tenantId = :t"
                + (fournisseurId == null ? "" : " and c.fournisseurId = :f") + " order by c.creeLe desc",
                CommandeEntity.class).setParameter("t", tenantId);
        if (fournisseurId != null) {
            q.setParameter("f", fournisseurId);
        }
        if (limite > 0) {
            q.setMaxResults(limite);
        }
        return commandes(q.getResultList());
    }

    private List<Commande> commandes(List<CommandeEntity> entites) {
        if (entites.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Commande.LigneCommande>> lignes = new HashMap<>();
        em.createQuery("select l from AchatsCommandeLigne l where l.commandeId in :ids order by l.commandeId, l.rang",
                        LigneCommandeEntity.class)
                .setParameter("ids", entites.stream().map(e -> e.id).toList()).getResultList()
                .forEach(l -> lignes.computeIfAbsent(l.commandeId, k -> new ArrayList<>())
                        .add(new Commande.LigneCommande(l.articleId, l.designation, l.quantite, l.unite, l.prixUnitaire,
                                l.tauxTva, l.recue)));
        return entites.stream().map(e -> Commande.reconstituer(new Commande.Etat(e.id, e.tenantId, e.numero,
                Commande.Statut.valueOf(e.statut), e.fournisseurId, e.fournisseurNom, e.date, e.livraisonPrevue,
                e.objet, e.notes, lignes.getOrDefault(e.id, List.of()), e.creePar, e.creeLe, e.emiseLe))).toList();
    }

    // ================================================================== réceptions

    @Override
    public void sauvegarder(Reception r) {
        ReceptionEntity e = new ReceptionEntity();
        e.id = r.id();
        e.tenantId = r.tenantId();
        e.numero = r.numero();
        e.commandeId = r.commandeId();
        e.commandeNumero = r.commandeNumero();
        e.fournisseurId = r.fournisseurId();
        e.fournisseurNom = r.fournisseurNom();
        e.date = r.date();
        e.depotId = r.depotId();
        e.reference = r.reference();
        e.creePar = r.creePar();
        e.creeLe = r.creeLe();
        em.persist(e);
        for (Reception.LigneRecue l : r.lignes()) {
            LigneReceptionEntity le = new LigneReceptionEntity();
            le.id = UUID.randomUUID();
            le.receptionId = r.id();
            le.rang = l.rang();
            le.articleId = l.articleId();
            le.designation = l.designation();
            le.quantite = l.quantite();
            le.unite = l.unite();
            le.coutUnitaire = l.coutUnitaire();
            em.persist(le);
        }
    }

    @Override
    public List<Reception> deLaCommande(UUID tenantId, UUID commandeId) {
        return receptions(em.createQuery("select r from AchatsReception r where r.tenantId = :t and r.commandeId = :c"
                        + " order by r.numero", ReceptionEntity.class)
                .setParameter("t", tenantId).setParameter("c", commandeId).getResultList());
    }

    @Override
    public Optional<Reception> reception(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(ReceptionEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(e -> receptions(List.of(e)).get(0));
    }

    private List<Reception> receptions(List<ReceptionEntity> entites) {
        if (entites.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Reception.LigneRecue>> lignes = new HashMap<>();
        em.createQuery("select l from AchatsReceptionLigne l where l.receptionId in :ids order by l.receptionId, l.rang",
                        LigneReceptionEntity.class)
                .setParameter("ids", entites.stream().map(e -> e.id).toList()).getResultList()
                .forEach(l -> lignes.computeIfAbsent(l.receptionId, k -> new ArrayList<>())
                        .add(new Reception.LigneRecue(l.rang, l.articleId, l.designation, l.quantite, l.unite,
                                l.coutUnitaire)));
        return entites.stream().map(e -> new Reception(e.id, e.tenantId, e.numero, e.commandeId, e.commandeNumero,
                e.fournisseurId, e.fournisseurNom, e.date, e.depotId, e.reference,
                lignes.getOrDefault(e.id, List.of()), e.creePar, e.creeLe)).toList();
    }

    // ================================================================== factures

    @Override
    public void sauvegarder(FactureFournisseur facture) {
        FactureFournisseur.Etat f = facture.etat();
        FactureEntity e = new FactureEntity();
        e.id = f.id();
        e.tenantId = f.tenantId();
        e.numero = f.numero();
        e.fournisseurId = f.fournisseurId();
        e.fournisseurNom = f.fournisseurNom();
        e.referenceFournisseur = f.referenceFournisseur();
        e.date = f.date();
        e.echeance = f.echeance();
        e.objet = f.objet();
        e.montantHt = f.montantHt();
        e.montantTva = f.montantTva();
        e.commandeId = f.commandeId();
        e.commandeNumero = f.commandeNumero();
        e.regle = f.regle();
        e.statut = f.statut().name();
        e.creePar = f.creePar();
        e.creeLe = f.creeLe();
        em.merge(e);
    }

    @Override
    public Optional<FactureFournisseur> facture(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(FactureEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageAchats::facture);
    }

    @Override
    public Optional<FactureFournisseur> verrouillerFacture(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(FactureEntity.class, id, LockModeType.PESSIMISTIC_WRITE))
                .filter(e -> e.tenantId.equals(tenantId)).map(StockageAchats::facture);
    }

    @Override
    public List<FactureFournisseur> factures(UUID tenantId, UUID fournisseurId, int limite) {
        TypedQuery<FactureEntity> q = em.createQuery("select f from AchatsFacture f where f.tenantId = :t"
                + (fournisseurId == null ? "" : " and f.fournisseurId = :f") + " order by f.date desc, f.numero desc",
                FactureEntity.class).setParameter("t", tenantId);
        if (fournisseurId != null) {
            q.setParameter("f", fournisseurId);
        }
        if (limite > 0) {
            q.setMaxResults(limite);
        }
        return q.getResultList().stream().map(StockageAchats::facture).toList();
    }

    @Override
    public boolean existe(UUID tenantId, UUID fournisseurId, String reference) {
        return !em.createQuery("select f.id from AchatsFacture f where f.tenantId = :t and f.fournisseurId = :f"
                        + " and lower(f.referenceFournisseur) = lower(:r) and f.statut <> 'ANNULEE'", UUID.class)
                .setParameter("t", tenantId).setParameter("f", fournisseurId).setParameter("r", reference)
                .setMaxResults(1).getResultList().isEmpty();
    }

    private static FactureFournisseur facture(FactureEntity e) {
        return FactureFournisseur.reconstituer(new FactureFournisseur.Etat(e.id, e.tenantId, e.numero, e.fournisseurId,
                e.fournisseurNom, e.referenceFournisseur, e.date, e.echeance, e.objet, e.montantHt, e.montantTva,
                e.commandeId, e.commandeNumero, e.regle, FactureFournisseur.Statut.valueOf(e.statut), e.creePar,
                e.creeLe));
    }

    // ================================================================== règlements

    @Override
    public void sauvegarder(ReglementFournisseur r) {
        ReglementEntity e = new ReglementEntity();
        e.id = r.id();
        e.tenantId = r.tenantId();
        e.factureId = r.factureId();
        e.date = r.date();
        e.montant = r.montant();
        e.mode = r.mode().name();
        e.reference = r.reference();
        e.compteId = r.compteId();
        e.annule = r.annule();
        e.motifAnnulation = r.motifAnnulation();
        e.creePar = r.creePar();
        e.creeLe = r.creeLe();
        em.merge(e);
    }

    @Override
    public Optional<ReglementFournisseur> reglement(UUID tenantId, UUID id) {
        return Optional.ofNullable(em.find(ReglementEntity.class, id)).filter(e -> e.tenantId.equals(tenantId))
                .map(StockageAchats::reglement);
    }

    @Override
    public List<ReglementFournisseur> deLaFacture(UUID tenantId, UUID factureId) {
        return em.createQuery("select r from AchatsReglement r where r.tenantId = :t and r.factureId = :f",
                        ReglementEntity.class)
                .setParameter("t", tenantId).setParameter("f", factureId).getResultList().stream()
                .map(StockageAchats::reglement).toList();
    }

    private static ReglementFournisseur reglement(ReglementEntity e) {
        return new ReglementFournisseur(e.id, e.tenantId, e.factureId, e.date, e.montant, ModeReglement.valueOf(e.mode),
                e.reference, e.compteId, e.annule, e.motifAnnulation, e.creePar, e.creeLe);
    }

    // ================================================================== numéros

    @Override
    public String prochain(UUID tenantId, String prefixe, int annee) {
        em.createNativeQuery("insert into achats_compteur (tenant_id, prefixe, annee, dernier) values (?1, ?2, ?3, 1)"
                        + " on duplicate key update dernier = dernier + 1")
                .setParameter(1, tenantId.toString()).setParameter(2, prefixe).setParameter(3, annee).executeUpdate();
        Number n = (Number) em.createNativeQuery("select dernier from achats_compteur where tenant_id = ?1"
                        + " and prefixe = ?2 and annee = ?3")
                .setParameter(1, tenantId.toString()).setParameter(2, prefixe).setParameter(3, annee)
                .getSingleResult();
        return prefixe + "-" + annee + "-" + String.format("%04d", n.longValue());
    }
}
