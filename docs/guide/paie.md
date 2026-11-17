# Paie

Le module Paie prépare les bulletins de paie des entreprises installées au **Togo** et en **Côte d'Ivoire** : salariés,
contrats de travail, cotisations sociales, impôt sur les salaires, net à payer, livre de paie, déclarations et
virement des salaires.

Menu : **Paie**. Le pays vient de la fiche de l'entreprise (**Entreprise**) : la paie calcule selon les règles du
pays où l'entreprise est installée. Pour un autre pays de l'UEMOA, l'écran le signale et rien ne se calcule.

## Qui peut voir les salaires

Les salaires sont confidentiels. Les quatre permissions de la paie sont réservées au **propriétaire** et à
l'**administrateur** ; le gestionnaire et le lecteur n'y ont pas accès. Pour un comptable ou un responsable RH,
créez un rôle (**Organisation → Rôles**) et cochez ce qu'il faut :

| Permission | Ouvre |
|---|---|
| Voir la paie (`paie.consulter`) | Salariés, contrats, bulletins, déclarations |
| Préparer la paie (`paie.preparer`) | Fiches, contrats, éléments du mois (absences, primes, avances) |
| Clôturer la paie (`paie.cloturer`) | Clôture et réouverture d'un mois, versement, salaires payés |
| Taux et plafonds (`paie.parametres.modifier`) | Corriger un taux ou un plafond |

## Mise en place (une fois)

1. **Salariés** → *Ajouter un salarié* : nom, prénoms, numéro d'assuré (CNSS ou CNPS), situation de famille,
   enfants à charge, conjoint sans revenu, mode de paiement (virement, Mobile Money, espèces, chèque) et compte.
   Le matricule (S-0001…) est donné par Kauris.
2. Juste après, le **contrat** : type (CDI, CDD…), poste, catégorie, date d'embauche, salaire de base mensuel et
   les éléments versés **chaque mois** :
   - *prime ou indemnité soumise* (ancienneté, logement payé en argent, heures supplémentaires fixes…) : elle
     entre dans le brut soumis aux cotisations et à l'impôt ;
   - *avantage en nature* (logement, véhicule, nourriture fournis) : soumis aux cotisations et à l'impôt, mais
     pas payé en argent, il ne s'ajoute donc pas au net ;
   - *indemnité non soumise* (remboursement de frais, indemnité exonérée) : payée, sans cotisation ni impôt.
     Vérifiez qu'elle l'est vraiment : une indemnité mal classée fausse les déclarations.

Un salarié n'a qu'un contrat en cours. Pour une augmentation, corrigez le contrat : les mois déjà clos gardent
l'ancien salaire.

## Chaque mois

1. **Mois de paie** → choisissez le mois → *Ouvrir la paie du mois*. Kauris calcule aussitôt un bulletin pour
   chaque salarié sous contrat pendant le mois.
2. Cliquez sur un salarié pour saisir **ce qui change ce mois-là** :
   - les **jours d'absence non payés** (sur 30) ;
   - les **primes du mois**, heures supplémentaires ;
   - les **avances** et remboursements de prêt (*retenue sur le net*).
   Le bulletin se recalcule à chaque enregistrement. Une retenue qui rendrait le net négatif est refusée.
3. Vérifiez les bulletins (*Bulletin* ouvre le document imprimable), puis **Clôturer le mois** : les bulletins
   reçoivent leur numéro (BP-202611-0001…) et sont **figés**. Ils ne changent plus, même si un contrat change.
4. Téléchargez les documents du mois (Excel, CSV ou PDF) :
   - **Livre de paie** : un salarié par ligne, brut, cotisations, impôt, net, coût total ;
   - **Cotisations** : CNSS et AMU (Togo) ou CNPS et CMU (Côte d'Ivoire), part du salarié, part de l'employeur ;
   - **Impôt** : IRPP retenu (Togo) ; ITS retenu et taxes de l'employeur sur les salaires (Côte d'Ivoire) ;
   - **Ordre de virement** : les salaires payés par virement ou Mobile Money, à signer et remettre à la banque.
5. Une fois les salaires versés : *Marquer les salaires comme payés*.

Une erreur après la clôture ? *Rouvrir* (tant que les salaires ne sont pas marqués payés) : les bulletins
redeviennent des brouillons. À la nouvelle clôture, ils reçoivent de nouveaux numéros ; un numéro n'est jamais
réutilisé.

