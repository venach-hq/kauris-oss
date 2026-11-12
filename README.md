# Kauris — modules open source

Modules de gestion pour les PME de la zone UEMOA (paie, comptabilité SYSCOHADA, flotte…). Chaque module
fonctionne **seul**, avec un compte local, ou **dans Kauris Cloud**, avec un compte venach.

> Dépôt privé pendant la reconstruction. Il deviendra public (licence AGPL-3.0) quand le premier module
> sera complet (phase 5).

## Démarrer

Prérequis : Java 21, Docker (base de développement, tests sur MySQL, recherche de secrets), `make`.

```bash
make help      # toutes les commandes
make verify    # compile et teste, y compris sur un MySQL jetable (identique à la CI)
make dev-up    # MySQL local sur le port 3308
make run       # lance le mode autonome sur http://localhost:8090
```

Au premier démarrage, Kauris ouvre l'écran d'installation : on y crée l'organisation, son premier
établissement et le compte du propriétaire (email + mot de passe). Cet écran se ferme ensuite pour toujours.

Environnement : `SPRING_PROFILES_ACTIVE=dev` (par défaut) ou `prod`. En production, `DB_URL`, `DB_USERNAME`
et `DB_PASSWORD` sont obligatoires (MySQL 8).

## Connexion

| Mode | Où | Comment |
|---|---|---|
| `local` | ce dépôt (`kauris-standalone`) | email + mot de passe ; seule l'empreinte BCrypt est gardée |
| `venach` | Kauris Cloud | compte account.venach.com (OpenID Connect) |

Dans les deux cas, le noyau ne retient de la personne qu'une identité (fournisseur + identifiant stable).
Ses droits viennent de ses rôles de membre : propriétaire, administrateur, gestionnaire ou lecteur, pour
toute l'organisation ou pour un établissement.

## Organisation

| Dossier | Rôle |
|---|---|
| `kauris-kernel` | Socle commun : registre des modules, organisation → établissements → membres → rôles et permissions, paramètres de l'entreprise (identité légale UEMOA, monnaies, compte bancaire, logo, thème des documents), ossature commune des documents et PDF, comptes locaux, interface commune, notifications |
| `kauris-ventes` | Clients, catalogue (articles et prestations), devis, factures et avoirs numérotés par année, TVA par taux, règlements, impayés, relevé de compte client ; documents à l'écran et en PDF |
| `kauris-standalone` | Lanceur autonome des modules, avec la connexion « local » |
| `docs/` | Règles du projet |

Les règles de travail (procédure par module, conventions, sécurité) sont dans [`docs/regles.md`](docs/regles.md).
