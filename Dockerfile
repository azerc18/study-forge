FROM eclipse-temurin:25-jre

RUN useradd --system --uid 1001 app \
 && mkdir -p /app/storage/uploads \
 && chown -R app:app /app

WORKDIR /app
COPY --chown=app:app target/*.jar app.jar
USER app

ENV JAVA_OPTS="-Xms256m -Xmx768m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]