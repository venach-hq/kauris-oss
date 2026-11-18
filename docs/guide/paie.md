# Paie

Le module Paie prépare les bulletins de paie des entreprises de l'UEMOA : salariés, contrats, avances et prêts,
cotisations sociales, impôt sur les salaires, net à payer, livre de paie, déclarations, virement des salaires et
écritures comptables.

Menu : **Paie**. Trois onglets : **Mois de paie**, **Salariés**, **Règles de paie**.

## Le principe : Kauris recommande, vous décidez

Rien n'est figé dans Kauris. Tout ce qui fait un bulletin est dans les **règles de paie** de votre entreprise, que
vous pouvez lire et corriger :

- les **cotisations sociales** : organisme, taux salarié et employeur, plafond, assiette minimale (le SMIG), ou
  montant fixe par personne couverte (assurance maladie), déduction ou non de l'impôt, personnel concerné (local,
  expatrié, cadres, apprentis), compte comptable ;
- l'**impôt sur les salaires** : nom, barème (mensuel ou annuel) et ses tranches, déduction des cotisations, retraite
  déductible plafonnée, abattement et sa limite, charges de famille (déduction par personne à charge, réduction par
  demi-part, ou réduction en pourcentage de l'impôt avec un minimum et un maximum selon les parts ; nombre de parts
  selon la famille), plafond de l'impôt, arrondis, primes exceptionnelles imposées par différence, apprentis exonérés,
  frais forfaitaires des gérants ;
- les **impôts forfaitaires** retenus au salarié selon son revenu brut annuel (TRIMF), dus aussi, si besoin, pour le
  conjoint sans revenu ;
- les **taxes de l'employeur** sur les salaires (impôt patronal, apprentissage, formation…) ;
- les **heures supplémentaires** : heures d'un mois complet et majorations ;
- les **comptes** des écritures de paie.

Pour le **Togo**, la **Côte d'Ivoire** et le **Sénégal**, Kauris propose des **règles recommandées**, vérifiées en novembre 2026, avec
leurs notes et leurs sources. Vous les activez, puis vous corrigez ce qui est propre à votre entreprise : votre taux
d'accident du travail, votre convention collective, une loi de finances nouvelle. Dans l'éditeur, chaque valeur qui
s'écarte de la recommandation l'affiche en jaune juste en dessous ; les lignes recommandées que vous avez retirées
sont listées en haut.

Pour un **autre pays** de l'UEMOA, créez des règles « à compléter » et saisissez ses cotisations, son impôt et ses
taxes avec votre conseil. Le calcul suit les mêmes principes.

Faites vérifier vos premiers bulletins par votre comptable ou votre caisse sociale : vous restez responsable des
règles que vous appliquez.

### Quand une règle change

Une loi ou un taux change à partir d'un mois ? Ouvrez les règles → **Nouvelle version** à partir de ce mois, puis
corrigez la copie. Les mois précédents gardent l'ancienne version ; les bulletins déjà clos ne bougent jamais.

## Qui peut voir les salaires

Les salaires sont confidentiels. Les quatre permissions de la paie sont réservées au **propriétaire** et à
l'**administrateur** ; le gestionnaire et le lecteur n'y ont pas accès. Pour un comptable ou un responsable RH,
créez un rôle (**Organisation → Rôles**) et cochez ce qu'il faut :

| Permission | Ouvre |
|---|---|
| Voir la paie (`paie.consulter`) | Salariés, contrats, avances, bulletins, déclarations, règles (en lecture) |
| Préparer la paie (`paie.preparer`) | Fiches, contrats, avances, éléments du mois |
| Clôturer la paie (`paie.cloturer`) | Clôture et réouverture d'un mois, versement, salaires payés |
| Règles de paie (`paie.parametres.modifier`) | Activer, corriger, versionner les règles |

## Mise en place (une fois)

