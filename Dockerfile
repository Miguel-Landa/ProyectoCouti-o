FROM node:22-alpine AS frontend
WORKDIR /web
COPY package.json .
RUN npm install
COPY build.js .
COPY src/main/resources/static ./src/main/resources/static
RUN node build.js

FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=frontend /web/build-static ./src/main/resources/static
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]