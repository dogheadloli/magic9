# 多阶段构建：Jenkins / 本地均可 docker build，无需先在宿主机 mvn package
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
ARG SKIP_TESTS=true
RUN if [ "$SKIP_TESTS" = "true" ]; then mvn -B -DskipTests package; else mvn -B package; fi

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /build/target/stock-monitor.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8964
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
