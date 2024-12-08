# Build stage
FROM maven:3.8-eclipse-temurin-8 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:8-jre
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    TZ=UTC \
    SPARK_LOCAL=true \
    SPARK_MODEL_PATH="/data/wine-quality-model"

# Install required dependencies
RUN apt-get update && \
    apt-get install -y libc6 libstdc++6 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /build/target/*jar-with-dependencies.jar /app/predictor.jar
COPY run-predictor.sh /app/
RUN chmod +x /app/run-predictor.sh

ENTRYPOINT ["/bin/sh", "/app/run-predictor.sh"]