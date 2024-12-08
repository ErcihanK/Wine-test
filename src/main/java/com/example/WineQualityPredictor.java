package com.example;

import org.apache.spark.ml.PipelineModel;
import org.apache.spark.mllib.evaluation.MulticlassMetrics;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.*;
import org.apache.spark.api.java.JavaRDD;
import scala.Tuple2;
import org.apache.spark.api.java.function.Function;
import java.nio.file.Paths;

public class WineQualityPredictor {
    private static final String awsRegion = "us-east-1";
    private static final boolean isLocal = true; // Force local mode
    private static final String LOCAL_MODEL_PATH = "/data/wine-quality-model"; // Update to use Docker path
    private static final String[] MODEL_SEARCH_PATHS = {
        "/data/wine-quality-model",         // Docker mounted volume
        "./data/wine-quality-model",        // Local directory
        "wine-quality-model"               // Fallback
    };

    private static String getModelPath() {
        if (!isLocal) {
            return String.format("s3a://cs643-wine-quality-datasets/%s/wine-quality-model", awsRegion);
        }
        
        // Search for model in multiple locations
        for (String path : MODEL_SEARCH_PATHS) {
            if (path != null && !path.isEmpty()) {
                java.io.File modelDir = new java.io.File(path);
                if (modelDir.exists()) {
                    System.out.println("Found model at: " + path);
                    return "file://" + modelDir.getAbsolutePath();
                } else {
                    System.out.println("Checked path: " + path + " (not found)");
                }
            }
        }
        throw new RuntimeException("Model not found in any of the search paths");
    }

    private static Dataset<Row> loadData(SparkSession spark, String path, StructType schema) {
        String dataPath = path;
        if (!path.startsWith("file://") && !path.startsWith("s3a://")) {
            java.io.File inputFile = new java.io.File(path);
            if (!inputFile.exists()) {
                System.err.println("Input file not found: " + path);
                System.err.println("Working directory: " + System.getProperty("user.dir"));
                System.err.println("Attempting to list /data directory:");
                java.io.File dataDir = new java.io.File("/data");
                if (dataDir.exists() && dataDir.isDirectory()) {
                    for (String file : dataDir.list()) {
                        System.err.println("- " + file);
                    }
                }
                throw new RuntimeException("Input file not found: " + path);
            }
            dataPath = "file://" + inputFile.getAbsolutePath();
        }
        System.out.println("Loading data from: " + dataPath);
        return spark.read()
                   .option("header", "true")
                   .option("delimiter", ";")
                   .option("quote", "\"")
                   .schema(schema)
                   .csv(dataPath)
                   .coalesce(1);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Please provide the test dataset path");
            System.exit(1);
        }

        String testDataPath = args[0];

        // Initialize Spark session with simplified configuration
        SparkSession.Builder sparkBuilder = SparkSession.builder()
            .appName("Wine Quality Predictor")
            .master("local[1]")
            .config("spark.driver.memory", "750m");

        // Add memory optimizations only
        SparkSession spark = sparkBuilder
            .config("spark.memory.fraction", "0.8")
            .config("spark.memory.storageFraction", "0.3")
            .config("spark.driver.maxResultSize", "500m")
            .config("spark.sql.shuffle.partitions", "1")
            .config("spark.rdd.compress", "true")
            .config("spark.shuffle.compress", "true")
            .config("spark.memory.offHeap.enabled", "false")
            .config("spark.cleaner.periodicGC.interval", "1min")
            .getOrCreate();

        // Define schema
        StructType schema = new StructType(new StructField[] {
            new StructField("fixed_acidity", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("volatile_acidity", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("citric_acid", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("residual_sugar", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("chlorides", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("free_sulfur_dioxide", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("total_sulfur_dioxide", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("density", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("pH", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("sulphates", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("alcohol", DataTypes.DoubleType, false, Metadata.empty()),
            new StructField("quality", DataTypes.DoubleType, false, Metadata.empty())
        });

        try {
            // Load the model using dynamic path
            PipelineModel model = PipelineModel.load(getModelPath());

            // Load test data using the new loadData method
            Dataset<Row> testData = loadData(spark, args[0], schema);

            // Make predictions with memory management
            Dataset<Row> predictions = model.transform(testData).cache();
            predictions.count(); // Materialize cache

            // Calculate F1 Score - Fixed RDD conversion
            JavaRDD<Tuple2<Double, Double>> predictionAndLabels = predictions
                .select("prediction", "quality")
                .javaRDD()
                .map((Function<Row, Tuple2<Double, Double>>) row ->
                    new Tuple2<>(row.getDouble(0), row.getDouble(1))
                );

            MulticlassMetrics metrics = new MulticlassMetrics(predictionAndLabels.rdd());
            double f1Score = metrics.weightedFMeasure();
            System.out.println("Weighted F1 Score on test data: " + f1Score);

        } catch (Exception e) {
            System.err.println("Error in prediction: " + e.getMessage());
            e.printStackTrace();
        } finally {
            spark.stop();
        }
    }
}