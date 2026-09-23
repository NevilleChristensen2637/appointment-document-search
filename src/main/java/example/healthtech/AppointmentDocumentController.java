package example.healthtech;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/appointments")
public class AppointmentDocumentController {
    private final AppointmentWorkflow workflow;

    public AppointmentDocumentController(AppointmentWorkflow workflow) { this.workflow = workflow; }

    @PostMapping(path = "/documents", consumes = "multipart/form-data")
    public AppointmentWorkflow.Receipt index(@RequestParam String appointmentId,
                                               @RequestParam String documentId,
                                               @RequestParam MultipartFile pdf)
            throws IOException, InterruptedException {
        if (appointmentId.isBlank() || documentId.isBlank() || pdf.isEmpty())
            throw new IllegalArgumentException("appointmentId, documentId and pdf are required");
        return workflow.index(appointmentId, documentId, pdf.getBytes());
    }

    @PostMapping(path = "/questions")
    public AppointmentWorkflow.Answer ask(@RequestParam String appointmentId, @RequestParam String question)
            throws IOException, InterruptedException {
        if (appointmentId.isBlank()) throw new IllegalArgumentException("appointmentId is required");
        return workflow.ask(appointmentId, question);
    }

    @ExceptionHandler(InfraiDocumentClient.InfraiRejection.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiDocumentClient.InfraiRejection error) {
        int status = error.status >= 400 && error.status < 500 ? error.status : 502;
        return ResponseEntity.status(status).body(Map.of("code", error.code, "message", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", error.getMessage()));
    }
}
