# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first so source-only changes reuse the cached layer.
# Maven Central rate-limits shared CI egress IPs (HTTP 429), which fails the build on an
# unlucky run rather than on anything wrong with the code — so retry the resolve a few times
# with a backoff before giving up.
COPY pom.xml ./
RUN for attempt in 1 2 3 4 5; do \
      mvn -B -q dependency:go-offline && break; \
      echo "dependency resolution failed (attempt $attempt), retrying in $((attempt * 15))s"; \
      sleep $((attempt * 15)); \
    done

# Not built with -o: dependency:go-offline does not reliably prefetch every plugin artifact
# package needs, so a strict offline build fails on the ones it missed. This resolves from the
# cache the step above filled, and only reaches out for what is genuinely absent.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system bag && useradd --system --gid bag --uid 10001 bag
COPY --from=build /build/target/*.jar /app/app.jar
USER 10001

# APP_VERSION is supplied at runtime (downward API in Kubernetes, env in compose)
# so the same image can be deployed as any version.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
