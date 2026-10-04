package example.healthtech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiDocumentClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAIClient embeddings;
    private final String baseUrl;
    private final String key;
    private final String collection;

    public InfraiDocumentClient(@Value("${infrai.base-url}") String baseUrl,
                                @Value("${infrai.api-key}") String key,
                                @Value("${infrai.collection}") String collection) {
        this.baseUrl = baseUrl;
        this.key = key;
        this.collection = collection;
        // The same credential and host handle document processing and search.
        this.embeddings = OpenAIOkHttpClient.builder().apiKey(key).baseUrl(baseUrl + "/v1").build();
    }

    public JsonNode ocr(byte[] pdf, String operationId) throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("pdf", Base64.getEncoder().encodeToString(pdf));
        payload.put("idempotency_key", operationId);
        return send("/v1/pdf/ocr", HttpRequest.BodyPublishers.ofString(payload.toString()),
                "application/json", null);
    }

    public List<Float> embed(String text) {
        return embeddings.embeddings().create(EmbeddingCreateParams.builder()
                .model("text-embedding-3-small").input(text).build()).data().get(0).embedding();
    }

    public void upsert(String id, List<Float> values, String appointmentId, String excerpt)
            throws IOException, InterruptedException {
        ObjectNode vector = mapper.createObjectNode();
        vector.put("id", id);
        vector.set("values", mapper.valueToTree(values));
        ObjectNode metadata = vector.putObject("metadata");
        metadata.put("appointment_id", appointmentId);
        metadata.put("excerpt", excerpt);
        ObjectNode payload = mapper.createObjectNode();
        payload.put("collection", collection);
        payload.putArray("vectors").add(vector);
        send("/v1/vector/upsert", HttpRequest.BodyPublishers.ofString(payload.toString()),
                "application/json", id);
    }

    public JsonNode query(List<Float> embedding, String appointmentId) throws IOException, InterruptedException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("collection", collection);
        payload.set("embedding", mapper.valueToTree(embedding));
        payload.put("top_k", 3);
        payload.put("include_metadata", true);
        payload.putObject("filter").put("appointment_id", appointmentId);
        return send("/v1/vector/query", HttpRequest.BodyPublishers.ofString(payload.toString()),
                "application/json", null);
    }

    private JsonNode send(String path, HttpRequest.BodyPublisher body, String contentType, String idempotencyKey)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", contentType).method("POST", body);
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = mapper.readTree(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                long delay = response.headers().firstValue("Retry-After").map(this::retrySeconds)
                        .orElse(1L << attempt);
                Thread.sleep(Math.min(delay, 30) * 1000);
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                throw new InfraiRejection(response.statusCode(), error.path("code").asText("REQUEST_REJECTED"), error.toString());
            }
            if (response.statusCode() >= 500) throw new IOException("Upstream response: " + response.statusCode());
            return envelope.path("data");
        }
        throw new IOException("Retry budget exhausted");
    }

    private long retrySeconds(String header) {
        try { return Math.max(1, Long.parseLong(header)); }
        catch (NumberFormatException ignored) { return 1; }
    }

    public static class InfraiRejection extends RuntimeException {
        public final int status;
        public final String code;
        InfraiRejection(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
