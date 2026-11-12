# Ventes

Menu **Ventes** : clients, catalogue, devis, factures, avoirs, règlements, impayés.

## Avant la première facture

1. **Réglages** (onglet de droite) : l'entreprise est-elle assujettie à la TVA ? Taux par défaut, délai de paiement,
   durée de validité des devis, conditions imprimées (pénalités de retard…). Non assujettie : indiquez la mention
   imprimée, par exemple « TVA non applicable ».
2. **Catalogue** : vos articles et prestations, avec leur prix **hors taxe** et leur taux de TVA. Cochez « Suivre le
   stock » pour les marchandises : chaque facture les sortira du stock.
3. **Clients** : nom, contact, email (pour envoyer factures et relances), NIF et RCCM pour les entreprises, délai de
   paiement propre si besoin.

Les montants s'écrivent sans décimales : `25000` ou `25 000`. Une virgule (`25 000,50`) est refusée, jamais
ignorée : le franc CFA n'a pas de centimes.

## Devis, factures, avoirs

- Un document se prépare en **brouillon** : il se modifie et se supprime librement, il ne compte nulle part.
- **Émettre** lui donne son numéro (`DE-2026-0001`, `FA-2026-0001`, `AV-2026-0001`), sans trou, dans l'ordre des
  dates ; client, mentions et conditions sont figés. Un document ne s'émet pas à une date future, ni avant le
  dernier émis du même type.
- Un devis émis s'accepte, se refuse, s'annule ou **se transforme en facture** en un clic.
- Une facture émise ne se modifie plus et ne se supprime pas : elle se corrige par un **avoir** (« Faire un avoir sur cette
  facture »), jamais plus que son montant.
- Chaque document se voit à l'écran et en **PDF**, avec l'en-tête et le thème de l'entreprise.

Les factures de Kauris ne sont pas des factures normalisées certifiées (e-MECeF, FNE, SECeF…) : si votre pays
les exige, la facture certifiée reste à produire par l'outil agréé.

## Règlements et impayés

Sur une facture émise : **Enregistrer le règlement** (date, montant, mode : espèces, virement, chèque, Mobile Money,
carte…, référence). Plusieurs règlements partiels sont possibles ; un règlement saisi par erreur s'annule avec un
motif (il reste visible, barré).

L'onglet **Impayés** montre ce que chaque client doit et ce qui est en retard. Le **relevé de compte** d'un client
(fiche client) reprend factures, avoirs et règlements sur une période, avec le solde.

## Envoyer par email et relancer

- **Envoyer par email** (document émis) : le destinataire est l'email du client, l'objet et le message sont
  pré-remplis (montant, échéance, coordonnées bancaires) et modifiables ; le PDF est joint.
- **Relancer par email** (fiche client ou Impayés) : le message liste les factures dues, avec les jours de retard ;
  le relevé de compte est joint.

Kauris installé chez vous n'envoie pas d'email lui-même : « Ouvrir dans ma messagerie » prépare le message dans
votre messagerie ; téléchargez le PDF et joignez-le. Kauris Cloud envoie directement, et les réponses du client
arrivent sur l'email de l'entreprise.

## Permissions

| Permission (en bref) | Pour |
|---|---|
| Voir | consulter clients, catalogue, documents, impayés |
| Préparer | créer des clients et des brouillons |
| Émettre | numéroter les documents, accepter ou refuser un devis |
| Encaisser | enregistrer et annuler des règlements |
| Catalogue | gérer articles, prestations et prix |
| Réglages | TVA, délais, conditions |
| Vendre au comptoir | la [caisse](caisse.md) |
