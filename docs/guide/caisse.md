# Caisse

Menu **Caisse** : la vente rapide au comptoir, pour une boutique, un restaurant, une pharmacie… Chaque ticket est
une vraie facture des [ventes](ventes.md), émise et réglée d'un coup : le stock et les règlements suivent.

## Ouvrir sa caisse

Chaque personne ouvre **sa** caisse, en début de journée, avec le **fond de caisse** : les espèces mises dans le
tiroir pour rendre la monnaie (ce n'est pas une recette). Kauris Cloud demande aussi sur quels comptes de
trésorerie arrivent les espèces, le Mobile Money et la carte.

## Vendre

1. Touchez les articles (ou tapez leur nom ; avec un lecteur de codes-barres, la référence puis Entrée les ajoute).
   Ajustez les quantités sur le ticket avec − et +.
2. Client : « Client comptoir » par défaut, ou un client du fichier pour une facture à son nom.
3. Paiement :
   - **Espèces reçues** : la monnaie à rendre s'affiche ; rien saisi = le compte juste. Les boutons 1 000, 2 000,
     5 000, 10 000 remplissent le montant reçu.
   - **Mobile Money** (avec la référence de la transaction) et **carte** : déduits des espèces.
4. **Encaisser** : le ticket s'affiche, prêt à imprimer (imprimante de tickets 80 mm, ou A4 avec le lien
   « Facture A4 »). Cochez « imprimer le ticket tout de suite » pour qu'il parte seul à l'impression.

Les prix sont ceux du catalogue (TTC à l'écran). Une remise en % s'applique à tout le ticket.

Un client rapporte un article : faites un **avoir** depuis la facture du ticket (Ventes → « Faire un avoir sur cette facture »). Les espèces rendues
sortent du tiroir : elles apparaîtront dans l'écart à la clôture, notez-le.

## Clôturer

Le soir, **Clôturer** : comptez le tiroir (en saisissant le nombre de billets et de pièces, Kauris fait le total),
saisissez les espèces comptées. L'**écart** compare ce total à ce qui devrait y être : fond + ventes en espèces.
Une note explique un écart. Le **rapport de caisse** (PDF) résume la journée : tickets, espèces, Mobile Money,
carte, fond, attendu, compté, écart, avec la liste des tickets.

Une caisse se clôture le jour où elle a été ouverte : celle de la veille ne vend plus tant qu'elle n'est pas
clôturée. Un responsable qui peut encaisser voit toutes les caisses et peut clôturer celle d'un caissier absent.

## Calculatrice

Onglet **Calculatrice**, ou bouton sur l'écran de vente. Grandes touches, au doigt ou au clavier (chiffres,
`+ - * /`, Entrée, Échap, Retour arrière).

| Touche ou outil | Exemple |
|---|---|
| `%` comme une calculatrice de bureau | 200 + 10 % = 220 ; 200 × 10 % = 20 |
| **+ TVA** : du HT au TTC | 10 000 → 11 800 (TVA 18 %) |
| **− TVA** : du TTC au HT | 11 800 → 10 000 |
| **TVA du TTC** : la TVA comprise dans un prix TTC | 11 800 → 1 800 |
| **TVA du HT** : la TVA à ajouter | 10 000 → 1 800 |
| **Arrondir** au pas choisi (5, 10, 25, 50, 100 F) | 1 183 → 1 185 |
| Mémoire MC, MR, M+, M− ; ± ; CE ; Copier | |
| **Prix de vente à partir du coût** | coût 800, marge 25 % sur le coût → 1 000 HT ; 25 % sur le prix de vente → 1 066,67 |
| **Marge d'un article** | coût 800, vente 1 000 → marge 200, taux de marge 25 %, taux de marque 20 %, coefficient 1,25 |
| **Remise** | 25 000 − 10 % → 22 500 ; de 25 000 à 20 000 → 20 % |
| **Monnaie à rendre** | 3 540 payés avec 10 000 → 6 460 : 1 × 5 000, 1 × 1 000, 1 × 250, 1 × 200, 1 × 10 |

Le taux de TVA des touches est celui des réglages des ventes ; il se change sous les touches. La flèche → met un
résultat d'outil à l'écran de la calculatrice. La calculatrice n'enregistre rien.

## Permission

« Vendre au comptoir » (avec « Voir » des ventes) suffit pour tenir une caisse : un caissier n'a pas besoin de
pouvoir préparer, émettre ni encaisser des factures ailleurs.
