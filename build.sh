
#!/bin/bash

# Build the JAR file
mvn clean package -DskipTests

# Check if JAR was built successfully
if [ ! -f "target/wine-quality-prediction-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    echo "Error: JAR file not built successfully"
    exit 1
fi

# Build Docker image
docker build -t wine-predictor .