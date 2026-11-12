# Sécurité

Kauris tient des données d'entreprises : factures, clients, montants. Une faille se signale **en privé**, jamais
dans un ticket public.

## Signaler une faille

Utilisez « Report a vulnerability » dans l'onglet **Security** de ce dépôt (signalement privé GitHub). Décrivez la
faille, comment la reproduire et ce qu'elle permet. Nous accusons réception sous 5 jours ouvrés et vous tenons
informé jusqu'à la correction ; vous êtes cité dans la note de version si vous le souhaitez.

## Versions suivies

Seule la dernière version publiée reçoit les correctifs de sécurité. Mettez à jour régulièrement
(`docker compose pull && docker compose up -d`).

## Bonnes pratiques d'installation

- Kauris sur Internet : toujours derrière HTTPS ([installation](docs/guide/installation.md)).
- Mots de passe de la base longs et aléatoires ; fichier `.env` hors de git et sauvegardé à part.
- Sauvegardes quotidiennes de la base, gardées hors du serveur.
