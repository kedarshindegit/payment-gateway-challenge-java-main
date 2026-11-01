FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Resolving dependencies before copying the source keeps them in a cached layer, so a code
# change does not download them again.
RUN ./gradlew dependencies --no-daemon --quiet > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon --quiet

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 gateway
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER gateway
EXPOSE 8090
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
