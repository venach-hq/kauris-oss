-- Caisse (vente au comptoir) : une session par personne, du fond de caisse à la clôture, et ses tickets.
-- Un ticket est une facture des ventes émise et réglée ; la session garde comment elle a été payée.
CREATE TABLE caisse_session (
    id                VARCHAR(36)   NOT NULL,
    tenant_id         VARCHAR(36)   NOT NULL,
    numero            VARCHAR(20)   NOT NULL,
    membre_id         VARCHAR(36)   NOT NULL,
    caissier          VARCHAR(150)  NOT NULL,
    jour              DATE          NOT NULL,
    ouverte_le        DATETIME(6)   NOT NULL,
    fond              BIGINT        NOT NULL,
    compte_especes    VARCHAR(36)   NULL,
    compte_mobile     VARCHAR(36)   NULL,
    compte_carte      VARCHAR(36)   NULL,
    -- membre_id tant que la session est ouverte, NULL ensuite : une seule caisse ouverte par personne.
    ouverte_par       VARCHAR(36)   NULL,
    close_le          DATETIME(6)   NULL,
    close_par         VARCHAR(150)  NULL,
    especes_comptees  BIGINT        NULL,
    ecart             BIGINT        NULL,
    note              VARCHAR(300)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_caisse_session_numero (tenant_id, numero),
    UNIQUE KEY uk_caisse_session_ouverte (tenant_id, ouverte_par),
    INDEX ix_caisse_session_membre (tenant_id, membre_id, ouverte_le)
);

CREATE TABLE caisse_ticket (
    id                VARCHAR(36)   NOT NULL,
    tenant_id         VARCHAR(36)   NOT NULL,
    session_id        VARCHAR(36)   NOT NULL,
    document_id       VARCHAR(36)   NOT NULL,
    numero            VARCHAR(30)   NOT NULL,
    cree_le           DATETIME(6)   NOT NULL,
    total             BIGINT        NOT NULL,
    especes           BIGINT        NOT NULL,
    mobile            BIGINT        NOT NULL,
    carte             BIGINT        NOT NULL,
    recu              BIGINT        NOT NULL,
    rendu             BIGINT        NOT NULL,
    reference_mobile  VARCHAR(80)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_caisse_ticket_document (tenant_id, document_id),
    INDEX ix_caisse_ticket_session (tenant_id, session_id)
);

CREATE TABLE caisse_compteur (
    tenant_id  VARCHAR(36)  NOT NULL,
    annee      INT          NOT NULL,
    dernier    BIGINT       NOT NULL,
    PRIMARY KEY (tenant_id, annee)
);
