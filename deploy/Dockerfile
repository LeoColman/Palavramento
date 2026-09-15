# syntax=docker/dockerfile:1
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 Leonardo Colman Lopes

# ---- build: server distribution, lexicon artifact included (ADR 0004) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# There is no Android SDK in this image, so the app module stays out of the build
# (settings.gradle.kts). The cache mount keeps Gradle, its dependencies and the JDK 17
# toolchain between deploys.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -Ppalavramento.skipApp=true :server:installDist

# ---- runtime: JRE plus the installed distribution ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
# The trie lexicon plus the JVM settle around 800 MB resident; 1 GB of heap leaves room.
ENV JAVA_OPTS="-Xmx1g"
EXPOSE 8080
CMD ["/app/bin/server"]
