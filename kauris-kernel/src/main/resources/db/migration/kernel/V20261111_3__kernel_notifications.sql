-- Notifications des membres, et boîte d'envoi (« outbox ») : ce qui doit partir vers l'extérieur est écrit
-- dans la même transaction que le changement métier, puis envoyé par un relais.

CREATE TABLE kernel_notification (
    id        VARCHAR(36)  NOT NULL,
    tenant_id VARCHAR(36)  NOT NULL,
    membre_id VARCHAR(36)  NOT NULL,
    titre     VARCHAR(120) NOT NULL,
    message   VARCHAR(500) NOT NULL,
    lien      VARCHAR(255) NULL,
    cree_le   DATETIME(6)  NOT NULL,
    lue_le    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_kernel_notification_organisation FOREIGN KEY (tenant_id) REFERENCES kernel_organisation (id),
    CONSTRAINT fk_kernel_notification_membre FOREIGN KEY (membre_id) REFERENCES kernel_membre (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_kernel_notification_membre ON kernel_notification (tenant_id, membre_id, cree_le);

CREATE TABLE kernel_boite_envoi (
    id                VARCHAR(36)   NOT NULL,
    tenant_id         VARCHAR(36)   NOT NULL,
    type              VARCHAR(40)   NOT NULL,
    contenu           VARCHAR(2000) NOT NULL,
    cree_le           DATETIME(6)   NOT NULL,
    prochain_essai_le DATETIME(6)   NOT NULL,
    envoye_le         DATETIME(6)   NULL,
    tentatives        INT           NOT NULL,
    derniere_erreur   VARCHAR(500)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_kernel_boite_envoi_attente ON kernel_boite_envoi (envoye_le, prochain_essai_le);
