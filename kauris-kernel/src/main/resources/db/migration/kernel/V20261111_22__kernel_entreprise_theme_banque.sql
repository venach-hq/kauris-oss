-- Thème des documents : trois modèles proposés (Moderne, Classique, Élégant) ou personnalisé, avec la police
-- des titres et celle du texte (familles du dossier de polices du serveur), la couleur des en-têtes de tableau,
-- la place du logo et le style du titre. Et le compte bancaire imprimé au pied des documents.
ALTER TABLE kernel_entreprise
    RENAME COLUMN police TO police_texte;

ALTER TABLE kernel_entreprise
    MODIFY COLUMN police_texte VARCHAR(40) NOT NULL,
    ADD COLUMN theme_modele     VARCHAR(20)  NOT NULL DEFAULT 'MODERNE',
    ADD COLUMN police_titres    VARCHAR(40)  NOT NULL DEFAULT 'Inter',
    ADD COLUMN couleur_entetes  VARCHAR(7)   NOT NULL DEFAULT '#1E293B',
    ADD COLUMN position_logo    VARCHAR(10)  NOT NULL DEFAULT 'GAUCHE',
    ADD COLUMN style_titre      VARCHAR(12)  NOT NULL DEFAULT 'MAJUSCULES',
    ADD COLUMN banque           VARCHAR(80)  NOT NULL DEFAULT '',
    ADD COLUMN banque_titulaire VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN banque_compte    VARCHAR(60)  NOT NULL DEFAULT '',
    ADD COLUMN banque_bic       VARCHAR(20)  NOT NULL DEFAULT '',
    ADD COLUMN banque_agence    VARCHAR(120) NOT NULL DEFAULT '';

-- Les thèmes enregistrés avant gardent leurs couleurs et leur taille ; leurs polices (noms d'une ancienne
-- liste) deviennent la famille la plus proche.
UPDATE kernel_entreprise
SET theme_modele  = 'PERSONNALISE',
    police_texte  = CASE police_texte
                        WHEN 'SERIF' THEN 'Times'
                        WHEN 'GEORGIA' THEN 'Merriweather'
                        WHEN 'GARAMOND' THEN 'Merriweather'
                        WHEN 'MONO' THEN 'Courier'
                        ELSE 'Inter' END,
    police_titres = police_texte,
    couleur_entetes = couleur_principale;
