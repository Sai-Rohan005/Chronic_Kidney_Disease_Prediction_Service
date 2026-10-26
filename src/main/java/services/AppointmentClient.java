package services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Component
public class AppointmentClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final URI endpoint;

    public AppointmentClient(
            RestClient.Builder restClientBuilder,
            @Value("${appointment.appointment-url}") String appointmentUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.endpoint = URI.create(appointmentUrl);
    }

    public Map<String, Object> getAppointment(String appointmentId) {
        URI requestUri = UriComponentsBuilder.fromUri(endpoint)
                .queryParam("appointment_id", appointmentId)
                .build()
                .encode()
                .toUri();
        return restClient.get()
                .uri(requestUri)
                .retrieve()
                .body(RESPONSE_TYPE);
    }
}
