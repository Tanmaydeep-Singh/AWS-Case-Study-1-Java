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
