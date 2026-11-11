# Kauris — modules open source

Modules de gestion pour les PME de la zone UEMOA (paie, comptabilité SYSCOHADA, flotte…). Chaque module
fonctionne **seul**, avec un compte local, ou **dans Kauris Cloud**, avec un compte venach.

> Dépôt privé pendant la reconstruction. Il deviendra public (licence AGPL-3.0) quand le premier module
> sera complet (phase 5).

## Démarrer

Prérequis : Java 21, Docker (pour la recherche de secrets), `make`.

```bash
make help      # toutes les commandes
make verify    # compile et teste (identique à la CI)
make run       # lance le mode autonome sur http://localhost:8090
```

Environnement : `SPRING_PROFILES_ACTIVE=dev` (par défaut) ou `prod`.

## Organisation

| Dossier | Rôle |
|---|---|
| `kauris-kernel` | Socle commun : registre des modules (puis organisation, membres, droits, connexion) |
| `kauris-standalone` | Lanceur autonome des modules |
| `docs/` | Règles du projet |

Les règles de travail (procédure par module, conventions, sécurité) sont dans [`docs/regles.md`](docs/regles.md).
