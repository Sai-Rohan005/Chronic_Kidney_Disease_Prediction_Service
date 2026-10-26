package services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

@Component
public class ReportClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final URI endpoint;

    public ReportClient(
            RestClient.Builder restClientBuilder,
            @Value("${report.report-url}") String reportUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.endpoint = URI.create(reportUrl);
    }

    public Map<String, Object> storeReport(Map<String, Object> report) {
        return restClient.post()
                .uri(endpoint)
                .body(report)
                .retrieve()
                .body(RESPONSE_TYPE);
    }
}
