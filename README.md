# Kauris

**Gestion des PME de la zone UEMOA** : ventes, caisse, stock et achats, avec des documents conformes (identité
légale, TVA, numérotation sans trou) et pensés pour le franc CFA. Logiciel libre sous licence
[AGPL-3.0](LICENSE), édité par Venach.

- **Ventes** : clients, catalogue, devis → factures → avoirs numérotés par année, TVA par taux, règlements,
  impayés, relevé de compte, envoi et relance par email.
- **Caisse** : vente au comptoir, monnaie à rendre, espèces / Mobile Money / carte, ticket 80 mm, clôture avec
  écart et rapport, calculatrice (TVA, marge, remise).
- **Stock et achats** : dépôts, entrées, sorties, transferts, inventaires, coût moyen pondéré ; fournisseurs,
  bons de commande, réceptions, factures et paiements fournisseurs.
- **Socle** : organisation, établissements, membres, rôles et permissions, paramètres de l'entreprise (Bénin,
  Burkina Faso, Côte d'Ivoire, Guinée-Bissau, Mali, Niger, Sénégal, Togo), documents PDF au thème de l'entreprise.

Kauris s'installe chez vous (ce dépôt) ou s'utilise hébergé : [Kauris Cloud](https://kauris.venach.com) ajoute
la trésorerie, les paiements instantanés PI-SPI et l'hébergement.

## Installer

```bash
curl -LO https://github.com/venach-hq/kauris-oss/releases/latest/download/docker-compose.yml
curl -L -o .env https://github.com/venach-hq/kauris-oss/releases/latest/download/kauris.env.example
# remplir les deux mots de passe de .env, puis
docker compose up -d        # http://localhost:8090
```

Tout est dans le **[guide d'utilisation](docs/guide/README.md)** : [installation](docs/guide/installation.md),
mise à jour, sauvegarde, [prise en main](docs/guide/prise-en-main.md), [ventes](docs/guide/ventes.md),
[caisse](docs/guide/caisse.md), [stock et achats](docs/guide/stock-achats.md).

## Développer

Prérequis : Java 21, Docker (base de développement, tests sur MySQL, recherche de secrets), `make` ; node pour les
tests des scripts des écrans.

```bash
make help      # toutes les commandes
make verify    # compile et teste, y compris sur un MySQL jetable (identique à la CI)
make dev-up    # MySQL local sur le port 3308
make run       # lance le mode autonome sur http://localhost:8090
make image     # construit l'image Docker
```

| Dossier | Rôle |
|---|---|
| `kauris-kernel` | Socle commun : registre des modules, organisation, établissements, membres, rôles et permissions, paramètres de l'entreprise, ossature des documents et PDF, comptes locaux, interface commune, notifications |
| `kauris-ventes` | Ventes, caisse, stock et achats (ils partagent le catalogue) |
| `kauris-standalone` | Le lanceur autonome, avec la connexion par email et mot de passe |
| `deploy/` | Installation par `docker compose` |
| `docs/guide/` | Guide d'utilisation |
| `docs/regles.md` | Règles du projet : architecture, conventions, sécurité |

Chaque module fonctionne seul ou dans Kauris Cloud ; un module ne dépend que du socle (`kauris-kernel`). Les
modules open source n'appellent aucun prestataire de paiement.

## Contribuer

Les contributions sont les bienvenues : lisez [CONTRIBUTING.md](CONTRIBUTING.md) et le
[code de conduite](CODE_OF_CONDUCT.md). Une faille de sécurité se signale en privé : [SECURITY.md](SECURITY.md).

## Licence

Copyright © 2026 Venach. Kauris est distribué sous la [GNU Affero General Public License v3.0](LICENSE)
(AGPL-3.0-only) : vous pouvez l'utiliser, l'étudier, le modifier et le redistribuer ; si vous mettez une version
modifiée à disposition d'autres personnes par le réseau, vous devez leur offrir son code source (variable
`KAURIS_CODE_SOURCE`, lien « Code source » en bas de chaque page).
