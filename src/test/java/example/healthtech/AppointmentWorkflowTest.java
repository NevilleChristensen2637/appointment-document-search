package example.healthtech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AppointmentWorkflowTest {
    @Test
    void operationalNotificationDoesNotExposeOcrContent() {
        String patientText = "Patient Jane Doe: diagnosis in scanned note";
        String notification = AppointmentWorkflow.notification("visit-104", AppointmentWorkflow.split(patientText).size());
        assertEquals("Appointment visit-104: document ready for staff review", notification);
        assertFalse(notification.contains("Jane Doe"));
        assertFalse(notification.contains("diagnosis"));
        assertTrue(AppointmentWorkflow.split(patientText).get(0).contains("diagnosis"));
    }
}
