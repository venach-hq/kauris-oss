-- Comptabilité SYSCOHADA : paramètres, plan comptable, journaux, exercices, écritures et leurs lignes, opérations
-- reçues des autres modules (une seule fois par origine et référence), imputations, compteurs de numéros.
-- Les instants (cree_le…) sont en millisecondes depuis 1970 (UTC). Montants en francs CFA, entiers.

CREATE TABLE compta_parametres (
    tenant_id        VARCHAR(36) NOT NULL,
    systeme          VARCHAR(10) NOT NULL,
    validation_auto  BOOLEAN     NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE compta_compte (
    tenant_id  VARCHAR(36)  NOT NULL,
    numero     VARCHAR(12)  NOT NULL,
    libelle    VARCHAR(120) NOT NULL,
    lettrable  BOOLEAN      NOT NULL,
    actif      BOOLEAN      NOT NULL,
    PRIMARY KEY (tenant_id, numero)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE compta_journal (
    tenant_id  VARCHAR(36) NOT NULL,
    code       VARCHAR(4)  NOT NULL,
    libelle    VARCHAR(60) NOT NULL,
    systeme    BOOLEAN     NOT NULL,
    PRIMARY KEY (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE compta_exercice (
    tenant_id  VARCHAR(36)  NOT NULL,
    annee      INT          NOT NULL,
    clos       BOOLEAN      NOT NULL,
    clos_le    BIGINT       NULL,
    clos_par   VARCHAR(120) NOT NULL,
    PRIMARY KEY (tenant_id, annee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- numero est NULL au brouillard, unique une fois l'écriture validée.
CREATE TABLE compta_ecriture (
    id            VARCHAR(36)  NOT NULL,
    tenant_id     VARCHAR(36)  NOT NULL,
    journal       VARCHAR(4)   NOT NULL,
    numero        VARCHAR(30)  NULL,
    date_ecriture DATE         NOT NULL,
    piece         VARCHAR(40)  NOT NULL,
    libelle       VARCHAR(160) NOT NULL,
    statut        VARCHAR(12)  NOT NULL,
    origine       VARCHAR(100) NOT NULL,
    reference     VARCHAR(140) NOT NULL,
    inverse_de    VARCHAR(36)  NULL,
    cree_le       BIGINT       NOT NULL,
    cree_par      VARCHAR(120) NOT NULL,
    validee_le    BIGINT       NULL,
    validee_par   VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compta_ecriture_numero (tenant_id, numero),
    KEY ix_compta_ecriture_date (tenant_id, date_ecriture),
    KEY ix_compta_ecriture_reference (tenant_id, origine, reference),
    KEY ix_compta_ecriture_inverse (tenant_id, inverse_de)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE compta_ligne (
    ecriture_id  VARCHAR(36)  NOT NULL,
    rang         INT          NOT NULL,
    tenant_id    VARCHAR(36)  NOT NULL,
    compte       VARCHAR(12)  NOT NULL,
    tiers        VARCHAR(60)  NOT NULL,
    tiers_nom    VARCHAR(120) NOT NULL,
    libelle      VARCHAR(160) NOT NULL,
    debit        BIGINT       NOT NULL,
    credit       BIGINT       NOT NULL,
    lettrage     VARCHAR(10)  NOT NULL,
    PRIMARY KEY (ecriture_id, rang),
    KEY ix_compta_ligne_compte (tenant_id, compte),
    CONSTRAINT fk_compta_ligne_ecriture FOREIGN KEY (ecriture_id) REFERENCES compta_ecriture (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE compta_operation (
    id              VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(36)  NOT NULL,
    origine         VARCHAR(100) NOT NULL,
    reference       VARCHAR(100) NOT NULL,
    date_operation  DATE         NOT NULL,
    contenu         MEDIUMTEXT   NOT NULL,
    statut          VARCHAR(25)  NOT NULL,
    message         VARCHAR(300) NOT NULL,
    ecriture_id     VARCHAR(36)  NULL,
    recue_le        BIGINT       NOT NULL,
    annulee_le      DATE         NULL,
    motif           VARCHAR(300) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compta_operation (tenant_id, origine, reference),
    KEY ix_compta_operation_statut (tenant_id, statut)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- detail : précision du rôle (mode de paiement, compte de trésorerie, taux) ; vide pour tout le rôle.
CREATE TABLE compta_imputation (
    tenant_id  VARCHAR(36) NOT NULL,
    role       VARCHAR(30) NOT NULL,
    detail     VARCHAR(60) NOT NULL,
    compte     VARCHAR(12) NOT NULL,
    PRIMARY KEY (tenant_id, role, detail)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE compta_compteur (
    tenant_id  VARCHAR(36) NOT NULL,
    journal    VARCHAR(4)  NOT NULL,
    annee      INT         NOT NULL,
    dernier    BIGINT      NOT NULL,
    PRIMARY KEY (tenant_id, journal, annee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
