# Flotte

Le module Flotte sert aux entreprises qui confient des véhicules à des conducteurs : motos (zémidjans, okadas),
taxis, tricycles, minibus, bus, camions, engins. Il tient les véhicules, les conducteurs, les contrats, les
versements journaliers, les retards, les jours fériés et les papiers.

Menu : **Flotte**.

## L'idée : un compte par conducteur

Vous ne saisissez rien jour par jour. Chaque conducteur a un **compte**, calculé par Kauris à partir de son contrat :

- chaque jour de travail, le versement du jour s'ajoute à ce qu'il doit ;
- les jours de repos, les jours fériés et les pauses (panne, maladie) ne comptent pas ;
- une avance, une amende ou un dommage s'ajoutent ; une remise se retranche ;
- chaque versement, **de n'importe quel montant**, paie d'abord le jour le plus ancien. Un versement d'avance
  couvre les jours suivants.

Le jour même n'est jamais « en retard » : beaucoup de conducteurs versent le soir.

Kauris en tire tout seul : **payé jusqu'au** (dernier jour entièrement payé), les **jours de retard** et leur
montant, ce qu'il faut **encaisser aujourd'hui** (retard + jour), l'avance éventuelle et, en location-vente, ce
qui a été versé sur le prix du véhicule, ce qui reste et la date de fin estimée.

Tout se corrige après coup (jour férié oublié, mauvais montant, panne déclarée en retard) : le compte se recalcule.

## Mise en place (une fois)

1. **Véhicules** → *Ajouter un véhicule* : type, immatriculation, marque… Dans *Paiement des versements*, notez où
   le conducteur paie (numéro Mobile Money de l'entreprise, par exemple) : c'est écrit dans les messages au
   conducteur.
2. **Conducteurs** → *Ajouter un conducteur* : nom, téléphone (pour WhatsApp et SMS), permis et sa date de fin,
   garant.
3. **Contrats** → *Nouveau contrat* : le véhicule, le conducteur, le type, le premier jour, le versement par jour
   et les jours de repos.
   - *Location* : versements sans fin prévue.
   - *Location-vente* : versements jusqu'au total ; une fois payé, le bouton *Céder le véhicule au conducteur*
     termine le contrat.
   - *Salarié* : pas de versement (le conducteur est payé par vous).

Un véhicule et un conducteur n'ont qu'un contrat en cours à la fois. Un conducteur dont le permis a expiré ne
reçoit pas de nouveau contrat.

## La caisse du jour (pour la caissière)

**Flotte** ouvre la caisse du jour : un conducteur par carte, les retards en rouge en premier, avec le montant à
encaisser en grand. En haut : ce qui a été encaissé aujourd'hui, ce qui reste à encaisser et le nombre de
conducteurs en retard. Tapez une immatriculation ou un nom dans la recherche pour trouver vite.

### Encaisser

1. Appuyez sur **Encaisser** sur la carte du conducteur.
2. Le montant dû est déjà proposé ; tapez ce que vous recevez, ou appuyez sur *1 jour*, *1 semaine*,
   *Tout ce qui est dû*. Kauris vous dit ce qui restera, ou l'avance.
3. Choisissez comment il a payé : **Espèces**, **Mobile Money** (tapez la référence du SMS), virement…
4. **Encaisser et imprimer le reçu**.

Le reçu se lit sur une imprimante de tickets de 80 mm, ou s'envoie au conducteur par WhatsApp. Un versement
saisi par erreur s'annule depuis son reçu (permission *gérer*) ; il reste visible, barré.

Un versement d'un autre jour (oubli de la veille) : ouvrez *Versement d'un autre jour ?* et choisissez la date.

### Demander le paiement à un conducteur en retard

Sur sa carte : **Demander le paiement**. Kauris prépare le message (retard, montant à verser, où payer) ; vous
pouvez le modifier, puis **Envoyer par WhatsApp**, **par SMS**, ou le copier.

Dans Kauris Cloud, la demande part directement sur le téléphone du conducteur (PI-SPI) ; il accepte et paie.

### Jours fériés

**Jours fériés** → choisissez la date et le nom (Tabaski, fête de l'indépendance…) → *Déclarer férié*. Ce jour
n'est dû par aucun conducteur, même déclaré après coup. *Retirer* le rend dû à nouveau.

Pour un seul conducteur (panne, maladie, véhicule au garage), mettez plutôt une **pause** sur son contrat.

## Le contrat et le compte du conducteur

Cliquez l'immatriculation : vous voyez l'état, *payé jusqu'au*, le retard, l'avance, le calendrier des six
dernières semaines (vert : payé, orange : en partie, rouge : impayé, violet : férié, jaune : pause), le relevé du
compte (dû, payé, solde après chaque ligne) et les versements.

*Gérer le contrat* (permission *gérer*) :

| Pour | Faites |
|---|---|
| Changer le montant par jour | *Changer le versement par jour* : à partir d'une date ; les jours d'avant gardent l'ancien montant |
| Changer les jours de repos | *Jours de repos* |
| Panne, maladie, véhicule au garage | *Mettre en pause* : du … au … (vide si on ne sait pas). Cochez *à la charge du conducteur* si les versements restent dus |
| Avance d'argent, amende, dommage, remise | *Avance, amende, dommage ou remise* |
| Erreur sur le premier jour ou le prix total | *Corriger le contrat* |
| Fin du contrat | *Terminer ou résilier* : ce que le conducteur doit encore reste dans son compte |

## Papiers

Sur la fiche du véhicule ou dans **Papiers** : assurance, visite technique, carte grise, taxes, autorisations,
avec leur date de fin. Ce qui expire dans les 30 jours (et les permis bientôt expirés) s'affiche sur la caisse du
jour, sous *À surveiller*. Au renouvellement, ajoutez le nouveau papier : l'ancien reste dans l'historique.

## Exports

Chaque liste se télécharge en **Excel**, **CSV** (Excel français) ou **PDF** : situation des conducteurs, versements
d'une période, relevé d'un contrat, véhicules, conducteurs, papiers.

## Rôles

| Permission | Pour |
|---|---|
| Voir la flotte (`flotte.consulter`) | Lire les listes, les comptes, les reçus, télécharger |
| Encaisser (`flotte.encaisser`) | Encaisser, déclarer les jours fériés, demander un paiement |
| Gérer (`flotte.gerer`) | Véhicules, conducteurs, contrats, papiers, pauses, amendes, annuler un versement |

Pour une caissière, créez un rôle *Caissière flotte* avec *Voir* et *Encaisser* (**Organisation → Rôles**).
