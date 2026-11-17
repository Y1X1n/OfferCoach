FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY --chown=app:app target/*.jar /app/app.jar

USER app

ENTRYPOINT ["java", \
  "-Xms96m", "-Xmx384m", \
  "-XX:MaxMetaspaceSize=160m", \
  "-XX:+UseSerialGC", \
  "-Duser.timezone=UTC", \
  "-jar", "/app/app.jar"]