1. **Règles de paie** → activez les règles recommandées de votre pays (le pays vient de la fiche de l'entreprise),
   vérifiez-les et corrigez-les.
2. **Salariés** → *Ajouter un salarié* : identité, numéro d'assuré, situation de famille, enfants à charge, conjoint
   sans revenu, autres personnes à charge, **régime fiscal** (salarié, dirigeant, gérant, apprenti), expatrié ou non,
   cadre ou non (au Sénégal, la retraite complémentaire IPRES ne concerne que les cadres),
   mode de paiement (virement, Mobile Money, espèces, chèque) et compte. Le matricule est donné par Kauris.
3. Juste après, le **contrat** : type, poste, catégorie, date d'embauche, salaire de base et éléments versés chaque
   mois :
   - *prime ou indemnité soumise* (ancienneté, logement payé en argent, congés payés…) ;
   - *avantage en nature* (logement, véhicule, nourriture fournis) : cotisé et imposé, jamais payé en argent ;
   - *frais d'emploi* : versés, ni cotisés ni imposés, sauf pour un dirigeant ou un gérant ;
   - *indemnité non soumise* (remboursement de frais, indemnité exonérée).

## Chaque mois

1. **Mois de paie** → *Ouvrir la paie du mois*. Kauris calcule un bulletin pour chaque salarié sous contrat, avec les
   règles en vigueur ce mois-là.
2. Cliquez sur un salarié pour saisir ce qui change ce mois-ci : **jours d'absence** non payés (sur 30), **heures
   supplémentaires** (par majoration), **primes du mois**, **primes exceptionnelles** (13e mois, bonus : imposées par
   différence quand les règles le prévoient), **retenues** (syndicat, saisie). Le bulletin se recalcule aussitôt.
3. Vérifiez les bulletins, puis **Clôturer le mois** : ils reçoivent leur numéro (BP-202611-0001…) et sont figés. Les
   mois se clôturent dans l'ordre.
4. Téléchargez les documents du mois (Excel, CSV ou PDF) : **livre de paie**, **cotisations** par organisme, **impôt
   et taxes** sur les salaires, **ordre de virement** à remettre à la banque, **écritures comptables** (journal OD).
5. Une fois les salaires versés : *Marquer les salaires comme payés*.

Une erreur après la clôture ? *Rouvrir* (tant que les salaires ne sont pas marqués payés et qu'aucun mois suivant
n'est clos). À la nouvelle clôture, les bulletins reçoivent de nouveaux numéros ; un numéro n'est jamais réutilisé.

**Entrée ou sortie en cours de mois** : le salaire de base est payé au prorata des jours sous contrat, sur 30. Pour une
sortie, ouvrez le contrat → *Terminer le contrat* ; les indemnités de fin de contrat se saisissent comme primes du
dernier mois.

## Avances et prêts

Sur la fiche du salarié → *Accorder une avance ou un prêt* : montant, retenue par mois, premier mois. Chaque mois, la
retenue apparaît sur le bulletin, **sans jamais rendre le net négatif** : ce qui ne peut pas être retenu est reporté au
mois suivant. Le reste dû se lit sur la fiche ; *Arrêter* stoppe les retenues suivantes.

## Les règles recommandées

### Togo (2026)

| Cotisation | Salarié | Employeur |
|---|---|---|
| CNSS pension (déductible dans la limite de 6 % du brut) | 4 % | 12,5 % |
| CNSS prestations familiales | | 3 % |
| CNSS risques professionnels (selon votre activité) | | 2 % |
| AMU (partage à vérifier avec la CNSS) | 5 % | 5 % |

**IRPP**, barème annuel : (brut soumis − cotisations déductibles) × 12, moins l'abattement de 28 % (sur la part du
revenu jusqu'à 10 000 000), moins 10 000 F par mois et par personne à charge (six au plus) ; revenu arrondi au millier
inférieur ; barème 0 % jusqu'à 900 000, puis 3, 10, 15, 20, 25, 30 et 35 % au-delà de 20 000 000 ; impôt arrondi à la
dizaine inférieure, divisé par 12. Primes exceptionnelles imposées par différence ; apprentis exonérés ; gérants :
20 % de frais jusqu'à 10 000 000, sans autre déduction.

### Côte d'Ivoire

| Cotisation | Salarié | Employeur | Assiette |
|---|---|---|---|
| CNPS retraite | 6,3 % | 7,7 % | plafond 3 375 000, au moins le SMIG (75 000) |
| CNPS prestations familiales | | 5 % | plafond 70 000 |
| CNPS maternité | | 0,75 % | plafond 70 000 |
| CNPS accidents du travail (2 à 5 % selon le secteur) | | 2 % | plafond 70 000 |
| CMU | 500 F | 500 F | par personne couverte, 8 au plus |

**ITS** (réforme 2024), barème mensuel sur le brut soumis : 0 % jusqu'à 75 000, puis 16, 21, 24, 28 et 32 % au-delà
de 8 000 000 ; moins 5 500 F par demi-part au-delà d'une part (parts : 1 seul, 2 marié, 1,5 seul avec enfants, + 0,5
par enfant, 5 au plus). **Taxes de l'employeur** : 1,2 % (personnel local) ou 10,4 % (expatriés), taxe d'apprentissage
0,4 %, formation professionnelle continue 0,6 % + 0,6 %.

### Sénégal (2026)

| Cotisation | Salarié | Employeur | Assiette |
|---|---|---|---|
| IPRES retraite, régime général | 5,6 % | 8,4 % | plafond 432 000 |
| IPRES retraite complémentaire (**cadres seulement**) | 2,4 % | 3,6 % | plafond 1 296 000 |
| CSS prestations familiales | | 7 % | plafond 63 000 |
| CSS accidents du travail (1, 3 ou 5 % selon votre activité) | | 1 % | plafond 63 000 |
| IPM prévoyance maladie (taux et plafond de **votre** IPM) | 3 % | 3 % | plafond 250 000 |

**IR**, barème annuel : brut soumis × 12, moins l'abattement de 30 % (au plus 900 000 par an), sans autre déduction ;
revenu arrondi au millier inférieur ; barème 0 % jusqu'à 630 000, puis 20, 30, 35, 37, 40 et 43 % au-delà de
50 000 000 ; **réduction pour charges de famille** en pourcentage de l'impôt selon les parts, entre un minimum et un
maximum (1,5 part : 10 %, de 100 000 à 300 000 … 5 parts : 45 %, de 800 000 à 3 180 000) ; impôt plafonné à 43 % du
revenu imposable ; divisé par 12. Parts : 1 ; marié 1,5, plus 0,5 si le conjoint n'a pas de revenu ; plus 0,5 par
enfant ; 5 au plus.

**TRIMF** : 900 à 36 000 F par an selon le revenu brut annuel, retenus par douzième, aussi pour l'épouse sans revenu.
**Taxe de l'employeur** : CFCE 3 % du brut.

Les sources de chaque modèle sont affichées dans **Règles de paie**.

## Verser les salaires

Le mode autonome ne paie rien lui-même : il produit l'**ordre de virement** (PDF à signer, Excel ou CSV à importer dans
la banque en ligne). Les salaires payés en espèces ou par chèque n'y figurent pas. Kauris Cloud peut y brancher un
versement automatique (paiement PI-SPI) : un bouton *Verser les salaires* apparaît alors sur le mois clos.

## Ce que la paie ne calcule pas d'elle-même

Le droit aux congés payés, la prime d'ancienneté d'une convention collective et les indemnités de fin de contrat se
saisissent comme primes (dans le contrat ou dans le mois). Les déclarations se déposent sur les portails de la caisse
sociale et des impôts à partir des tableaux du mois.
