FROM eclipse-temurin:17-jdk

LABEL maintainer="phuc.trangeorge1564@hcmut.edu.vn"

WORKDIR /app

COPY target/*.jar app.jar

ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]