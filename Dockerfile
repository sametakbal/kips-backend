FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY --chmod=0755 gradlew ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-noble
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER 10001:10001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
