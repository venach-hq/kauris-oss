-- Achats : fournisseurs, bons de commande (BC) et leurs lignes, bons de réception (BR) et leurs lignes, factures
-- fournisseurs (FF), règlements, numérotation sans trou par organisation, type et année.
CREATE TABLE achats_fournisseur (
    id              VARCHAR(36)   NOT NULL,
    tenant_id       VARCHAR(36)   NOT NULL,
    nom             VARCHAR(150)  NOT NULL,
    contact         VARCHAR(120)  NOT NULL,
    email           VARCHAR(190)  NOT NULL,
    telephone       VARCHAR(40)   NOT NULL,
    adresse         VARCHAR(190)  NOT NULL,
    ville           VARCHAR(80)   NOT NULL,
    pays            VARCHAR(60)   NOT NULL,
    nif             VARCHAR(60)   NOT NULL,
    rccm            VARCHAR(60)   NOT NULL,
    delai_paiement  INT           NOT NULL,
    notes           VARCHAR(1000) NOT NULL,
    actif           BOOLEAN       NOT NULL,
    cree_le         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_achats_fournisseur_tenant (tenant_id, nom)
);

CREATE TABLE achats_commande (
    id                VARCHAR(36)   NOT NULL,
    tenant_id         VARCHAR(36)   NOT NULL,
    numero            VARCHAR(30)   NULL,
    statut            VARCHAR(20)   NOT NULL,
    fournisseur_id    VARCHAR(36)   NOT NULL,
    fournisseur_nom   VARCHAR(150)  NOT NULL,
    date_commande     DATE          NOT NULL,
    livraison_prevue  DATE          NULL,
    objet             VARCHAR(200)  NOT NULL,
    notes             VARCHAR(1000) NOT NULL,
    total_ttc         BIGINT        NOT NULL,
    cree_par          VARCHAR(36)   NULL,
    cree_le           DATETIME(6)   NOT NULL,
    emise_le          DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_achats_commande_numero UNIQUE (tenant_id, numero),
    CONSTRAINT fk_achats_commande_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES achats_fournisseur (id),
    INDEX ix_achats_commande_liste (tenant_id, cree_le)
);

CREATE TABLE achats_commande_ligne (
    id              VARCHAR(36)   NOT NULL,
    tenant_id       VARCHAR(36)   NOT NULL,
    commande_id     VARCHAR(36)   NOT NULL,
    rang            INT           NOT NULL,
    article_id      VARCHAR(36)   NULL,
    designation     VARCHAR(500)  NOT NULL,
    quantite        DECIMAL(15,3) NOT NULL,
    unite           VARCHAR(20)   NOT NULL,
    prix_unitaire   BIGINT        NOT NULL,
    taux_tva        DECIMAL(5,2)  NOT NULL,
    recue           DECIMAL(15,3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_achats_commande_ligne UNIQUE (commande_id, rang),
    CONSTRAINT fk_achats_commande_ligne FOREIGN KEY (commande_id) REFERENCES achats_commande (id) ON DELETE CASCADE
);

CREATE TABLE achats_reception (
    id               VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(36)  NOT NULL,
    numero           VARCHAR(30)  NOT NULL,
    commande_id      VARCHAR(36)  NOT NULL,
    commande_numero  VARCHAR(30)  NULL,
    fournisseur_id   VARCHAR(36)  NOT NULL,
    fournisseur_nom  VARCHAR(150) NOT NULL,
    date_reception   DATE         NOT NULL,
    depot_id         VARCHAR(36)  NOT NULL,
    reference        VARCHAR(60)  NOT NULL,
    cree_par         VARCHAR(36)  NULL,
    cree_le          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_achats_reception_numero UNIQUE (tenant_id, numero),
    CONSTRAINT fk_achats_reception_commande FOREIGN KEY (commande_id) REFERENCES achats_commande (id),
    INDEX ix_achats_reception_commande (tenant_id, commande_id)
);

CREATE TABLE achats_reception_ligne (
    id             VARCHAR(36)   NOT NULL,
    reception_id   VARCHAR(36)   NOT NULL,
    rang           INT           NOT NULL,
    article_id     VARCHAR(36)   NULL,
    designation    VARCHAR(500)  NOT NULL,
    quantite       DECIMAL(15,3) NOT NULL,
    unite          VARCHAR(20)   NOT NULL,
    cout_unitaire  BIGINT        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_achats_reception_ligne FOREIGN KEY (reception_id) REFERENCES achats_reception (id)
);

CREATE TABLE achats_facture (
    id                     VARCHAR(36)  NOT NULL,
    tenant_id              VARCHAR(36)  NOT NULL,
    numero                 VARCHAR(30)  NOT NULL,
    fournisseur_id         VARCHAR(36)  NOT NULL,
    fournisseur_nom        VARCHAR(150) NOT NULL,
    reference_fournisseur  VARCHAR(60)  NOT NULL,
    date_facture           DATE         NOT NULL,
    echeance               DATE         NULL,
    objet                  VARCHAR(200) NOT NULL,
    montant_ht             BIGINT       NOT NULL,
    montant_tva            BIGINT       NOT NULL,
    commande_id            VARCHAR(36)  NULL,
    commande_numero        VARCHAR(30)  NULL,
    regle                  BIGINT       NOT NULL,
    statut                 VARCHAR(20)  NOT NULL,
    cree_par               VARCHAR(36)  NULL,
    cree_le                DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_achats_facture_numero UNIQUE (tenant_id, numero),
    CONSTRAINT fk_achats_facture_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES achats_fournisseur (id),
    INDEX ix_achats_facture_fournisseur (tenant_id, fournisseur_id, reference_fournisseur)
);

CREATE TABLE achats_reglement (
    id                VARCHAR(36)  NOT NULL,
    tenant_id         VARCHAR(36)  NOT NULL,
    facture_id        VARCHAR(36)  NOT NULL,
    date_reglement    DATE         NOT NULL,
    montant           BIGINT       NOT NULL,
    mode              VARCHAR(20)  NOT NULL,
    reference         VARCHAR(80)  NOT NULL,
    compte_id         VARCHAR(36)  NULL,
    annule            BOOLEAN      NOT NULL,
    motif_annulation  VARCHAR(200) NOT NULL,
    cree_par          VARCHAR(36)  NULL,
    cree_le           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_achats_reglement_facture FOREIGN KEY (facture_id) REFERENCES achats_facture (id),
    INDEX ix_achats_reglement_facture (tenant_id, facture_id)
);

CREATE TABLE achats_compteur (
    tenant_id  VARCHAR(36) NOT NULL,
    prefixe    VARCHAR(5)  NOT NULL,
    annee      INT         NOT NULL,
    dernier    BIGINT      NOT NULL,
    PRIMARY KEY (tenant_id, prefixe, annee)
);
