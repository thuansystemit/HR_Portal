FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring && \
    mkdir -p /app/uploads /app/logs && chown -R spring:spring /app/uploads /app/logs

USER spring

COPY target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dcom.sun.net.ssl.checkRevocation=true", \
  "-jar", "app.jar"]
