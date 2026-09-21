# =============================================================
#  Dockerfile — Backend Micropay (Multi-stage build)
#  Stage 1: Build con Maven Wrapper / Eclipse Temurin 21 JDK
#  Stage 2: Runtime ligero con JRE Alpine
# =============================================================

# ──────────────────────────────────────────────────────────────
#  STAGE 1 — BUILD (Maven + JDK completo)
# ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

LABEL maintainer="Micropay Backend Team <backend@micropay.example>"
LABEL stage="builder"

ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xmx1024m"
ENV MAVEN_HOME=/opt/maven
ENV MAVEN_VERSION=3.9.9
ENV DEBIAN_FRONTEND=noninteractive

WORKDIR /opt/app

# Instala Maven manualmente (reproducible, sin wrapper inicial)
RUN apt-get update -qq && \
    apt-get install -qq --no-install-recommends curl ca-certificates unzip > /dev/null && \
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
      -o /tmp/apache-maven.tar.gz && \
    tar -xzf /tmp/apache-maven.tar.gz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION} ${MAVEN_HOME} && \
    ln -s ${MAVEN_HOME}/bin/mvn /usr/local/bin/mvn && \
    rm -rf /var/lib/apt/lists/* /tmp/apache-maven.tar.gz && \
    mvn --version

# Cache de dependencias (layer separado para rebuilds rápidos)
COPY backend/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2,rw \
    mvn -B -e -q dependency:go-offline -DskipTests

# Copia fuente y compila (sin tests — CI ya los corrió)
COPY backend/src ./src
RUN --mount=type=cache,target=/root/.m2,rw \
    mvn -B -e -q clean package \
        -DskipTests \
        -Dmaven.javadoc.skip=true \
        -Dmaven.source.skip=true

# Extrae fat-jar para layertools (Spring Boot thin deploy)
RUN java -Djarmode=layertools -jar target/micropay-backend.jar extract --destination target/extracted

# ──────────────────────────────────────────────────────────────
#  STAGE 2 — RUNTIME (JRE Alpine mínimo, no root user)
# ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="Micropay Backend Team <backend@micropay.example>"
LABEL stage="runtime"

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /opt/app

USER spring:spring

# Layers Spring Boot (orden de menos a más cambiante para cache)
COPY --from=builder /opt/app/target/extracted/dependencies/  ./
COPY --from=builder /opt/app/target/extracted/spring-boot-loader/  ./
COPY --from=builder /opt/app/target/extracted/snapshot-dependencies/  ./
COPY --from=builder /opt/app/target/extracted/application/  ./

# Puerto por defecto Spring Boot
EXPOSE 8080

# Healthcheck Java puro (sin curl) — Spring Boot Actuator /actuator/health
HEALTHCHECK --start-period=30s --interval=20s --timeout=5s --retries=5 \
  CMD ["java", "-cp", "BOOT-INF/classes:BOOT-INF/lib/*", \
       "org.springframework.boot.loader.launch.JarLauncher", \
       "--spring.main.web-application-type=none", \
       "--management.endpoints.web.exposure.include=health", \
       "--server.port=0"] \
    || wget -qO- --spider http://127.0.0.1:8080/actuator/health 2>/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher ${0} ${@}"]
