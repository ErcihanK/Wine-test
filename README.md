
# Wine Quality Prediction with Apache Spark on AWS

This project demonstrates how to use **Apache Spark** on **AWS EC2** to develop a parallel machine learning application for predicting wine quality. The model is trained in parallel across multiple EC2 instances, and the trained model is then used for wine quality prediction.

## Prerequisites

Before you can run this assignment, ensure you have the following:

### Software Requirements:
- **Java 8 or higher**
- **Apache Spark 3.5.3** with **MLlib** (Machine Learning Library)
- **AWS CLI** installed and configured with access to the appropriate AWS resources.
- **Maven** for building the project.
- **Docker** for containerizing the prediction application.
  
### AWS Setup:
- **4 EC2 instances** for parallel model training.
- **S3 bucket** to store the training, validation, and model data.
- **Docker Hub** account for hosting the Docker image.

## Setting Up AWS Environment

### Step 1: Set Up 4 EC2 Instances

1. **Launch 4 EC2 instances** (t2.micro or larger depending on your needs).
   - Ensure they are in the **same VPC** and can communicate with each other (e.g., through private IP addresses).
   - Assign appropriate **security group rules** to allow communication:
     - Port 7077 for Spark master communication
     - Port 22 for SSH access

2. **Install Dependencies on All EC2 Instances:**
   - **Java**: Install Java 8 or above.
     ```bash
     sudo apt update
     sudo apt install openjdk-8-jdk
     ```
   - **Apache Spark**: Install Apache Spark following official documentation.

3. **Configure AWS CLI** on each EC2 instance.
   ```bash
   aws configure
   ```
   Enter your AWS Access Key, Secret Access Key, region, and output format.

### Step 2: Set Up Spark Cluster

1. **On the Master Node**:
   - Start the **Spark master** using the following command:
   ```bash
   start-master.sh
   ```

2. **On Worker Nodes**:
   - Start the **Spark worker** and point it to the master:
   ```bash
   start-slave.sh spark://<master-node-ip>:7077
   ```
   Replace `<master-node-ip>` with the private IP of your master node.

Now, your Spark cluster should be up and running.

## Model Training

### Step 1: Clone the Repository

Clone the GitHub repository to your EC2 master node:

```bash
git clone https://github.com/YourGitHubUsername/wine-quality-prediction.git
cd wine-quality-prediction/wine-quality-prediction
```

### Step 2: Build the Project

To build the project, use **Maven**:

```bash
mvn clean package
```

This will compile the code and create a `.jar` file (`target/wine-quality-prediction-1.0-SNAPSHOT.jar`).

### Step 3: Run the Model Training Job

Submit the training job to Spark. On the **master node**, run:

```bash
spark-submit --class com.example.WineQualityTrainer \
  --master spark://<master-node-ip>:7077 \
  --deploy-mode cluster \
  --executor-memory 2G \
  --total-executor-cores 4 \
  --conf spark.executor.instances=3 \
  --conf spark.sql.shuffle.partitions=12 \
  target/wine-quality-prediction-1.0-SNAPSHOT.jar
```

This will start training the model in parallel on the EC2 instances. The trained model will be saved to an **S3 bucket**.

## Running the Prediction Application

### Without Docker

1. **Ensure the trained model is stored in your S3 bucket**. The trained model should be located at:
   ```
   s3://cs643-wine-quality-datasets/wine-quality-model/
   ```

2. **Submit the prediction job** on your EC2 instance using the following `spark-submit` command:
   ```bash
   spark-submit --class com.example.PredictionApp \
     --master spark://<master-node-ip>:7077 \
     --deploy-mode client \
     --executor-memory 2G \
     --total-executor-cores 4 \
     --conf spark.executor.instances=1 \
     target/wine-quality-prediction-1.0-SNAPSHOT.jar
   ```

3. The application will load the trained model from the S3 bucket and make predictions on the **TestDataset.csv**.

### With Docker

1. **Build the Docker Image** for the prediction application:
   ```bash
   docker build -t wine-quality-prediction .
   ```

2. **Run the Docker container** with the necessary data files and the trained model:
   ```bash
   docker run -v /path/to/data:/app/data wine-quality-prediction
   ```
   This mounts your local data directory to the Docker container, allowing you to perform predictions with the trained model.

3. The predictions will be output inside the container. Make sure to replace `/path/to/data` with the actual path where your test data and model are stored.

## Docker Hub Repository
You can access the Dockerized version of the prediction application at the following Docker Hub link:

[Docker Hub Repository - Wine Quality Prediction](https://hub.docker.com/repository/docker/ercihankorkmaz/wine-predictor)
