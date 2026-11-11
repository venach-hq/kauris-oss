# Consignes pour Claude

- Langue : français, simple et direct.
- Lire `docs/regles.md` avant de toucher au code et la suivre (procédure par module, Git, configuration).
- Commandes : uniquement via le `Makefile` (`make verify` avant tout commit).
- Jamais de secret dans le code, les tests ou la configuration ; jamais de connecteur de paiement dans ce dépôt.
- Les modules ne s'importent pas entre eux : seulement `kauris-kernel` et des événements.
- Travail par branche et pull request ; `main` est protégée.
