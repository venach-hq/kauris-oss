-- Les articles dont le stock est suivi (module Achats et stock), avec leur seuil d'alerte.
ALTER TABLE ventes_article
    ADD COLUMN suivi_stock  BOOLEAN       NOT NULL DEFAULT FALSE AFTER taux_tva,
    ADD COLUMN seuil_alerte DECIMAL(15,3) NULL AFTER suivi_stock;
