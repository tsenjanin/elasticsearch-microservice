FROM docker.io/library/maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build

# Dependencies in their own layer so source changes don't re-download them
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Sample logs are only for local development; keep them out of the image
COPY src ./src
RUN rm -rf src/main/resources/logs \
    && mvn -B -q package -DskipTests \
    && cp target/*.jar app.jar

FROM docker.io/library/eclipse-temurin:25.0.4.1_1-jre
RUN useradd --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app
EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
