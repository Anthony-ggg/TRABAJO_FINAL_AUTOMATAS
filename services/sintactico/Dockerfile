FROM eclipse-temurin:21-jdk AS build
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY pom.xml .
COPY services/shared/pom.xml services/shared/
COPY services/lexico/pom.xml services/lexico/
COPY services/sintactico/pom.xml services/sintactico/
COPY services/semantico/pom.xml services/semantico/
COPY orchestrator/pom.xml orchestrator/

RUN mvn dependency:go-offline -B 2>/dev/null || true

COPY services/shared services/shared
COPY services/lexico services/lexico
COPY services/sintactico services/sintactico
COPY services/semantico services/semantico
COPY orchestrator orchestrator

RUN mvn clean package -DskipTests -pl services/shared,services/sintactico -am -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/services/sintactico/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
