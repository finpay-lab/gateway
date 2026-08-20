# FinPay service runtime image.
# The bootJar is built OUTSIDE this image (via the Gradle image, e.g.
#   docker run --rm -v "$PWD":/work -w /work -v gradle-cache:/root/.gradle \
#     gradle:8.14.5-jdk21 gradle bootJar --no-daemon
# ) and copied in here so the build stays reproducible and fast.
FROM eclipse-temurin:21-jre-ubi9-minimal
WORKDIR /app
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
HEALTHCHECK CMD wget -q -O- http://localhost:${SERVER_PORT:-8080}/actuator/health/liveness || exit 1
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
