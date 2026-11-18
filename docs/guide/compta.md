# Comptabilité

Le module Comptabilité tient la comptabilité de l'entreprise selon le **SYSCOHADA révisé** (Acte uniforme du
26 janvier 2017), en **système normal** ou en **système minimal de trésorerie** : plan comptable, journaux, écritures,
lettrage, grand livre, balance, bilan, compte de résultat, TVA et clôture d'exercice.

Menu : **Comptabilité**. Onglets : **Accueil**, **Écritures**, **Grand livre**, **Balance**, **Lettrage**,
**États financiers**, **TVA**, **Exercices**, **Plan comptable**, **Paramètres** (et **À revoir** quand une opération
attend).

## Ce qui arrive tout seul

Les autres modules envoient leurs opérations à la comptabilité, au moment où elles se font :

| Module | Opération | Écriture (comptes par défaut) |
|---|---|---|
| Ventes | facture émise | D 411 client (TTC) / C 701 ventes de marchandises, 706 services, C 4431 TVA collectée |
| Ventes | avoir émis | l'inverse de la facture, même pièce que la facture d'origine |
| Ventes, caisse | règlement encaissé | D 571 caisse, 552 Mobile Money, 513 chèque ou 521 banque / C 411 client |
| Caisse | clôture avec écart | manquant : D 6581 / C 571 ; excédent : D 571 / C 7581 |
| Achats | facture fournisseur | D 601 (si elle suit une commande) ou 605 / D 4452 TVA déductible / C 401 fournisseur |
| Achats | paiement fournisseur | D 401 / C trésorerie |
| Paie | mois clôturé | une pièce par bulletin, avec les comptes des règles de paie (661, 663, 664, 641 / 431, 447, 421, 422) |
| Paie | salaires payés | D 422 net à payer / C trésorerie selon le mode de paiement du salarié |
| Flotte | versement encaissé | D trésorerie / C 706 services vendus |

Une annulation (règlement annulé, facture fournisseur annulée, mois de paie rouvert, versement annulé) supprime
l'écriture si elle est encore au brouillard, sinon passe l'écriture inverse (contre-passation). Une opération n'est
jamais reçue deux fois.

**Imputations.** Ces comptes ne sont que des propositions : dans **Paramètres → Imputations**, choisissez le compte de
chaque rôle (clients, ventes de marchandises, TVA…), et, pour la trésorerie, un compte par mode de paiement, ou par
compte de dépôt dans Kauris Cloud (une ligne par banque, par exemple 5211 Ecobank). Les ventes de la flotte peuvent
aller sur leur propre compte (précision « FLOTTE »).

**À revoir.** Une opération qui ne peut pas s'écrire (exercice clos, compte désactivé, imputation manquante) ne bloque
jamais la vente ou la paie : elle attend dans l'onglet **À revoir**, avec la raison. Corrigez la cause, puis
**Réessayer** ; ou **Écarter** si elle ne doit pas être comptabilisée.

## Le brouillard, la validation, la contre-passation

