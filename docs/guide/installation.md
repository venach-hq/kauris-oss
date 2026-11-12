# Installer Kauris

Kauris s'installe avec Docker : une base MySQL et l'application, sur un serveur ou sur un poste du magasin qui sert
les autres postes du réseau.

## Ce qu'il faut

- Un ordinateur ou un serveur Linux (Windows et macOS marchent aussi avec Docker Desktop), 2 Go de mémoire au moins.
- [Docker](https://docs.docker.com/engine/install/) avec `docker compose`.

## Installer

```bash
mkdir kauris && cd kauris
# Les deux fichiers d'installation sont joints à chaque version :
#   https://github.com/venach-hq/kauris-oss/releases/latest
curl -LO https://github.com/venach-hq/kauris-oss/releases/latest/download/docker-compose.yml
curl -L -o .env https://github.com/venach-hq/kauris-oss/releases/latest/download/kauris.env.example
```

Ouvrez `.env` et donnez deux mots de passe longs et aléatoires (par exemple le résultat de `openssl rand -base64 24`) :

```
MYSQL_PASSWORD=…
MYSQL_ROOT_PASSWORD=…
```

Gardez ce fichier : sans lui, plus d'accès à la base. Puis :

```bash
docker compose up -d
```

Kauris répond sur `http://localhost:8090`. Au premier passage, l'écran d'installation crée l'organisation, son
premier établissement et le compte du propriétaire ; il se ferme ensuite pour toujours. Suite :
[Prise en main](prise-en-main.md).

## Depuis les autres postes du magasin

Dans `.env`, mettez `KAURIS_ECOUTE=0.0.0.0`, puis `docker compose up -d`. Les autres postes ouvrent
`http://<adresse-du-poste>:8090`. Sur un réseau local seulement : jamais ouvert ainsi sur Internet.

## Sur Internet (HTTPS)

Laissez `KAURIS_ECOUTE=127.0.0.1` et placez devant Kauris un proxy HTTPS. Avec [Caddy](https://caddyserver.com),
le certificat se fait tout seul ; fichier `Caddyfile` :

```
kauris.mon-entreprise.com {
    reverse_proxy 127.0.0.1:8090
}
```

Avec nginx, transmettez les en-têtes `Host`, `X-Forwarded-For` et `X-Forwarded-Proto`.

## Mettre à jour

```bash
docker compose pull && docker compose up -d
```

La base se met à jour au démarrage. Sauvegardez avant (ci-dessous). Pour rester sur une version donnée :
`KAURIS_VERSION=0.1.0` dans `.env`.

## Sauvegarder et restaurer

Tout est dans la base MySQL (logo compris). Sauvegarde, chaque jour et hors du serveur :

```bash
docker compose exec -T mysql sh -c 'mysqldump -ukauris -p"$MYSQL_PASSWORD" --single-transaction kauris' \
  | gzip > kauris-$(date +%F).sql.gz
```

Restauration dans une installation neuve (Kauris arrêté) :

```bash
docker compose up -d mysql
gunzip -c kauris-AAAA-MM-JJ.sql.gz | docker compose exec -T mysql sh -c 'mysql -ukauris -p"$MYSQL_PASSWORD" kauris'
docker compose up -d
```

## Réglages

| Variable (`.env`) | Rôle |
|---|---|
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Mots de passe de la base (obligatoires) |
| `KAURIS_VERSION` | Version de Kauris (`latest` par défaut) |
| `KAURIS_ECOUTE`, `KAURIS_PORT` | Adresse et port d'écoute (`127.0.0.1:8090` par défaut) |
| `KAURIS_CODE_SOURCE` | Adresse du code source si vous mettez à disposition une version modifiée (licence AGPL-3.0) |
| `KAURIS_GUIDE` | Adresse de ce guide, si vous en tenez une copie |

Polices des documents PDF : déposez des fichiers `.ttf` dans le dossier `polices/` à côté de
`docker-compose.yml`, puis choisissez-les dans Entreprise → Thème des documents.

## Sans Docker

Java 21 et MySQL 8 : téléchargez `kauris-<version>.jar` depuis les
[versions publiées](https://github.com/venach-hq/kauris-oss/releases), puis

```bash
SPRING_PROFILES_ACTIVE=prod DB_URL='jdbc:mysql://localhost:3306/kauris' DB_USERNAME=kauris DB_PASSWORD=… \
  java -jar kauris-<version>.jar
```
