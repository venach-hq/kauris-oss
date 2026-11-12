# Stock et achats

Menus **Stock** et **Achats**.

## Stock

Seuls les articles du catalogue avec « Suivre le stock » coché sont suivis.

- **État du stock** : quantité et valeur par article, par dépôt ou en tout. La valeur est au **coût moyen unitaire
  pondéré** (CMUP), méthode admise par le SYSCOHADA. Un seuil d'alerte par article signale ce qu'il faut racheter.
- Le stock bouge tout seul : une **facture émise** (ou un ticket de caisse) fait sortir les articles du dépôt
  principal, un **avoir** les y fait revenir, une **réception** de commande les fait entrer.
- **Entrée ou sortie** : le reste (achat hors module Achats, don, casse, perte, consommation interne). Le coût d'une
  entrée met à jour le coût moyen.
- **Transfert** : d'un dépôt à l'autre ; la valeur suit.
- **Inventaire** : saisissez ce que vous avez compté ; Kauris crée les écarts. Un article laissé vide ne bouge pas.
- **Mouvements** : tout est gardé, rien ne s'efface ; une erreur se corrige par un mouvement inverse ou un
  inventaire.
- **Dépôts** : magasins, boutiques, entrepôts. Le dépôt principal sert par défaut aux ventes.

Un stock peut passer en négatif (une vente saisie avant la réception) : il est signalé en rouge, la vente n'est
pas bloquée.

État du stock et fiche d'un article s'impriment en PDF.

## Achats

Le circuit : **bon de commande → réception → facture fournisseur → paiement**.

1. **Fournisseurs** : la fiche sert aux bons de commande ; son historique montre commandes, factures et ce que
   vous lui devez.
2. **Commandes** : un bon de commande se prépare en brouillon puis « Envoyer (numéroter) » le fige ; il se
   transmet en PDF.
3. **Réception** : à l'arrivée de la marchandise, saisissez les quantités reçues ; les articles suivis entrent en
   stock au prix de la commande. Les livraisons partielles sont possibles ; « Clore » une commande dont le reste
   n'arrivera pas.
4. **Factures fournisseurs** : depuis une commande, ou sans commande (loyer, électricité…), avec leur échéance.
5. **Paiements** : chaque paiement au fournisseur ; la facture est soldée à zéro. L'onglet **À payer** montre ce
   qui reste dû et ce qui est en retard.

Kauris Cloud inscrit les paiements en trésorerie et peut payer un fournisseur par PI-SPI.
