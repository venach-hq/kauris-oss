-- Noyau : organisation -> établissements -> membres -> rôles.
-- Identifiants UUID (texte, 36 caractères) et tenant_id partout ; l'organisation est son propre tenant.

CREATE TABLE kernel_organisation (
    id        VARCHAR(36)  NOT NULL,
    tenant_id VARCHAR(36)  NOT NULL,
    nom       VARCHAR(120) NOT NULL,
    statut    VARCHAR(20)  NOT NULL,
    cree_le   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_kernel_organisation_tenant CHECK (tenant_id = id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE kernel_etablissement (
    id        VARCHAR(36)  NOT NULL,
    tenant_id VARCHAR(36)  NOT NULL,
    nom       VARCHAR(120) NOT NULL,
    principal BOOLEAN      NOT NULL,
    ouvert    BOOLEAN      NOT NULL,
    cree_le   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_kernel_etablissement_nom UNIQUE (tenant_id, nom),
    CONSTRAINT fk_kernel_etablissement_organisation FOREIGN KEY (tenant_id) REFERENCES kernel_organisation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- fournisseur + sujet : la personne chez le fournisseur d'identité (« venach » ou « local »).
-- Vides tant que le membre est seulement invité.
CREATE TABLE kernel_membre (
    id          VARCHAR(36)  NOT NULL,
    tenant_id   VARCHAR(36)  NOT NULL,
    fournisseur VARCHAR(20)  NULL,
    sujet       VARCHAR(190) NULL,
    email       VARCHAR(190) NOT NULL,
    nom         VARCHAR(120) NOT NULL,
    statut      VARCHAR(20)  NOT NULL,
    cree_le     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_kernel_membre_email UNIQUE (tenant_id, email),
    CONSTRAINT uq_kernel_membre_identite UNIQUE (tenant_id, fournisseur, sujet),
    CONSTRAINT fk_kernel_membre_organisation FOREIGN KEY (tenant_id) REFERENCES kernel_organisation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_kernel_membre_identite ON kernel_membre (fournisseur, sujet);
CREATE INDEX ix_kernel_membre_invitation ON kernel_membre (email, statut);

-- etablissement_id vide : le rôle vaut pour toute l'organisation.
CREATE TABLE kernel_membre_role (
    id               VARCHAR(36) NOT NULL,
    tenant_id        VARCHAR(36) NOT NULL,
    membre_id        VARCHAR(36) NOT NULL,
    code_role        VARCHAR(30) NOT NULL,
    etablissement_id VARCHAR(36) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_kernel_membre_role_membre FOREIGN KEY (membre_id) REFERENCES kernel_membre (id),
    CONSTRAINT fk_kernel_membre_role_etablissement FOREIGN KEY (etablissement_id) REFERENCES kernel_etablissement (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_kernel_membre_role_tenant ON kernel_membre_role (tenant_id);
