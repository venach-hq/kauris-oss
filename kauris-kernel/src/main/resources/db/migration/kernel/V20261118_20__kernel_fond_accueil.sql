-- L'image de fond du tableau de bord, propre à chaque membre. PNG, JPEG ou WebP, 3 Mo au plus.
-- Sans ligne ici, l'accueil garde son fond par défaut.
CREATE TABLE kernel_fond_accueil (
    tenant_id  VARCHAR(36) NOT NULL,
    membre_id  VARCHAR(36) NOT NULL,
    type_mime  VARCHAR(20) NOT NULL,
    contenu    MEDIUMBLOB  NOT NULL,
    modifie_le DATETIME(3) NOT NULL,
    PRIMARY KEY (tenant_id, membre_id),
    CONSTRAINT fk_kernel_fond_accueil_membre FOREIGN KEY (membre_id) REFERENCES kernel_membre (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
