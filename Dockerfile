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

RUN addgroup -S punishment && adduser -S punishment -G punishment

WORKDIR /app

COPY --from=build /project/service/build/libs/punishment-service-*.jar app.jar

RUN chown -R punishment:punishment /app
USER punishment

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -XX:-OmitStackTraceInFastThrow -Dio.netty.allocator.maxCachedBufferCapacity=65536 -Dio.netty.recycler.maxCapacityPerThread=4096 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080 9090

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
