-- Ventes : clients, catalogue, devis / factures / avoirs et leurs lignes, règlements, numérotation et réglages.
-- Montants en unités entières de la monnaie de l'entreprise (francs CFA) ; pourcentages à deux décimales.
CREATE TABLE ventes_client (
    id               VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(36)   NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    nom              VARCHAR(150)  NOT NULL,
    contact          VARCHAR(120)  NOT NULL,
    email            VARCHAR(190)  NOT NULL,
    telephone        VARCHAR(40)   NOT NULL,
    adresse          VARCHAR(190)  NOT NULL,
    ville            VARCHAR(80)   NOT NULL,
    pays             VARCHAR(60)   NOT NULL,
    nif              VARCHAR(60)   NOT NULL,
    rccm             VARCHAR(60)   NOT NULL,
    delai_paiement   INT           NOT NULL,
    notes            VARCHAR(1000) NOT NULL,
    actif            BOOLEAN       NOT NULL,
    cree_le          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_ventes_client_tenant (tenant_id, nom)
);

CREATE TABLE ventes_article (
    id               VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(36)   NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    reference        VARCHAR(40)   NOT NULL,
    designation      VARCHAR(200)  NOT NULL,
    unite            VARCHAR(20)   NOT NULL,
    prix_ht          BIGINT        NOT NULL,
    taux_tva         DECIMAL(5,2)  NOT NULL,
    actif            BOOLEAN       NOT NULL,
    cree_le          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_ventes_article_tenant (tenant_id)
);

-- Un document garde une copie du client telle qu'elle était à l'émission (colonnes client_…).
CREATE TABLE ventes_document (
    id               VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(36)   NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    numero           VARCHAR(30)   NULL,
    statut           VARCHAR(20)   NOT NULL,
    date_document    DATE          NOT NULL,
    echeance         DATE          NULL,
    objet            VARCHAR(200)  NOT NULL,
    reference        VARCHAR(80)   NOT NULL,
    remise_globale   DECIMAL(5,2)  NOT NULL,
    notes            VARCHAR(1000) NOT NULL,
    mention_tva      VARCHAR(200)  NOT NULL,
    conditions       VARCHAR(1000) NOT NULL,
    origine_id       VARCHAR(36)   NULL,
    origine_numero   VARCHAR(30)   NULL,
    regle            BIGINT        NOT NULL,
    credite          BIGINT        NOT NULL,
    total_ht         BIGINT        NOT NULL,
    total_tva        BIGINT        NOT NULL,
    total_ttc        BIGINT        NOT NULL,
    client_id        VARCHAR(36)   NOT NULL,
    client_type      VARCHAR(20)   NOT NULL,
    client_nom       VARCHAR(150)  NOT NULL,
    client_contact   VARCHAR(120)  NOT NULL,
    client_email     VARCHAR(190)  NOT NULL,
    client_telephone VARCHAR(40)   NOT NULL,
    client_adresse   VARCHAR(190)  NOT NULL,
    client_ville     VARCHAR(80)   NOT NULL,
    client_pays      VARCHAR(60)   NOT NULL,
    client_nif       VARCHAR(60)   NOT NULL,
    client_rccm      VARCHAR(60)   NOT NULL,
    cree_par         VARCHAR(36)   NULL,
    cree_le          DATETIME(6)   NOT NULL,
    emis_le          DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ventes_document_numero UNIQUE (tenant_id, numero),
    INDEX ix_ventes_document_liste (tenant_id, type, date_document),
    INDEX ix_ventes_document_client (tenant_id, client_id, date_document),
    INDEX ix_ventes_document_origine (tenant_id, origine_id)
);

CREATE TABLE ventes_ligne (
    id               VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(36)   NOT NULL,
    document_id      VARCHAR(36)   NOT NULL,
    rang             INT           NOT NULL,
    article_id       VARCHAR(36)   NULL,
    designation      VARCHAR(500)  NOT NULL,
    quantite         DECIMAL(15,3) NOT NULL,
    unite            VARCHAR(20)   NOT NULL,
    prix_unitaire    BIGINT        NOT NULL,
    remise           DECIMAL(5,2)  NOT NULL,
    taux_tva         DECIMAL(5,2)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ventes_ligne_rang UNIQUE (document_id, rang),
    CONSTRAINT fk_ventes_ligne_document FOREIGN KEY (document_id) REFERENCES ventes_document (id) ON DELETE CASCADE
);

CREATE TABLE ventes_reglement (
    id               VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(36)   NOT NULL,
    facture_id       VARCHAR(36)   NOT NULL,
    date_reglement   DATE          NOT NULL,
    montant          BIGINT        NOT NULL,
    mode             VARCHAR(20)   NOT NULL,
    reference        VARCHAR(80)   NOT NULL,
    annule           BOOLEAN       NOT NULL,
    motif_annulation VARCHAR(200)  NOT NULL,
    cree_par         VARCHAR(36)   NULL,
    cree_le          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_ventes_reglement_facture (tenant_id, facture_id),
    INDEX ix_ventes_reglement_date (tenant_id, date_reglement),
    CONSTRAINT fk_ventes_reglement_facture FOREIGN KEY (facture_id) REFERENCES ventes_document (id)
);

-- Numérotation sans trou : une ligne par organisation, type de document et année, verrouillée à l'émission.
CREATE TABLE ventes_compteur (
    tenant_id        VARCHAR(36)   NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    annee            INT           NOT NULL,
    dernier          BIGINT        NOT NULL,
    PRIMARY KEY (tenant_id, type, annee)
);

CREATE TABLE ventes_reglages (
    tenant_id        VARCHAR(36)   NOT NULL,
    assujetti_tva    BOOLEAN       NOT NULL,
    taux_tva         DECIMAL(5,2)  NOT NULL,
    mention_tva      VARCHAR(200)  NOT NULL,
    delai_paiement   INT           NOT NULL,
    validite_devis   INT           NOT NULL,
    conditions       VARCHAR(1000) NOT NULL,
    PRIMARY KEY (tenant_id)
);
