# Toutes les commandes du projet. La CI (GitHub aujourd'hui, une autre demain) n'appelle que ces cibles :
# rien de ce qui suit ne dépend d'un service hébergé en particulier.
SHELL := /bin/bash
MVN := ./mvnw -B
COMPOSE := docker compose -f docker-compose.dev.yml

.DEFAULT_GOAL := help

.PHONY: help dev-up dev-down dev-reset verify test test-js package run install secrets clean

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[1m%-10s\033[0m %s\n", $$1, $$2}'

dev-up: ## Démarre le MySQL local du mode autonome (localhost:3308)
	$(COMPOSE) up -d

dev-down: ## Arrête le MySQL local (les données sont gardées)
	$(COMPOSE) down

dev-reset: ## Arrête le MySQL local ET efface ses données
	$(COMPOSE) down -v

verify: test-js ## Compile et lance tous les tests, y compris sur un MySQL jetable (Docker requis ; ce que fait la CI)
	$(MVN) verify

test-js: ## Teste les scripts des écrans qui calculent (node requis, sautés sans node)
	@if command -v node >/dev/null; then for f in kauris-*/src/test/js/*.test.js; do node "$$f" || exit 1; done; \
	else echo "node absent : tests des scripts sautés"; fi

test: ## Lance les tests
	$(MVN) test

package: ## Construit les jars sans relancer les tests
	$(MVN) -DskipTests package

install: ## Installe les modules dans le dépôt Maven local (utilisé par kauris-cloud)
	$(MVN) -DskipTests install

run: install ## Lance le mode autonome en développement (http://localhost:8090, après make dev-up)
	SPRING_PROFILES_ACTIVE=dev $(MVN) -pl kauris-standalone spring-boot:run

secrets: ## Cherche des secrets dans le code et tout l'historique git (Docker requis)
	docker run --rm -v "$(CURDIR)":/repo zricethezav/gitleaks:latest detect --source /repo --redact -v

clean: ## Supprime les fichiers compilés
	$(MVN) clean