**Entrée ou sortie en cours de mois** : le salaire de base est payé au prorata des jours sous contrat, sur 30.
Pour une sortie, ouvrez le contrat → *Terminer le contrat* avec le dernier jour travaillé ; les indemnités de fin
de contrat (préavis, congés, licenciement) se saisissent comme primes du dernier mois.

## Comment Kauris calcule

Les montants sont en francs CFA, sans décimales. Une cotisation s'arrondit au franc le plus proche.

### Togo

| Cotisation | Salarié | Employeur | Base |
|---|---|---|---|
| CNSS pension | 4 % | 12,5 % | brut soumis |
| CNSS prestations familiales | | 3 % | brut soumis |
| CNSS risques professionnels | | 2 % | brut soumis |
| AMU (assurance maladie universelle) | 5 % | 5 % | brut soumis |

**IRPP**, calculé sur l'année :

1. (brut soumis − CNSS et AMU du salarié) × 12 ;
2. moins l'**abattement de 28 %**, calculé sur la part du revenu qui ne dépasse pas 10 000 000 ;
3. moins **10 000 par mois** et par personne à charge (enfants, conjoint sans revenu), six au plus ;
4. arrondi au millier inférieur, puis barème : 0 % jusqu'à 900 000, 3 % jusqu'à 3 000 000, 10 % jusqu'à
   6 000 000, 15 % jusqu'à 9 000 000, 20 % jusqu'à 12 000 000, 25 % jusqu'à 15 000 000, 30 % jusqu'à
   20 000 000, 35 % au-delà ;
5. l'impôt de l'année, arrondi à la dizaine inférieure, divisé par 12.

Le bulletin écrit le détail de ce calcul.

### Côte d'Ivoire

| Cotisation | Salarié | Employeur | Base |
|---|---|---|---|
| CNPS retraite | 6,3 % | 7,7 % | brut soumis, plafonné à 3 375 000 par mois |
| CNPS prestations familiales | | 5 % | brut soumis, plafonné à 70 000 |
| CNPS assurance maternité | | 0,75 % | idem |
| CNPS accidents du travail | | 2 % (2 à 5 % selon le secteur) | idem |
| CMU | moitié | moitié | 1 000 par mois et par personne couverte (salarié, conjoint, enfants), 8 au plus |

**ITS** (réforme en vigueur depuis le 1er janvier 2024), sur le brut soumis du mois : 0 % jusqu'à 75 000, 16 %
jusqu'à 240 000, 21 % jusqu'à 800 000, 24 % jusqu'à 2 400 000, 28 % jusqu'à 8 000 000, 32 % au-delà ; moins la
**réduction pour charges de famille** : 5 500 par demi-part au-delà d'une part. Les parts : une pour une personne
seule, deux pour un couple marié, une demi-part par enfant ; une personne seule avec enfants part d'une part et
demie ; cinq parts au plus.

**Taxes de l'employeur** sur le brut soumis : impôt patronal 1,2 % (10,4 % pour un salarié expatrié, à cocher sur
sa fiche), taxe d'apprentissage 0,4 %, formation professionnelle continue 0,6 % et taxe additionnelle 0,6 %.

## Taux et plafonds

Les valeurs ci-dessus ont été vérifiées en novembre 2026, mais les sources publiques ne s'accordent pas toujours
(par exemple sur le partage de l'AMU au Togo) et les lois de finances les changent. Dans **Taux et plafonds**,
corrigez une valeur **à partir d'un mois** : les mois suivants l'utilisent, les mois déjà clos ne changent pas
(rouvrez-les s'il le faut). Notez la source dans la note (lettre de la caisse, loi de finances). Retirer une
correction rend la valeur précédente.

Faites vérifier vos premiers bulletins par votre comptable ou votre caisse : Kauris calcule selon la règle générale,
sans les cas particuliers d'une convention collective ou d'un accord d'entreprise.

Les tranches de l'impôt suivent la loi : elles changent avec une nouvelle version de Kauris.

## Verser les salaires

Le mode autonome ne paie rien lui-même : il produit l'**ordre de virement** (PDF à signer, Excel ou CSV à importer
dans la banque en ligne). Les salaires payés en espèces ou par chèque n'y figurent pas.

Kauris Cloud peut y brancher un versement automatique (paiement PI-SPI) : un bouton *Verser les salaires* apparaît
alors sur le mois clos.

## Ce que la paie ne fait pas encore

Pas de calcul automatique des congés payés, des heures supplémentaires majorées, de la prime d'ancienneté selon la
convention collective, ni des déclarations au format des portails (CNSS, CNPS, impôts) : saisissez ces montants
comme primes, et reportez les totaux des tableaux sur les portails.
