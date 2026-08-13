FROM eclipse-temurin:17.0.19_10-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 mall \
    && useradd --uid 10001 --gid mall --no-create-home --shell /usr/sbin/nologin mall \
    && mkdir -p /app /opt/lingqimall/uploads/products /opt/lingqimall/uploads/private \
    && chown -R mall:mall /app /opt/lingqimall

WORKDIR /app
COPY --chown=mall:mall mall-distribution/target/*.jar /app/app.jar

USER mall
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
