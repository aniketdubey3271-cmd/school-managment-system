FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
# Maven Central can temporarily rate-limit Render with HTTP 429. Retry only
# that transient download failure; fail immediately for code/build errors.
RUN for attempt in 1 2 3 4 5; do \
      if mvn -B -DskipTests package > /tmp/maven-build.log 2>&1; then \
        cat /tmp/maven-build.log; \
        exit 0; \
      else \
        status=$?; \
        cat /tmp/maven-build.log; \
      fi; \
      if ! grep -Eq '429|Too Many Requests' /tmp/maven-build.log || [ "$attempt" -eq 5 ]; then \
        exit "$status"; \
      fi; \
      delay=$((attempt * 10)); \
      echo "Maven Central rate-limited the build; retrying in ${delay}s (attempt $((attempt + 1))/5)..."; \
      sleep "$delay"; \
    done

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV PORT=10000
EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
