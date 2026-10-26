package services;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class ServiceClientsTest {

    @Test
    void appointmentClientFetchesAppointmentById() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppointmentClient client = new AppointmentClient(
                builder, "http://localhost:8082/appointments/getappointment"
        );

        server.expect(requestTo(
                        "http://localhost:8082/appointments/getappointment?appointment_id=abc123"
                ))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"clinical_data\":{\"age\":50}}",
                        MediaType.APPLICATION_JSON
                ));

        Map<String, Object> appointment = client.getAppointment("abc123");

        assertEquals(Map.of("age", 50), appointment.get("clinical_data"));
        server.verify();
    }

    @Test
    void reportClientPostsReportAndReturnsStoredReport() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReportClient client = new ReportClient(
                builder, "http://localhost:8083/reports/store_report"
        );
        Map<String, Object> report = Map.of(
                "appointment_id", "abc123",
                "results", Map.of("final_diagnosis", "No CKD")
        );

        server.expect(requestTo("http://localhost:8083/reports/store_report"))
                .andExpect(method(POST))
                .andExpect(content().json(
                        "{\"appointment_id\":\"abc123\",\"results\":{\"final_diagnosis\":\"No CKD\"}}"
                ))
                .andRespond(withSuccess(
                        "{\"id\":\"report-1\"}",
                        MediaType.APPLICATION_JSON
                ));

        Map<String, Object> storedReport = client.storeReport(report);

        assertEquals("report-1", storedReport.get("id"));
        server.verify();
    }
}
