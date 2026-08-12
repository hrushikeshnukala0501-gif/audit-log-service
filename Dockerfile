FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 auditlog
COPY --from=build /workspace/target/audit-log-service-*.jar app.jar
USER auditlog
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
