FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B clean package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S forum && adduser -S forum -G forum
WORKDIR /app
COPY --from=build /workspace/target/rebuilt-zijing-forum-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/uploads && chown -R forum:forum /app
USER forum
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/api/health >/dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
