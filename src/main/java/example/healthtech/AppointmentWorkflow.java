package example.healthtech;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AppointmentWorkflow {
    private final InfraiDocumentClient client;

    public AppointmentWorkflow(InfraiDocumentClient client) { this.client = client; }

    public record Receipt(String documentId, int indexedPassages, String notification) {}
    public record Answer(String appointmentId, String question, JsonNode passages) {}

    public Receipt index(String appointmentId, String documentId, byte[] pdf)
            throws IOException, InterruptedException {
        JsonNode result = client.ocr(pdf, stableId(appointmentId + ":" + documentId));
        String extracted = result.path("text").asText("");
        if (extracted.isBlank()) throw new IllegalArgumentException("OCR returned no searchable text");
        List<String> passages = split(extracted);
        for (int i = 0; i < passages.size(); i++) {
            String passage = passages.get(i);
            client.upsert(stableId(appointmentId + ":" + documentId + ":" + i),
                    client.embed(passage), appointmentId, passage);
        }
        return new Receipt(documentId, passages.size(), notification(appointmentId, passages.size()));
    }

    public Answer ask(String appointmentId, String question) throws IOException, InterruptedException {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("question is required");
        JsonNode passages = client.query(client.embed(question), appointmentId);
        return new Answer(appointmentId, question, passages);
    }

    static String notification(String appointmentId, int count) {
        return count > 0 ? "Appointment " + appointmentId + ": document ready for staff review"
                : "Appointment " + appointmentId + ": document awaiting review";
    }

    static List<String> split(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < normalized.length(); start += 700) {
            chunks.add(normalized.substring(start, Math.min(start + 700, normalized.length())));
        }
        return chunks;
    }

    private static String stableId(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