Par défaut, les écritures reçues arrivent **au brouillard** : vous pouvez les vérifier, changer un compte, puis les
**valider** (une à une, ou tout le brouillard jusqu'à une date). Dans **Paramètres**, vous pouvez choisir de les valider
tout de suite.

Une écriture **validée** reçoit son numéro (VE-2026-00012), sans trou, par journal et par année, et ne change plus.
Pour corriger une erreur : **contre-passer** (Kauris passe l'écriture inverse, validée), puis saisir l'écriture juste.
Une écriture au brouillard se corrige ou se supprime.

## Saisir une écriture

**Écritures → Saisir une écriture** : journal (ventes, achats, trésorerie, paie, opérations diverses, ou les vôtres),
date, pièce, libellé, puis les lignes : compte (choisi dans la liste du plan), tiers si besoin, libellé, débit ou crédit.
Le total des débits doit égaler celui des crédits. Un tiers saisi par son nom reprend le même tiers que les écritures
reçues (un client des ventes, par exemple), pour le lettrage.

À saisir vous-même : frais et agios bancaires, emprunts, apports, immobilisations et amortissements, variation de
stock de fin d'année, provisions, charges à payer, affectation du résultat.

**Recette ou dépense** : une saisie simple pour le **système minimal de trésorerie** (et pour tout mouvement simple) :
l'argent entre ou sort, par la caisse, la banque ou Mobile Money, pour telle nature (vente, loyer, carburant…).

## Lettrage, grand livre, balance

- **Lettrage** : sur un compte de tiers (clients, fournisseurs, personnel…), rapprocher les lignes qui se compensent.
  Le **lettrage automatique** rapproche une facture et ses règlements (même pièce), puis tout un tiers soldé. Ce qui
  reste non lettré est encore dû. Seules les écritures validées se lettrent.
- **Grand livre** : les mouvements d'un compte sur une période, avec le report et le solde progressif ; filtre par
  tiers. Tout le grand livre se télécharge en Excel, CSV ou PDF.
- **Balance** : générale (tous les comptes) ou par tiers (un compte de tiers), brouillard compris ou non.

## États financiers

**Bilan** et **compte de résultat** selon le modèle SYSCOHADA révisé de votre système, avec l'exercice précédent :

- **Système normal** : compte de résultat de TA à XI (marge commerciale, valeur ajoutée, excédent brut
  d'exploitation, résultats d'exploitation, financier, HAO, résultat net) ; bilan de AD à DZ (brut, amortissements,
  net à l'actif). Les comptes de tiers et de trésorerie vont à l'actif ou au passif selon le sens de leur solde.
- **Système minimal de trésorerie** : recettes et dépenses par nature, résultat, et bilan simplifié.

Tant qu'il reste des écritures au brouillard, les états sont marqués **provisoires**. Ils se téléchargent en PDF, Excel
ou CSV. Vérifiez-les avec votre expert-comptable avant le dépôt de la liasse.

## TVA

**TVA → mois** : chiffre d'affaires hors taxes, TVA collectée (443), TVA déductible (445), crédit du mois précédent
(4449), **TVA due** ou **crédit à reporter**. Reportez ces montants sur votre déclaration. Puis **Préparer l'écriture de
liquidation** : elle solde les comptes de TVA du mois vers 4441 (TVA due) ou 4449 (crédit). Le paiement de la TVA se
saisit ensuite en trésorerie.

## Exercices et clôture

L'exercice est l'année civile ; il s'ouvre à la première écriture. Pour **clôturer** : l'année est finie, toutes ses
écritures sont validées, ses opérations à revoir traitées, l'exercice précédent clos. Kauris passe alors les
**à-nouveaux** du 1er janvier suivant : les soldes de bilan (avec le détail par tiers) et le résultat en « résultat en
instance d'affectation » (1301 bénéfice, 1309 perte). Plus aucune écriture n'entre dans l'exercice clos.

Une erreur découverte après ? **Rouvrez** l'exercice (si le suivant n'est pas clos), corrigez, clôturez de nouveau :
les à-nouveaux sont refaits.

## Plan comptable et journaux

Le plan SYSCOHADA révisé est en place dès le premier usage. Ajoutez vos sous-comptes (une banque par compte 521…),
corrigez un libellé, rendez un compte lettrable, désactivez un compte que vous n'utilisez plus. Un compte utilisé par
un autre module (la paie a ses comptes) est créé tout seul s'il manque, avec le libellé de son compte parent.

Journaux fournis : VE ventes, AC achats, TR trésorerie, PA paie, OD opérations diverses, AN à-nouveaux. Vous pouvez
en ajouter (un par banque, par exemple) et les renommer.

## Système normal ou minimal

Dans **Paramètres** :

- **Système normal** : pour les PME, comptabilité d'engagement complète.
- **Système minimal de trésorerie** : pour les très petites entreprises dont le chiffre d'affaires reste sous les seuils
  du SYSCOHADA (60 millions pour le négoce, 40 pour l'artisanat, 30 pour les services) : saisie en recettes et
  dépenses, états simplifiés.

Le choix change la présentation des états et l'accueil ; les écritures restent les mêmes. Vous pouvez changer de
système, par exemple en début d'exercice.

## Droits

Les quatre permissions sont réservées au propriétaire et à l'administrateur, tant qu'ils ne les cochent pas dans un
rôle (comptable) : consulter ; saisir et lettrer ; valider, contre-passer, liquider la TVA et clôturer ; paramétrer.

## Limites

- Le stock est en inventaire intermittent : la variation de stock se passe à la clôture, en opération diverse.
- Pas encore d'immobilisations avec plan d'amortissement automatique, ni de rapprochement bancaire avec relevé importé,
  ni de liasse fiscale au format DGI : les états se téléchargent pour être repris.
