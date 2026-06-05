# Full Docker build for CI or cold builds.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /project

# Cache Gradle and dependency resolution layers.
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY common/build.gradle.kts common/build.gradle.kts
COPY service/build.gradle.kts service/build.gradle.kts
COPY client/common/build.gradle.kts client/common/build.gradle.kts
COPY client/stress/build.gradle.kts client/stress/build.gradle.kts

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# Copy sources and build the service shadow JAR.
COPY common/src common/src
COPY service/src service/src
COPY client/common/src client/common/src
COPY client/stress/src client/stress/src

RUN ./gradlew :service:shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S punishments && adduser -S punishments -G punishments

WORKDIR /app

COPY --from=build /project/service/build/libs/service-*.jar app.jar

RUN chown -R punishments:punishments /app
USER punishments

EXPOSE 8080 9090

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseStringDeduplication", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
