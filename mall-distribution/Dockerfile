FROM openjdk:17-slim
VOLUME /tmp
COPY target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "/app.jar"]
