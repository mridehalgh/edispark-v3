# Eclipse Temurin 21.0.13+9, pinned Linux ARM64 runtime manifest.
FROM eclipse-temurin:21-jre@sha256:4a541e56af0ae9a1ff20cdbf43e400864542116b0e7804b37f51be4c574f2057
ARG SOURCE_COMMIT
LABEL org.opencontainers.image.source="https://github.com/mridehalgh/edispark-v3" \
      org.opencontainers.image.revision=$SOURCE_COMMIT
WORKDIR /app
COPY --chown=10001:10001 apps/edispark-api/target/edispark-api-1.0.0-SNAPSHOT.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.io.tmpdir=/tmp"
USER 10001:10001
VOLUME ["/tmp"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
