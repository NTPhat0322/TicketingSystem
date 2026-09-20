# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY presentation/pom.xml presentation/
COPY bootstrap/pom.xml bootstrap/
RUN mvn -B -q -pl bootstrap -am dependency:go-offline

COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
COPY presentation/src presentation/src
COPY bootstrap/src bootstrap/src
RUN mvn -B -q -pl bootstrap -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/bootstrap/target/bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
