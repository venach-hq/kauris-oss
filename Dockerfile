# Image du mode autonome de Kauris (tous les modules de ce dépôt), construite depuis les sources :
#   make image                      → kauris:dev
#   docker compose (deploy/)        → voir docs/guide/installation.md
# Aucun secret dans l'image : la base et ses identifiants viennent des variables d'environnement.

FROM eclipse-temurin:21-jdk AS construction
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY kauris-kernel/pom.xml kauris-kernel/
COPY kauris-ventes/pom.xml kauris-ventes/
COPY kauris-standalone/pom.xml kauris-standalone/
# Les dépendances d'abord : elles restent en cache tant que les pom.xml ne changent pas.
RUN ./mvnw -B -q dependency:go-offline -pl kauris-standalone -am || true
COPY kauris-kernel/src kauris-kernel/src
COPY kauris-ventes/src kauris-ventes/src
COPY kauris-standalone/src kauris-standalone/src
RUN ./mvnw -B -q -DskipTests package -pl kauris-standalone -am \
    && cp kauris-standalone/target/kauris-standalone.jar /kauris.jar

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.title="Kauris" \
      org.opencontainers.image.description="Gestion des PME de la zone UEMOA : ventes, caisse, stock, achats" \
      org.opencontainers.image.source="https://github.com/venach-hq/kauris-oss" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      org.opencontainers.image.vendor="Venach"
RUN groupadd --system kauris && useradd --system --gid kauris --home-dir /app kauris \
    && mkdir -p /app/polices && chown -R kauris:kauris /app
WORKDIR /app
COPY --from=construction --chown=kauris:kauris /kauris.jar /app/kauris.jar
USER kauris
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8090 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/kauris.jar"]
