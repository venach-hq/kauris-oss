# Contribuer à Kauris

Merci ! Kauris sert des entreprises réelles : chaque changement doit rester simple, testé et sûr.

## Avant de coder

- Un bug : ouvrez un ticket avec ce que vous faisiez, ce que vous attendiez et ce qui s'est passé.
- Une idée de fonction : ouvrez d'abord un ticket pour en parler ; cela évite un travail qui ne serait pas repris.
- Lisez [docs/regles.md](docs/regles.md) : architecture, conventions, procédure par module.

## Proposer un changement

1. Une branche par sujet, puis une pull request vers `main`.
2. `make verify` passe sur votre poste (Docker requis pour les tests sur MySQL).
3. Des tests pour ce qui change : règles métier en mémoire, écrans et base sur MySQL.
4. Pas de secret, ni dans le code, ni dans les tests, ni dans la configuration.
5. Écrans, messages et documentation en français simple. Les montants en franc CFA n'ont pas de décimales.
6. Une migration de base ne se modifie jamais une fois publiée : on en ajoute une nouvelle.

## Accord de contribution

Kauris est publié sous AGPL-3.0 et Venach l'utilise aussi dans Kauris Cloud. Pour qu'une contribution puisse
servir aux deux, en ouvrant une pull request vous déclarez :

1. que vous êtes l'auteur de la contribution, ou que vous avez le droit de la proposer, et qu'elle ne contient pas
   de code d'un tiers incompatible avec l'AGPL-3.0 ;
2. que vous la placez sous la licence AGPL-3.0 du projet ;
3. que vous accordez à Venach, en plus, une licence mondiale, gratuite, non exclusive, perpétuelle et irrévocable
   d'utiliser, de modifier et de distribuer votre contribution, y compris sous d'autres licences (par exemple
   dans Kauris Cloud), et de concéder ces droits à d'autres.

Vous restez l'auteur de votre contribution et pouvez l'utiliser comme vous le voulez. Ajoutez la ligne
`Signed-off-by: Prénom Nom <email>` à vos commits (`git commit -s`) pour confirmer cet accord.
