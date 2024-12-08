#!/bin/sh

# Always set local environment for this case
export SPARK_LOCAL=true

# Default to validation dataset if no input provided
if [ -z "$1" ]; then


    INPUT_PATH="C:/Users/eric1/Downloads/
    -wine-quality-datasets/ValidationDataset.csv"
else
    INPUT_PATH="$1"
fi

exec java $JAVA_OPTS -jar predictor.jar "$INPUT_PATH"