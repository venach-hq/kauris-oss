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
   Une migration publiée ne se modifie jamais. Une nouvelle migration porte un numéro **plus grand** que
   toutes celles déjà appliquées en production, sinon Flyway refuse de démarrer (le noyau en est à
   `V20261111_21`).
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

## Permissions

Chaque module déclare ses permissions avec `ModuleKauris.permissions()`, en trois niveaux :

| Niveau | Donné par les rôles de base | Exemple |
|---|---|---|
| `CONSULTER` | Lecteur, Gestionnaire | `paie.bulletins.consulter` |
| `MODIFIER` | Gestionnaire | `paie.bulletins.modifier` |
| `ADMINISTRER` | Propriétaire et administrateur seulement | `paie.parametres.modifier` |

Le propriétaire crée en plus des rôles pour les postes de son entreprise (comptable, caissier, RH…) en cochant
ces permissions ; un rôle se donne pour toute l'organisation ou pour certains établissements. Le propriétaire
et l'administrateur ont toujours toutes les permissions.

- Un écran demande la sienne avec `ContexteCourant.exiger(requete, "code.permission")` (refus : page 403).
- Un service la vérifie avec `acces.peut("code")` ou `acces.peut("code", etablissementId)`.
- Une entrée de menu porte sa permission (`new EntreeMenu(libelle, chemin, permission)`) : elle n'apparaît
  qu'à qui l'a.
- Un code de permission ne change plus une fois publié : les rôles personnalisés l'enregistrent.

## Paramètres de l'entreprise et documents

Le noyau garde l'identité légale de l'entreprise selon son pays (RCCM, NIF / IFU / NCC / NINEA, numéros
sociaux…), ses monnaies, son logo et le thème de ses documents. Un module les lit avec
`GererEntrepriseUseCase.consulter(tenantId)` et habille ses documents avec le gabarit commun :

```html
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{kauris/document :: page(titre='Facture F-001', entreprise=${entreprise}, contenu=~{::section})}">
<body><section> … </section></body>
</html>
```

En-tête (logo, identité, identifiants), police, taille et couleurs du thème, et pied de page avec la mention
légale OHADA (`entreprise.mentionLegale()`) sont alors les mêmes partout. Les montants s'écrivent avec
`entreprise.devise().formater(montant)`.

## Notifications

Pour prévenir un membre, un service appelle `NotifierUseCase` **dans la transaction** de son changement :
la notification et son avis (boîte d'envoi, table `kernel_boite_envoi`) sont enregistrés avec lui, ou pas
du tout. Un relais fait ensuite partir les avis vers les écrans ouverts (WebSocket `/ws/notifications`).

| Réglage | Valeur |
|---|---|
| `kauris.notifications.transport` | `direct` (défaut, une seule instance) ou `rabbitmq` (échange `kauris.notifications`) |
| `kauris.notifications.relais.intervalle` | `2s` par défaut |
| `kauris.notifications.relais.actif` | `true` par défaut ; `false` dans les tests qui passent le relais à la main |

La base fait foi : la liste et le compteur la relisent toujours. Un avis peut arriver deux fois ou se
perdre sans que rien ne manque. Le lien d'une notification est toujours un chemin de l'application.

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
