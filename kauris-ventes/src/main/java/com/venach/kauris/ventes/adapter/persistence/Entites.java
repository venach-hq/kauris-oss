package com.venach.kauris.ventes.adapter.persistence;

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

/** Les tables des ventes (migration db/migration/ventes). */
final class Entites {

    private Entites() {
    }

    @Entity(name = "VentesClient")
    @Table(name = "ventes_client")
    static class ClientEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "type", length = 20, nullable = false)
        String type;
        @Column(name = "nom", length = 150, nullable = false)
        String nom;
        @Column(name = "contact", length = 120, nullable = false)
        String contact;
        @Column(name = "email", length = 190, nullable = false)
        String email;
        @Column(name = "telephone", length = 40, nullable = false)
        String telephone;
        @Column(name = "adresse", length = 190, nullable = false)
        String adresse;
        @Column(name = "ville", length = 80, nullable = false)
        String ville;
        @Column(name = "pays", length = 60, nullable = false)
        String pays;
        @Column(name = "nif", length = 60, nullable = false)
        String nif;
        @Column(name = "rccm", length = 60, nullable = false)
        String rccm;
        @Column(name = "delai_paiement", nullable = false)
        int delaiPaiement;
        @Column(name = "notes", length = 1000, nullable = false)
        String notes;
        @Column(name = "actif", nullable = false)
        boolean actif;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    @Entity(name = "VentesArticle")
    @Table(name = "ventes_article")
    static class ArticleEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "type", length = 20, nullable = false)
        String type;
        @Column(name = "reference", length = 40, nullable = false)
        String reference;
        @Column(name = "designation", length = 200, nullable = false)
        String designation;
        @Column(name = "unite", length = 20, nullable = false)
        String unite;
        @Column(name = "prix_ht", nullable = false)
        long prixHt;
        @Column(name = "taux_tva", precision = 5, scale = 2, nullable = false)
        BigDecimal tauxTva;
        @Column(name = "actif", nullable = false)
        boolean actif;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    @Entity(name = "VentesDocument")
    @Table(name = "ventes_document")
    static class DocumentEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "type", length = 20, nullable = false, updatable = false)
        String type;
        @Column(name = "numero", length = 30)
        String numero;
        @Column(name = "statut", length = 20, nullable = false)
        String statut;
        @Column(name = "date_document", nullable = false)
        LocalDate date;
        @Column(name = "echeance")
        LocalDate echeance;
        @Column(name = "objet", length = 200, nullable = false)
        String objet;
        @Column(name = "reference", length = 80, nullable = false)
        String reference;
        @Column(name = "remise_globale", precision = 5, scale = 2, nullable = false)
        BigDecimal remiseGlobale;
        @Column(name = "notes", length = 1000, nullable = false)
        String notes;
        @Column(name = "mention_tva", length = 200, nullable = false)
        String mentionTva;
        @Column(name = "conditions", length = 1000, nullable = false)
        String conditions;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "origine_id", length = 36, updatable = false)
        UUID origineId;
        @Column(name = "origine_numero", length = 30, updatable = false)
        String origineNumero;
        @Column(name = "regle", nullable = false)
        long regle;
        @Column(name = "credite", nullable = false)
        long credite;
        @Column(name = "total_ht", nullable = false)
        long totalHt;
        @Column(name = "total_tva", nullable = false)
        long totalTva;
        @Column(name = "total_ttc", nullable = false)
        long totalTtc;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "client_id", length = 36, nullable = false)
        UUID clientId;
        @Column(name = "client_type", length = 20, nullable = false)
        String clientType;
        @Column(name = "client_nom", length = 150, nullable = false)
        String clientNom;
        @Column(name = "client_contact", length = 120, nullable = false)
        String clientContact;
        @Column(name = "client_email", length = 190, nullable = false)
        String clientEmail;
        @Column(name = "client_telephone", length = 40, nullable = false)
        String clientTelephone;
        @Column(name = "client_adresse", length = 190, nullable = false)
        String clientAdresse;
        @Column(name = "client_ville", length = 80, nullable = false)
        String clientVille;
        @Column(name = "client_pays", length = 60, nullable = false)
        String clientPays;
        @Column(name = "client_nif", length = 60, nullable = false)
        String clientNif;
        @Column(name = "client_rccm", length = 60, nullable = false)
        String clientRccm;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "cree_par", length = 36, updatable = false)
        UUID creePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "emis_le")
        Instant emisLe;
    }

    @Entity(name = "VentesLigne")
    @Table(name = "ventes_ligne")
    static class LigneEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "document_id", length = 36, nullable = false, updatable = false)
        UUID documentId;
        @Column(name = "rang", nullable = false)
        int rang;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "article_id", length = 36)
        UUID articleId;
        @Column(name = "designation", length = 500, nullable = false)
        String designation;
        @Column(name = "quantite", precision = 15, scale = 3, nullable = false)
        BigDecimal quantite;
        @Column(name = "unite", length = 20, nullable = false)
        String unite;
        @Column(name = "prix_unitaire", nullable = false)
        long prixUnitaire;
        @Column(name = "remise", precision = 5, scale = 2, nullable = false)
        BigDecimal remise;
        @Column(name = "taux_tva", precision = 5, scale = 2, nullable = false)
        BigDecimal tauxTva;
    }

    @Entity(name = "VentesReglement")
    @Table(name = "ventes_reglement")
    static class ReglementEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "id", length = 36, nullable = false, updatable = false)
        UUID id;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "facture_id", length = 36, nullable = false, updatable = false)
        UUID factureId;
        @Column(name = "date_reglement", nullable = false)
        LocalDate date;
        @Column(name = "montant", nullable = false)
        long montant;
        @Column(name = "mode", length = 20, nullable = false)
        String mode;
        @Column(name = "reference", length = 80, nullable = false)
        String reference;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "compte_id", length = 36, updatable = false)
        UUID compteId;
        @Column(name = "annule", nullable = false)
        boolean annule;
        @Column(name = "motif_annulation", length = 200, nullable = false)
        String motifAnnulation;
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "cree_par", length = 36, updatable = false)
        UUID creePar;
        @JdbcTypeCode(SqlTypes.TIMESTAMP)
        @Column(name = "cree_le", nullable = false, updatable = false)
        Instant creeLe;
    }

    @Entity(name = "VentesReglages")
    @Table(name = "ventes_reglages")
    static class ReglagesEntity {
        @Id
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "tenant_id", length = 36, nullable = false, updatable = false)
        UUID tenantId;
        @Column(name = "assujetti_tva", nullable = false)
        boolean assujettiTva;
        @Column(name = "taux_tva", precision = 5, scale = 2, nullable = false)
        BigDecimal tauxTva;
        @Column(name = "mention_tva", length = 200, nullable = false)
        String mentionTva;
        @Column(name = "delai_paiement", nullable = false)
        int delaiPaiement;
        @Column(name = "validite_devis", nullable = false)
        int validiteDevis;
        @Column(name = "conditions", length = 1000, nullable = false)
        String conditions;
    }
}
