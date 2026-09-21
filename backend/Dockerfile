# Multi-stage build. The middle stage compiles this backend against the Aerospike preview Java
# SDK (aerospike-client-java-sdk, branch "stage") - a developer-preview library with no published
# Maven Central artifact, built from source in the first stage. See backend/README.md "Build".

# ---- Stage 1: build the Aerospike preview SDK from source -------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS sdk-builder

# SDK_REF tracks a moving branch ("stage"), which is exactly what broke here once already:
# CLIENT-5451 (2026-09-10, commit 6222bee) changed Cluster.supportsAel()'s gate from server 8.1.3
# to 8.2.0, silently flipping this demo's AEL path back to the classic-Exp fallback - no code here
# changed, `stage` just moved. First pinned to f5d314b (the commit before that change) to match the
# 8.1.3 server available at the time; now pinned to 6222bee instead - still `stage`'s tip as of
# 2026-09-12, nothing has landed since - because the server image is now genuinely 8.2.0 (see
# backend/README.md's "AEL verified against a real 8.1.3+ server"), so there's no more reason to
# stay behind it. Re-verify (don't just bump blindly) before moving this pin again.
ARG SDK_REF=6222bee

RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

# `git clone --branch` only accepts ref names, not raw commit SHAs - which SDK_REF now is (see
# above) - and GitHub rejects fetching an arbitrary non-tip SHA directly ("couldn't find remote
# ref"). So this clones `stage` with enough depth to contain SDK_REF, then checks it out; depth 50
# is well past how far behind HEAD a pinned commit here is likely to drift before someone updates
# this pin. Works for SDK_REF=stage too (checkout is then a no-op).
RUN git clone --branch stage --depth 50 \
      https://github.com/aerospike/aerospike-client-java-sdk /sdk \
    && cd /sdk && git checkout -q ${SDK_REF}

# Only the "client" module is needed (reactor also has examples/benchmarks). Tests are skipped -
# they need a live cluster fixture and would fail/hang in a Docker build with no server reachable.
RUN cd /sdk && mvn -q -pl client -am install -DskipTests

# ---- Stage 2: build the backend jar against that SDK -------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

COPY --from=sdk-builder /root/.m2 /root/.m2

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q package -DskipTests

# ---- Stage 3: slim runtime image ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# docker-compose's healthcheck for this service is `wget -qO- http://localhost:8081/health` -
# Alpine JRE images don't reliably ship a wget build with -qO support, so install it explicitly.
RUN apk add --no-cache wget

WORKDIR /app
COPY --from=build /app/target/booking-backend.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
