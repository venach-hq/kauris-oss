-- Rôles créés par le propriétaire pour les postes de son entreprise, avec leurs permissions.
-- permissions : codes séparés par des virgules (« entreprise.consulter,membres.consulter »).
CREATE TABLE kernel_role_personnalise (
    id          VARCHAR(36)   NOT NULL,
    tenant_id   VARCHAR(36)   NOT NULL,
    nom         VARCHAR(60)   NOT NULL,
    description VARCHAR(200)  NOT NULL,
    permissions VARCHAR(4000) NOT NULL,
    cree_le     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_kernel_role_personnalise_nom UNIQUE (tenant_id, nom),
    CONSTRAINT fk_kernel_role_personnalise_organisation FOREIGN KEY (tenant_id) REFERENCES kernel_organisation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Un rôle d'un membre est soit un rôle de base (code_role), soit un rôle personnalisé
-- (code_role = 'PERSONNALISE' et role_personnalise_id renseigné).
ALTER TABLE kernel_membre_role
    ADD COLUMN role_personnalise_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_kernel_membre_role_personnalise FOREIGN KEY (role_personnalise_id)
        REFERENCES kernel_role_personnalise (id);
