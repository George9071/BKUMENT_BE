FROM eclipse-temurin:17-jdk

LABEL maintainer="levodangkhoatg2@gmail.com"

WORKDIR /app

COPY target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]