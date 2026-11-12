-- Le compte de trésorerie où un règlement est arrivé (Kauris Cloud) ; vide en mode autonome.
ALTER TABLE ventes_reglement ADD COLUMN compte_id VARCHAR(36) NULL AFTER reference;
