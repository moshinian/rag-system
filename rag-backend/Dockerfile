FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace/rag-backend
COPY rag-backend/pom.xml ./pom.xml
COPY rag-backend/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 rag \
    && useradd --system --uid 10001 --gid rag --home-dir /app rag
WORKDIR /app
COPY --from=build /workspace/rag-backend/target/rag-service-0.0.1-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/data/uploads /app/logs && chown -R rag:rag /app

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
