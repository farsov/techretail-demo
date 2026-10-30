# Imagen del backend: compila con Maven y corre solo con el JRE, con un usuario sin privilegios.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/app.jar app.jar
USER app
# Sin DB_URL usa la base en memoria; en la nube se pasan DB_URL, DB_USER y DB_PASSWORD al ejecutar.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
