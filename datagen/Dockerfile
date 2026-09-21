# Builds and runs the dataset generator as a one-shot docker-compose service.
# Writes hotels.ndjson / landmarks.json / stats.json to /data (a shared volume
# with `backend`) and exits. See ../docs/design.md for the schema and
# ../datagen/README.md for CLI flags and the record-size finding.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# Warm the dependency cache in its own layer so source-only edits skip the download.
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package

# 21-jre-alpine, not 17-jre-alpine: eclipse-temurin only publishes 17-jre-alpine
# for amd64 (confirmed via `docker manifest inspect` — no arm64 build exists),
# which breaks this on Apple Silicon. A jar compiled for release 17 runs fine
# on a 21 JRE, and 21-jre-alpine is multi-arch (also what backend/Dockerfile uses).
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/datagen.jar ./datagen.jar
# Owned by `app` before the VOLUME instruction so a fresh named volume (first run) inherits
# writable permissions for the non-root user below — Docker copies the image directory's content
# and ownership into an empty named volume the first time it's mounted.
RUN mkdir -p /data && chown app:app /data
VOLUME ["/data"]
USER app
# One-shot job: generates the dataset and exits, no long-running process to healthcheck.
HEALTHCHECK NONE
ENTRYPOINT ["java", "-jar", "/app/datagen.jar"]
