FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY --chown=app:app target/oc-app-0.1.0-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 8081

ENTRYPOINT ["java", \
  "-Xms128m", "-Xmx320m", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:+UseSerialGC", \
  "-Duser.timezone=UTC", \
  "-jar", "/app/app.jar"]