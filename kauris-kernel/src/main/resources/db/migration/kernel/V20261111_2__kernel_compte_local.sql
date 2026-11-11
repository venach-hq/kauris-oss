-- Mode de connexion « local » (Kauris installé seul) : email + empreinte du mot de passe.
-- Dans Kauris Cloud la table existe mais reste vide : la connexion y passe par account.venach.com.

CREATE TABLE kernel_compte_local (
    id                     VARCHAR(36)  NOT NULL,
    tenant_id              VARCHAR(36)  NOT NULL,
    email                  VARCHAR(190) NOT NULL,
    empreinte_mot_de_passe VARCHAR(100) NOT NULL,
    cree_le                DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_kernel_compte_local_email UNIQUE (email),
    CONSTRAINT fk_kernel_compte_local_organisation FOREIGN KEY (tenant_id) REFERENCES kernel_organisation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
