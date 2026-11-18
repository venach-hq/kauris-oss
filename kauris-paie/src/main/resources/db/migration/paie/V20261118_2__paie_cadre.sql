-- Statut cadre du salarié : certaines cotisations ne concernent que les cadres
-- (retraite complémentaire des cadres de l'IPRES au Sénégal).
ALTER TABLE paie_salarie ADD COLUMN cadre BOOLEAN NOT NULL DEFAULT FALSE;
