-- Flotte : véhicules, conducteurs, contrats, versements, jours fériés, papiers.
-- Ce que doit un conducteur n'est pas stocké : il se calcule à partir du contrat et des paiements.
-- Les instants (saisi_le…) sont en millisecondes depuis 1970 (UTC), sans fuseau à deviner.

CREATE TABLE flotte_vehicule (
    id                  VARCHAR(36)  NOT NULL,
    tenant_id           VARCHAR(36)  NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    immatriculation     VARCHAR(20)  NOT NULL,
    marque              VARCHAR(60)  NOT NULL,
    modele              VARCHAR(60)  NOT NULL,
    couleur             VARCHAR(30)  NOT NULL,
    annee               INT          NULL,
    chassis             VARCHAR(40)  NOT NULL,
    mise_en_service     DATE         NULL,
    prix_achat          BIGINT       NULL,
    compteur            BIGINT       NULL,
    reference_paiement  VARCHAR(80)  NOT NULL,
    statut              VARCHAR(20)  NOT NULL,
    notes               VARCHAR(500) NOT NULL,
    cree_le             BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flotte_vehicule_immat (tenant_id, immatriculation),
    INDEX ix_flotte_vehicule_ref (tenant_id, reference_paiement)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flotte_conducteur (
    id                VARCHAR(36)  NOT NULL,
    tenant_id         VARCHAR(36)  NOT NULL,
    nom               VARCHAR(120) NOT NULL,
    telephone         VARCHAR(25)  NOT NULL,
    piece_identite    VARCHAR(60)  NOT NULL,
    permis_numero     VARCHAR(40)  NOT NULL,
    permis_categorie  VARCHAR(20)  NOT NULL,
    permis_expire_le  DATE         NULL,
    garant_nom        VARCHAR(120) NOT NULL,
    garant_telephone  VARCHAR(25)  NOT NULL,
    actif             BOOLEAN      NOT NULL,
    notes             VARCHAR(500) NOT NULL,
    cree_le           BIGINT       NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_flotte_conducteur_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flotte_contrat (
    id             VARCHAR(36)  NOT NULL,
    tenant_id      VARCHAR(36)  NOT NULL,
    numero         VARCHAR(20)  NOT NULL,
    vehicule_id    VARCHAR(36)  NOT NULL,
    conducteur_id  VARCHAR(36)  NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    debut          DATE         NOT NULL,
    fin            DATE         NULL,
    -- jours de la semaine sans versement : « SUNDAY » ou « SATURDAY,SUNDAY »
    jours_repos    VARCHAR(80)  NOT NULL,
    prix_total     BIGINT       NULL,
    statut         VARCHAR(20)  NOT NULL,
    motif_fin      VARCHAR(300) NOT NULL,
    notes          VARCHAR(500) NOT NULL,
    cree_le        BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flotte_contrat_numero (tenant_id, numero),
    INDEX ix_flotte_contrat_vehicule (tenant_id, vehicule_id),
    INDEX ix_flotte_contrat_conducteur (tenant_id, conducteur_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Montant par jour travaillé, à partir d'une date.
CREATE TABLE flotte_contrat_tarif (
    contrat_id  VARCHAR(36) NOT NULL,
    depuis      DATE        NOT NULL,
    montant     BIGINT      NOT NULL,
    PRIMARY KEY (contrat_id, depuis)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Périodes sans versement : garage, suspension.
CREATE TABLE flotte_contrat_pause (
    id          VARCHAR(36)  NOT NULL,
    contrat_id  VARCHAR(36)  NOT NULL,
    du          DATE         NOT NULL,
    au          DATE         NULL,
    motif       VARCHAR(200) NOT NULL,
    a_charge    BOOLEAN      NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_flotte_pause_contrat (contrat_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flotte_versement (
    id                VARCHAR(36)  NOT NULL,
    tenant_id         VARCHAR(36)  NOT NULL,
    contrat_id        VARCHAR(36)  NOT NULL,
    numero            VARCHAR(20)  NOT NULL,
    jour              DATE         NOT NULL,
    montant           BIGINT       NOT NULL,
    mode              VARCHAR(20)  NOT NULL,
    reference         VARCHAR(80)  NOT NULL,
    saisi_par         VARCHAR(150) NOT NULL,
    saisi_le          BIGINT       NOT NULL,
    annule            BOOLEAN      NOT NULL,
    motif_annulation  VARCHAR(200) NOT NULL,
    annule_par        VARCHAR(150) NOT NULL,
    annule_le         BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flotte_versement_numero (tenant_id, numero),
    INDEX ix_flotte_versement_contrat (tenant_id, contrat_id),
    INDEX ix_flotte_versement_jour (tenant_id, jour),
    INDEX ix_flotte_versement_ref (tenant_id, reference)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Avances, amendes, dommages (dus par le conducteur) et remises (en sa faveur).
CREATE TABLE flotte_ajustement (
    id                VARCHAR(36)  NOT NULL,
    tenant_id         VARCHAR(36)  NOT NULL,
    contrat_id        VARCHAR(36)  NOT NULL,
    jour              DATE         NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    montant           BIGINT       NOT NULL,
    motif             VARCHAR(200) NOT NULL,
    saisi_par         VARCHAR(150) NOT NULL,
    saisi_le          BIGINT       NOT NULL,
    annule            BOOLEAN      NOT NULL,
    motif_annulation  VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_flotte_ajustement_contrat (tenant_id, contrat_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flotte_ferie (
    tenant_id  VARCHAR(36) NOT NULL,
    jour       DATE        NOT NULL,
    libelle    VARCHAR(80) NOT NULL,
    PRIMARY KEY (tenant_id, jour)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flotte_document (
    id           VARCHAR(36)  NOT NULL,
    tenant_id    VARCHAR(36)  NOT NULL,
    vehicule_id  VARCHAR(36)  NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    reference    VARCHAR(60)  NOT NULL,
    emis_le      DATE         NULL,
    expire_le    DATE         NULL,
    cout         BIGINT       NULL,
    notes        VARCHAR(300) NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_flotte_document_vehicule (tenant_id, vehicule_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flotte_compteur (
    tenant_id  VARCHAR(36) NOT NULL,
    prefixe    VARCHAR(5)  NOT NULL,
    annee      INT         NOT NULL,
    dernier    BIGINT      NOT NULL,
    PRIMARY KEY (tenant_id, prefixe, annee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
