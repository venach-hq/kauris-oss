-- Stock : dépôts (magasins, entrepôts), quantités par article et par dépôt, valeur au coût moyen unitaire
-- pondéré (CMUP) par article, et journal des mouvements (jamais modifiés : une erreur se corrige par un
-- ajustement). Quantités à trois décimales ; valeurs en unités entières de la monnaie de l'entreprise.
CREATE TABLE stock_depot (
    id         VARCHAR(36)  NOT NULL,
    tenant_id  VARCHAR(36)  NOT NULL,
    nom        VARCHAR(80)  NOT NULL,
    principal  BOOLEAN      NOT NULL,
    actif      BOOLEAN      NOT NULL,
    cree_le    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_stock_depot_tenant (tenant_id)
);

-- Une ligne par article suivi : quantité totale et valeur totale ; le CMUP = valeur / quantité.
CREATE TABLE stock_article (
    tenant_id     VARCHAR(36)   NOT NULL,
    article_id    VARCHAR(36)   NOT NULL,
    quantite      DECIMAL(15,3) NOT NULL,
    valeur        BIGINT        NOT NULL,
    dernier_cout  BIGINT        NOT NULL,
    PRIMARY KEY (tenant_id, article_id)
);

CREATE TABLE stock_quantite (
    tenant_id   VARCHAR(36)   NOT NULL,
    article_id  VARCHAR(36)   NOT NULL,
    depot_id    VARCHAR(36)   NOT NULL,
    quantite    DECIMAL(15,3) NOT NULL,
    PRIMARY KEY (tenant_id, article_id, depot_id)
);

CREATE TABLE stock_mouvement (
    id             VARCHAR(36)   NOT NULL,
    tenant_id      VARCHAR(36)   NOT NULL,
    numero         VARCHAR(30)   NOT NULL,
    date_mouvement DATE          NOT NULL,
    type           VARCHAR(30)   NOT NULL,
    article_id     VARCHAR(36)   NOT NULL,
    depot_id       VARCHAR(36)   NOT NULL,
    quantite       DECIMAL(15,3) NOT NULL,
    cout_unitaire  BIGINT        NOT NULL,
    valeur         BIGINT        NOT NULL,
    libelle        VARCHAR(200)  NOT NULL,
    reference      VARCHAR(60)   NOT NULL,
    document_id    VARCHAR(36)   NULL,
    lien_id        VARCHAR(36)   NULL,
    cree_par       VARCHAR(36)   NULL,
    cree_le        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_stock_mouvement_numero UNIQUE (tenant_id, numero),
    INDEX ix_stock_mouvement_article (tenant_id, article_id, date_mouvement),
    INDEX ix_stock_mouvement_document (tenant_id, document_id)
);

-- Numérotation des mouvements par organisation et par année, sans trou : « MS-2026-000001 ».
CREATE TABLE stock_compteur (
    tenant_id  VARCHAR(36) NOT NULL,
    annee      INT         NOT NULL,
    dernier    BIGINT      NOT NULL,
    PRIMARY KEY (tenant_id, annee)
);
