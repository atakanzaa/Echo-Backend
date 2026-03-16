FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Güvenlik: root olmayan kullanıcı
RUN addgroup -S echo && adduser -S echo -G echo
USER echo

COPY --chown=echo:echo target/echo-backend-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

# Prodüksiyon JVM ayarları
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
