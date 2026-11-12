package com.venach.kauris.ventes.achats.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Les tables des achats (migration db/migration/achats). */
final class EntitesAchats {

    private EntitesAchats() {
    }

    @Entity(name = "AchatsFournisseur")
    @Table(name = "achats_fournisseur")
    static class FournisseurEntity {
        @Id @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "nom", length = 150, nullable = false) String nom;
        @Column(name = "contact", length = 120, nullable = false) String contact;
        @Column(name = "email", length = 190, nullable = false) String email;
        @Column(name = "telephone", length = 40, nullable = false) String telephone;
        @Column(name = "adresse", length = 190, nullable = false) String adresse;
        @Column(name = "ville", length = 80, nullable = false) String ville;
        @Column(name = "pays", length = 60, nullable = false) String pays;
        @Column(name = "nif", length = 60, nullable = false) String nif;
        @Column(name = "rccm", length = 60, nullable = false) String rccm;
        @Column(name = "delai_paiement", nullable = false) int delaiPaiement;
        @Column(name = "notes", length = 1000, nullable = false) String notes;
        @Column(name = "actif", nullable = false) boolean actif;
        @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    @Entity(name = "AchatsCommande")
    @Table(name = "achats_commande")
    static class CommandeEntity {
        @Id @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "numero", length = 30) String numero;
        @Column(name = "statut", length = 20, nullable = false) String statut;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "fournisseur_id", length = 36, nullable = false)
        UUID fournisseurId;
        @Column(name = "fournisseur_nom", length = 150, nullable = false) String fournisseurNom;
        @Column(name = "date_commande", nullable = false) LocalDate date;
        @Column(name = "livraison_prevue") LocalDate livraisonPrevue;
        @Column(name = "objet", length = 200, nullable = false) String objet;
        @Column(name = "notes", length = 1000, nullable = false) String notes;
        @Column(name = "total_ttc", nullable = false) long totalTtc;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "cree_par", length = 36, updatable = false) UUID creePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
        @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "emise_le") Instant emiseLe;
    }

    @Entity(name = "AchatsCommandeLigne")
    @Table(name = "achats_commande_ligne")
    static class LigneCommandeEntity {
        @Id @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "commande_id", length = 36, nullable = false, updatable = false)
        UUID commandeId;
        @Column(name = "rang", nullable = false) int rang;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "article_id", length = 36) UUID articleId;
        @Column(name = "designation", length = 500, nullable = false) String designation;
        @Column(name = "quantite", precision = 15, scale = 3, nullable = false) BigDecimal quantite;
        @Column(name = "unite", length = 20, nullable = false) String unite;
        @Column(name = "prix_unitaire", nullable = false) long prixUnitaire;
        @Column(name = "taux_tva", precision = 5, scale = 2, nullable = false) BigDecimal tauxTva;
        @Column(name = "recue", precision = 15, scale = 3, nullable = false) BigDecimal recue;
    }

    @Entity(name = "AchatsReception")
    @Table(name = "achats_reception")
    static class ReceptionEntity {
        @Id @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "numero", length = 30, nullable = false, updatable = false) String numero;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "commande_id", length = 36, nullable = false, updatable = false)
        UUID commandeId;
        @Column(name = "commande_numero", length = 30, updatable = false) String commandeNumero;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "fournisseur_id", length = 36, nullable = false, updatable = false)
        UUID fournisseurId;
        @Column(name = "fournisseur_nom", length = 150, nullable = false, updatable = false) String fournisseurNom;
        @Column(name = "date_reception", nullable = false, updatable = false) LocalDate date;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "depot_id", length = 36, nullable = false, updatable = false)
        UUID depotId;
        @Column(name = "reference", length = 60, nullable = false, updatable = false) String reference;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "cree_par", length = 36, updatable = false) UUID creePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    @Entity(name = "AchatsReceptionLigne")
    @Table(name = "achats_reception_ligne")
    static class LigneReceptionEntity {
        @Id @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "reception_id", length = 36, nullable = false, updatable = false)
        UUID receptionId;
        @Column(name = "rang", nullable = false, updatable = false) int rang;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "article_id", length = 36, updatable = false) UUID articleId;
        @Column(name = "designation", length = 500, nullable = false, updatable = false) String designation;
        @Column(name = "quantite", precision = 15, scale = 3, nullable = false, updatable = false) BigDecimal quantite;
        @Column(name = "unite", length = 20, nullable = false, updatable = false) String unite;
        @Column(name = "cout_unitaire", nullable = false, updatable = false) long coutUnitaire;
    }

    @Entity(name = "AchatsFacture")
    @Table(name = "achats_facture")
    static class FactureEntity {
        @Id @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "numero", length = 30, nullable = false, updatable = false) String numero;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "fournisseur_id", length = 36, nullable = false, updatable = false)
        UUID fournisseurId;
        @Column(name = "fournisseur_nom", length = 150, nullable = false) String fournisseurNom;
        @Column(name = "reference_fournisseur", length = 60, nullable = false) String referenceFournisseur;
        @Column(name = "date_facture", nullable = false) LocalDate date;
        @Column(name = "echeance") LocalDate echeance;
        @Column(name = "objet", length = 200, nullable = false) String objet;
        @Column(name = "montant_ht", nullable = false) long montantHt;
        @Column(name = "montant_tva", nullable = false) long montantTva;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "commande_id", length = 36) UUID commandeId;
        @Column(name = "commande_numero", length = 30) String commandeNumero;
        @Column(name = "regle", nullable = false) long regle;
        @Column(name = "statut", length = 20, nullable = false) String statut;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "cree_par", length = 36, updatable = false) UUID creePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    @Entity(name = "AchatsReglement")
    @Table(name = "achats_reglement")
    static class ReglementEntity {
        @Id @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "facture_id", length = 36, nullable = false, updatable = false)
        UUID factureId;
        @Column(name = "date_reglement", nullable = false) LocalDate date;
        @Column(name = "montant", nullable = false) long montant;
        @Column(name = "mode", length = 20, nullable = false) String mode;
        @Column(name = "reference", length = 80, nullable = false) String reference;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "compte_id", length = 36, updatable = false) UUID compteId;
        @Column(name = "annule", nullable = false) boolean annule;
        @Column(name = "motif_annulation", length = 200, nullable = false) String motifAnnulation;
        @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "cree_par", length = 36, updatable = false) UUID creePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }
}
