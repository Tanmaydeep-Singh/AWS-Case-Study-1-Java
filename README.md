# 🧠 AWS Case Study 1 — S3 File Processing with Lambda and DynamoDB

This project demonstrates how to build an **automated text file processing system** using **AWS Lambda**, **S3**, and **DynamoDB**.  
Whenever a `.txt` file is uploaded to an S3 bucket, the **Lambda function** is triggered to:
- Read the file contents,
- Count **lines**, **words**, and **characters**,
- Extract a **100-character preview**, and
- Store the processing results in a **DynamoDB table**.

---

## 🧩 Project Overview

| Component | Description |
|------------|-------------|
| **S3 Bucket** | Stores uploaded `.txt` files |
| **Lambda Function** | Processes text files and sends results to DynamoDB |
| **DynamoDB Table** | Stores file analysis results |
| **CloudWatch Logs** | Logs execution details and errors |

---

## 📁 Architecture Flow

1. User uploads a text file (`.txt`) to S3.
2. The **S3 Event Trigger** invokes the **Lambda function**.
3. Lambda reads the file from S3 using the AWS SDK.
4. Lambda counts lines, words, and characters, and extracts a preview.
5. Lambda stores results in **DynamoDB**.
6. Logs are written to **CloudWatch** for monitoring.

---

## ⚙️ Step-by-Step Setup Guide

### **Step 1: Create S3 Bucket**
1. Go to **AWS Console → S3 → Create Bucket**.
2. Name it: `file-processing-bucket-<your-name>`.
3. Keep all default settings and create the bucket.

 ![Screensot Diagram](./Images/ScreenShot-1.png)


### **Step 2: Configure S3 Event Notification**
1. Open your S3 bucket → **Properties** → scroll to **Event Notifications**.
2. Click **Create event notification**:
   - Name: `LambdaTriggerEvent`
   - Event type: `All object create events`
   - Suffix filter: `.txt`
   - Destination: Lambda function (`TextFileProcessor`)
3. Save changes.

  ![Screensot Diagram](./Images/ScreenShot-4.png)


---

### **Step 3: Create DynamoDB Table**
1. Go to **DynamoDB → Create table**.
2. Table name: `FileProcessingResults`
3. Partition key: `fileName (String)`
4. Click **Create Table**.

 ![Screensot Diagram](./Images/ScreenShot-3.png)




---

### **Step 4: Create the Lambda Function**

#### **Option 1: Node.js Version**
If you prefer Node.js 22 runtime, use the following code:

  ![Screensot Diagram](./Images/ScreenShot-2.png)
   ![Screensot Diagram](./Images/ScreenShot-7.png)


```Java


   package com;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.Instant;

public class S3FileProcessorHandler implements RequestHandler<S3Event, String> {

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
    private final AmazonDynamoDB dynamoClient = AmazonDynamoDBClientBuilder.standard().build();

    @Override
    public String handleRequest(S3Event event, Context context) {
        context.getLogger().log("Event received: " + event.toString());

        try {
            if (event.getRecords() == null || event.getRecords().isEmpty()) {
                context.getLogger().log("⚠️ No S3 records found in event.\n");
                return "{\"message\": \"No records to process.\"}";
            }

            // 1️⃣ Get S3 bucket and key from event
            String bucket = event.getRecords().get(0).getS3().getBucket().getName();
            String key = event.getRecords().get(0).getS3().getObject().getKey();
            key = java.net.URLDecoder.decode(key.replace("+", " "), StandardCharsets.UTF_8);

            context.getLogger().log("Processing file: " + key + " from bucket: " + bucket);

            // 2️⃣ Read file content from S3
            S3Object s3Object = s3Client.getObject(bucket, key);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s3Object.getObjectContent(), StandardCharsets.UTF_8));

            StringBuilder contentBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }

            String fileContent = contentBuilder.toString();

            // 3️⃣ Process text: count lines, words, and characters
            int lineCount = fileContent.split("\\r?\\n").length;
            int wordCount = fileContent.trim().isEmpty() ? 0 : fileContent.trim().split("\\s+").length;
            int charCount = fileContent.length();
            String preview = fileContent.length() > 100 ? fileContent.substring(0, 100) : fileContent;

            // 4️⃣ Save results into DynamoDB
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("fileName", new AttributeValue(key));
            item.put("lineCount", new AttributeValue().withN(Integer.toString(lineCount)));
            item.put("wordCount", new AttributeValue().withN(Integer.toString(wordCount)));
            item.put("charCount", new AttributeValue().withN(Integer.toString(charCount)));
            item.put("preview", new AttributeValue(preview));
            item.put("processedAt", new AttributeValue(Instant.now().toString()));

            PutItemRequest request = new PutItemRequest()
                    .withTableName("FileProcessingResults")
                    .withItem(item);

            dynamoClient.putItem(request);
            context.getLogger().log("✅ Data saved to DynamoDB successfully.");

            // 5️⃣ Return success response
            return String.format(
                    "{ \"message\": \"File processed successfully\", \"fileName\": \"%s\", \"lineCount\": %d, \"wordCount\": %d, \"charCount\": %d }",
                    key, lineCount, wordCount, charCount);

        } catch (Exception e) {
            context.getLogger().log("❌ Error processing file: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}


```

```pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com</groupId>
    <artifactId>fileprocessor</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- AWS Lambda Core -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-core</artifactId>
            <version>1.2.3</version>
        </dependency>

        <!-- AWS Lambda Events -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-events</artifactId>
            <version>3.11.2</version>
        </dependency>

        <!-- AWS SDK for S3 -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-java-sdk-s3</artifactId>
            <version>1.12.774</version>
        </dependency>

        <!-- AWS SDK for DynamoDB -->
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-java-sdk-dynamodb</artifactId>
            <version>1.12.774</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.3.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <transformers>
                                <transformer
                                    implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.example.S3FileProcessorHandler</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>


</project>

```
### **Step 5: Test the Setup**

Upload a file named sample.txt to your S3 bucket.

Wait for the Lambda to trigger automatically.

Check:

CloudWatch Logs → to verify execution success.

DynamoDB → new entry created with file statistics.

### **Step 6: Result**

)
  ![Screensot Diagram](./Images/ScreenShot-5.png)
 ![Screensot Diagram](./Images/ScreenShot-6.png)
