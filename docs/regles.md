# Règles du projet

## Indépendance

GitHub est un outil de travail, pas l'endroit où vit le code.

- Toute commande (compiler, tester, chercher des secrets, construire) est une cible du `Makefile`.
  La CI ne fait qu'appeler `make` : elle peut tourner ailleurs sans réécriture.
- La documentation et les décisions vivent dans `docs/`, pas dans un wiki ni dans les tickets.
- Aucun secret dans GitHub seul : la source est le gestionnaire de mots de passe et le fichier
  d'environnement du serveur.

## Procédure pour chaque module

Un module n'est « fini » que lorsque tout ceci est fait :

1. Inventaire de l'ancien code : ce qu'on garde, ce qu'on corrige, ce qu'on jette.
2. Domaine et ports (architecture hexagonale) : la logique métier ne dépend pas de Spring.
3. Reprise avec corrections :
   - identifiants UUID, `tenant_id` sur toutes les tables ;
   - aucun import vers l'intérieur d'un autre module (seulement `kauris-kernel` et des événements) ;
   - aucun secret dans le code ou la configuration.
4. Migrations Flyway du module (`db/migration/<module>/`), jamais `ddl-auto=update`.
   Nommage : `V<AAAAMMJJ>_<n>__<module>_<sujet>.sql` ; tables préfixées par le module (`kernel_…`).
   Une migration publiée ne se modifie jamais.
5. Tests : domaine (unitaires), services, intégration base (MySQL jetable lancé par Testcontainers).
6. Écrans sur l'interface commune.
7. CI au vert (`make verify`, `make secrets`).
8. Déploiement.

Chaque module déclare un bean `ModuleKauris` avec un code stable (`rh-paie-tg`, `compta-syscohada`…).

## Interface commune

Tous les écrans utilisent le gabarit du noyau (`templates/kauris/layout.html`) et sa feuille de style
(`/kauris/kauris.css`, classes `k-…`), sans dépendance externe :

```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{kauris/layout :: page(titre='Mon écran', contenu=~{::section})}">
<body><section> … </section></body>
</html>
```

- Le modèle contient toujours `kauris` : personne connectée, organisation courante (`kauris.acces()`), menu.
- Un contrôleur obtient l'organisation courante avec `ContexteCourant.exiger(requete)` et passe son
  `tenantId` à tous les appels : aucun identifiant d'organisation ne vient d'un formulaire ou d'une adresse.
- Un module ajoute ses entrées au menu avec `ModuleKauris.menu()`.
- Après une action : redirection, avec le message dans `succes` ou `erreur` (attributs flash). Les droits
  sont contrôlés par les services, pas par l'écran : cacher un bouton ne protège rien.
- L'application dit qui est connecté en fournissant un bean `SessionKauris`.

## Git

- `main` est protégée : on y arrive par une pull request dont la CI est verte.
- Une branche par sujet : `feat/…`, `fix/…`, `docs/…`.
- Messages : `feat: …`, `fix: …`, `docs: …`, `refactor: …`, `test: …`, `chore: …`.

## Configuration

| Fichier | Rôle |
|---|---|
| `application.yml` | Réglages communs, sans secret |
| `application-dev.yml` | Poste de développement |
| `application-prod.yml` | Production |

Les secrets sont lus dans les variables d'environnement. Une variable manquante en production doit
empêcher le démarrage, jamais être remplacée par une valeur par défaut.

## Paiements

Les modules open source n'appellent aucun prestataire de paiement. Ils exposent une interface de
versement avec une implémentation manuelle (export d'un fichier de virement). Le connecteur PI-SPI
appartient à Kauris Cloud et n'est jamais publié.
