-- Paramètres de l'entreprise : identité légale, monnaies, thème des documents, logo.
CREATE TABLE kernel_entreprise (
    tenant_id          VARCHAR(36)  NOT NULL,
    denomination       VARCHAR(120) NOT NULL,
    sigle              VARCHAR(60)  NOT NULL,
    forme_juridique    VARCHAR(30)  NOT NULL,
    capital            BIGINT       NULL,
    pays               VARCHAR(2)   NOT NULL,
    ville              VARCHAR(60)  NOT NULL,
    adresse            VARCHAR(190) NOT NULL,
    boite_postale      VARCHAR(60)  NOT NULL,
    telephone          VARCHAR(60)  NOT NULL,
    email              VARCHAR(190) NOT NULL,
    site_web           VARCHAR(190) NOT NULL,
    devise             VARCHAR(3)   NOT NULL,
    autres_devises     VARCHAR(100) NOT NULL,
    police             VARCHAR(20)  NOT NULL,
    taille_texte       INT          NOT NULL,
    couleur_principale VARCHAR(7)   NOT NULL,
    couleur_texte      VARCHAR(7)   NOT NULL,
    mentions           VARCHAR(300) NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_kernel_entreprise_organisation FOREIGN KEY (tenant_id) REFERENCES kernel_organisation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Identifiants attribués par les administrations du pays : RCCM, NIF / IFU / NCC / NINEA, sécurité sociale…
-- code : le champ tel que le noyau le définit pour le pays (« rccm », « nif », « cnss »).
CREATE TABLE kernel_entreprise_identifiant (
    id        VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    code      VARCHAR(30) NOT NULL,
    valeur    VARCHAR(60) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_kernel_entreprise_identifiant UNIQUE (tenant_id, code),
    CONSTRAINT fk_kernel_entreprise_identifiant FOREIGN KEY (tenant_id) REFERENCES kernel_entreprise (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Le logo, à part : il est lourd et ne se lit que pour être affiché. PNG ou JPEG, 512 Ko au plus.
CREATE TABLE kernel_entreprise_logo (
    tenant_id VARCHAR(36) NOT NULL,
    type_mime VARCHAR(20) NOT NULL,
    contenu   MEDIUMBLOB  NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_kernel_entreprise_logo FOREIGN KEY (tenant_id) REFERENCES kernel_entreprise (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
