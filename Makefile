# Toutes les commandes du projet. La CI (GitHub aujourd'hui, une autre demain) n'appelle que ces cibles :
# rien de ce qui suit ne dépend d'un service hébergé en particulier.
SHELL := /bin/bash
MVN := ./mvnw -B

.DEFAULT_GOAL := help

.PHONY: help verify test package run install secrets clean

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[1m%-10s\033[0m %s\n", $$1, $$2}'

verify: ## Compile et lance tous les tests (ce que fait la CI)
	$(MVN) verify

test: ## Lance les tests
	$(MVN) test

package: ## Construit les jars sans relancer les tests
	$(MVN) -DskipTests package

install: ## Installe les modules dans le dépôt Maven local (utilisé par kauris-cloud)
	$(MVN) -DskipTests install

run: install ## Lance le mode autonome en développement (http://localhost:8090)
	SPRING_PROFILES_ACTIVE=dev $(MVN) -pl kauris-standalone spring-boot:run

secrets: ## Cherche des secrets dans le code et tout l'historique git (Docker requis)
	docker run --rm -v "$(CURDIR)":/repo zricethezav/gitleaks:latest detect --source /repo --redact -v

clean: ## Supprime les fichiers compilés
	$(MVN) clean
