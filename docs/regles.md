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
   `V20261111_22`, les ventes à `V20261112_40`, le stock à `V20261112_41`, les achats à `V20261112_42`).
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
sociaux…), ses monnaies, son compte bancaire, son logo et le **thème de ses documents** (modèle Moderne,
Classique ou Élégant, ou personnalisé : polices des titres et du texte, taille, trois couleurs, place du logo,
style du titre). Un module lit l'entreprise avec `GererEntrepriseUseCase.consulter(tenantId)`.

### Ossature commune des documents

Tous les documents ont la même ossature (`templates/kauris/document.html`) :

1. en-tête de l'entreprise : logo, raison sociale, adresse, NIF, RCCM, téléphone, email, site, filet épais ;
2. titre ;
3. contenu propre au document, avec les briques communes : cartouche (`kauris/document :: cartouche`),
   tableaux `k-doc-tableau` (lignes `k-doc-groupe`, `k-doc-total`, cellules `k-doc-montant`), signatures
   (`kauris/document :: signatures`) ;
4. pied légal : raison sociale • capital • NIF • RCCM, coordonnées bancaires, mentions ;
5. bas de chaque page : « ENTREPRISE — TITRE précision — édité le … » et « Page x / y ».

Un module prépare l'habillage avec `ProducteurDocuments`, puis renvoie sa vue à l'écran, ou le PDF :

```java
Habillage h = documents.habillage(tenantId, "Relevé des mouvements", "du 01/10 au 31/10/2026",
        "/tresorerie/releve.pdf?…", false);                       // à l'écran
model.addAttribute("habillage", h);
…
byte[] pdf = documents.pdf("tresorerie/releve", variables);    // variables.habillage préparé avec pdf = true
return EntrepriseController.enPdf(pdf, ProducteurDocuments.nomDeFichier("Relevé", "202610"));
```

```html
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{kauris/document :: page(contenu=~{::section})}">
<body><section> … </section></body>
</html>
```

- Pas d'adresse `@{…}` dans une vue de document : elle sert aussi au PDF, produit hors d'une requête.
- Mise en page en tableaux (le moteur PDF lit le CSS 2.1 : ni flex ni variables CSS).
- Les montants s'écrivent avec `entreprise.devise().formater(montant)` (entier) ou `formaterDecimal(montant)`
  (`BigDecimal`) : deux noms distincts, car Thymeleaf ne choisit pas entre deux méthodes du même nom.
- Un montant en toutes lettres : `MontantEnLettres.montant(montant, devise)` (« un million de francs CFA »).
- Documents commerciaux : deux colonnes `k-doc-colonnes` (cellule `k-doc-intervalle` entre les deux), bloc du
  destinataire `k-doc-destinataire` (nom en `k-doc-nom-tiers`), totaux à droite `k-doc-totaux`, remarques
  `k-doc-note`, bandeau `k-doc-brouillon` pour un document sans valeur.

### Polices des documents

Le réglage `kauris.documents.polices` (variable `KAURIS_POLICES`) donne un dossier de fichiers TrueType nommés
« Famille-Style.ttf » (`Poppins-SemiBoldItalic.ttf`, `Lato-Regular.ttf`). Chaque famille devient un choix de
l'éditeur de thème ; le PDF les intègre, le navigateur les charge depuis `/kauris/polices/`. Sans dossier, ou
pour une famille absente, les documents prennent Helvetica, Times ou Courier : rien n'échoue. Les polices
proposées par les modèles : Inter (Moderne), Merriweather et Lato (Classique), Poppins (Élégant).

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

## Ventes, achats et stock

Le jar `kauris-ventes` porte deux modules qui partagent le catalogue d'articles : « Ventes » (`ventes`) et
« Achats et stock » (`achats-stock`, paquet `…ventes.stock`, tables `stock_…`, migrations `db/migration/stock`).
Une facture émise fait sortir du dépôt principal les articles suivis ; un avoir les y fait revenir. La valeur
du stock est au coût moyen unitaire pondéré. Les achats (paquet `…ventes.achats`, tables `achats_…`) : une
réception de commande fait entrer les articles suivis au prix de la commande ; les règlements des fournisseurs
passent par le port `PortsAchats.Decaissements` (Kauris Cloud y branche sa trésorerie).

## Ventes : ce que l'application peut y brancher

Le module Ventes déclare un port `Ports.Encaissements` : les comptes où déposer un règlement, d'autres façons
d'encaisser une facture (boutons sur la facture) et deux appels, dans la transaction, quand un règlement est
enregistré ou annulé. Le mode autonome n'y branche rien ; Kauris Cloud y branche sa trésorerie (demande de
paiement PI-SPI, report des règlements). Un paiement constaté par l'application elle-même passe par
`VentesService.encaisserAutomatiquement`, ramené au reste à payer.

## Paiements

Les modules open source n'appellent aucun prestataire de paiement. Ils exposent une interface de
versement avec une implémentation manuelle (export d'un fichier de virement). Le connecteur PI-SPI
appartient à Kauris Cloud et n'est jamais publié.
