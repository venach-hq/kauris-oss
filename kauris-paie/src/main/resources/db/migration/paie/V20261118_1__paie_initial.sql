-- Paie : règles de paie datées (par pays), salariés, contrats de travail, avances et prêts, mois de paie, éléments du
-- mois, bulletins figés. Un mois ouvert n'a pas de bulletin stocké : il se recalcule. À la clôture, chaque bulletin
-- est enregistré avec ses lignes, tel qu'il a été calculé et remis au salarié.
-- Les instants (cree_le…) sont en millisecondes depuis 1970 (UTC). Montants en francs CFA, entiers.

CREATE TABLE paie_salarie (
    id               VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(36)  NOT NULL,
    matricule        VARCHAR(20)  NOT NULL,
    nom              VARCHAR(80)  NOT NULL,
    prenoms          VARCHAR(120) NOT NULL,
    naissance        DATE         NULL,
    telephone        VARCHAR(25)  NOT NULL,
    email            VARCHAR(190) NOT NULL,
    adresse          VARCHAR(190) NOT NULL,
    piece_identite   VARCHAR(60)  NOT NULL,
    numero_social    VARCHAR(40)  NOT NULL,
    situation        VARCHAR(20)  NOT NULL,
    enfants          INT          NOT NULL,
    conjoint_a_charge BOOLEAN     NOT NULL,
    autres_personnes INT          NOT NULL,
    expatrie         BOOLEAN      NOT NULL,
    regime           VARCHAR(20)  NOT NULL,
    mode_paiement    VARCHAR(20)  NOT NULL,
    banque           VARCHAR(80)  NOT NULL,
    compte           VARCHAR(60)  NOT NULL,
    actif            BOOLEAN      NOT NULL,
    notes            VARCHAR(500) NOT NULL,
    cree_le          BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paie_salarie_matricule (tenant_id, matricule)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE paie_contrat (
    id            VARCHAR(36)  NOT NULL,
    tenant_id     VARCHAR(36)  NOT NULL,
    salarie_id    VARCHAR(36)  NOT NULL,
    numero        VARCHAR(20)  NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    poste         VARCHAR(80)  NOT NULL,
    categorie     VARCHAR(60)  NOT NULL,
    debut         DATE         NOT NULL,
    fin_prevue    DATE         NULL,
    salaire_base  BIGINT       NOT NULL,
    sortie        DATE         NULL,
    motif_sortie  VARCHAR(200) NOT NULL,
    notes         VARCHAR(500) NOT NULL,
    cree_le       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paie_contrat_numero (tenant_id, numero),
    INDEX ix_paie_contrat_salarie (tenant_id, salarie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Primes, indemnités et avantages versés chaque mois avec le contrat.
CREATE TABLE paie_contrat_element (
    contrat_id  VARCHAR(36) NOT NULL,
    rang        INT         NOT NULL,
    libelle     VARCHAR(80) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    montant     BIGINT      NOT NULL,
    PRIMARY KEY (contrat_id, rang)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE paie_periode (
    id         VARCHAR(36)  NOT NULL,
    tenant_id  VARCHAR(36)  NOT NULL,
    -- « 2026-11 »
    mois       CHAR(7)      NOT NULL,
    pays       VARCHAR(20)  NOT NULL,
    clos       BOOLEAN      NOT NULL,
    clos_le    BIGINT       NULL,
    clos_par   VARCHAR(150) NOT NULL,
    paye_le    DATE         NULL,
    cree_le    BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paie_periode_mois (tenant_id, mois)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Ce qui change un mois pour un salarié : absences non payées, primes et retenues du mois.
CREATE TABLE paie_variables (
    tenant_id      VARCHAR(36) NOT NULL,
    periode_id     VARCHAR(36) NOT NULL,
    salarie_id     VARCHAR(36) NOT NULL,
    jours_absence  INT         NOT NULL,
    PRIMARY KEY (periode_id, salarie_id),
    INDEX ix_paie_variables_tenant (tenant_id, periode_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE paie_variable_heures (
    periode_id  VARCHAR(36)   NOT NULL,
    salarie_id  VARCHAR(36)   NOT NULL,
    rang        INT           NOT NULL,
    libelle     VARCHAR(60)   NOT NULL,
    majoration  DECIMAL(7, 2) NOT NULL,
    nombre      DECIMAL(7, 2) NOT NULL,
    PRIMARY KEY (periode_id, salarie_id, rang)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE paie_variable_element (
    periode_id  VARCHAR(36) NOT NULL,
    salarie_id  VARCHAR(36) NOT NULL,
    rang        INT         NOT NULL,
    libelle     VARCHAR(80) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    montant     BIGINT      NOT NULL,
    PRIMARY KEY (periode_id, salarie_id, rang)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Bulletin figé à la clôture du mois : ce qui a été remis au salarié et déclaré.
CREATE TABLE paie_bulletin (
    id                   VARCHAR(36)   NOT NULL,
    tenant_id            VARCHAR(36)   NOT NULL,
    periode_id           VARCHAR(36)   NOT NULL,
    salarie_id           VARCHAR(36)   NOT NULL,
    contrat_id           VARCHAR(36)   NOT NULL,
    numero               VARCHAR(20)   NOT NULL,
    matricule            VARCHAR(20)   NOT NULL,
    nom_salarie          VARCHAR(210)  NOT NULL,
    poste                VARCHAR(80)   NOT NULL,
    categorie            VARCHAR(60)   NOT NULL,
    numero_social        VARCHAR(40)   NOT NULL,
    situation            VARCHAR(40)   NOT NULL,
    enfants              INT           NOT NULL,
    famille              VARCHAR(60)   NOT NULL,
    jours_payes          INT           NOT NULL,
    salaire_base         BIGINT        NOT NULL,
    mode_paiement        VARCHAR(20)   NOT NULL,
    banque               VARCHAR(80)   NOT NULL,
    compte               VARCHAR(60)   NOT NULL,
    regime               VARCHAR(20)   NOT NULL,
    regles               VARCHAR(80)   NOT NULL,
    impot_nom            VARCHAR(20)   NOT NULL,
    compte_net           VARCHAR(12)   NOT NULL,
    brut                 BIGINT        NOT NULL,
    brut_soumis          BIGINT        NOT NULL,
    cotisations_salarie  BIGINT        NOT NULL,
    impot                BIGINT        NOT NULL,
    retenues             BIGINT        NOT NULL,
    avantages_nature     BIGINT        NOT NULL,
    net_a_payer          BIGINT        NOT NULL,
    charges_employeur    BIGINT        NOT NULL,
    base_impot           BIGINT        NOT NULL,
    detail_impot         VARCHAR(400)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paie_bulletin_numero (tenant_id, numero),
    UNIQUE KEY uk_paie_bulletin_mois (periode_id, salarie_id),
    INDEX ix_paie_bulletin_salarie (tenant_id, salarie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE paie_bulletin_ligne (
    bulletin_id     VARCHAR(36)   NOT NULL,
    rang            INT           NOT NULL,
    code            VARCHAR(60)   NOT NULL,
    libelle         VARCHAR(160)  NOT NULL,
    nature          VARCHAR(20)   NOT NULL,
    organisme       VARCHAR(10)   NOT NULL,
    base            BIGINT        NOT NULL,
    taux_salarie    DECIMAL(7, 4) NULL,
    gain            BIGINT        NOT NULL,
    salarie         BIGINT        NOT NULL,
    taux_employeur  DECIMAL(7, 4) NULL,
    employeur       BIGINT        NOT NULL,
    compte          VARCHAR(12)   NOT NULL,
    compte_charge   VARCHAR(12)   NOT NULL,
    PRIMARY KEY (bulletin_id, rang)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Règles de paie d'un pays à partir d'un mois : cotisations, taxes, impôt, heures supplémentaires, comptes.
-- Le contenu est un document JSON (ReglesPaie.Contenu) : de nouveaux réglages s'y ajoutent sans migration.
CREATE TABLE paie_regles (
    id           VARCHAR(36)  NOT NULL,
    tenant_id    VARCHAR(36)  NOT NULL,
    pays         VARCHAR(20)  NOT NULL,
    depuis       DATE         NOT NULL,
    nom          VARCHAR(80)  NOT NULL,
    modele       VARCHAR(20)  NOT NULL,
    contenu      MEDIUMTEXT   NOT NULL,
    notes        VARCHAR(1000) NOT NULL,
    modifie_le   BIGINT       NOT NULL,
    modifie_par  VARCHAR(150) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paie_regles_date (tenant_id, pays, depuis)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Avances, acomptes et prêts au personnel ; les remboursements se lisent sur les bulletins clos (code « AV:id »).
CREATE TABLE paie_avance (
    id            VARCHAR(36)  NOT NULL,
    tenant_id     VARCHAR(36)  NOT NULL,
    salarie_id    VARCHAR(36)  NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    libelle       VARCHAR(80)  NOT NULL,
    montant       BIGINT       NOT NULL,
    mensualite    BIGINT       NOT NULL,
    premier_mois  CHAR(7)      NOT NULL,
    accordee_le   DATE         NOT NULL,
    annulee       BOOLEAN      NOT NULL,
    note          VARCHAR(300) NOT NULL,
    cree_le       BIGINT       NOT NULL,
    cree_par      VARCHAR(150) NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_paie_avance_salarie (tenant_id, salarie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE paie_compteur (
    tenant_id  VARCHAR(36) NOT NULL,
    prefixe    VARCHAR(5)  NOT NULL,
    annee      INT         NOT NULL,
    dernier    BIGINT      NOT NULL,
    PRIMARY KEY (tenant_id, prefixe, annee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
