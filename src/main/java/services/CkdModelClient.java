package services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CkdModelClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Gson gson;
    private final URI tabularEndpoint;
    private final URI imageEndpoint;

    public CkdModelClient(
            @Value("${ckd-prediction.tabular-url:http://localhost:8001/routes/tab_predict}")
            String tabularUrl,
            @Value("${ckd-prediction.image-url:http://localhost:8001/routes/unet_predict}")
            String imageUrl
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.gson = new Gson();
        this.tabularEndpoint = URI.create(tabularUrl);
        this.imageEndpoint = URI.create(imageUrl);
    }

    public Map<String, Object> predictClinicalData(Map<String, Object> clinicalData) {
        return post(tabularEndpoint, Map.of("root", clinicalData));
    }

    public Map<String, Object> predictImage(String imageUrl) {
        return post(imageEndpoint, Map.of("url", imageUrl));
    }

    private Map<String, Object> post(URI endpoint, Map<String, Object> requestBody) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PredictionException("Prediction request was interrupted", exception);
        } catch (IOException exception) {
            throw new PredictionException("Prediction service could not be reached", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new PredictionException(
                    "Prediction service returned HTTP " + response.statusCode()
            );
        }
        return parsePrediction(response.body());
    }

    private Map<String, Object> parsePrediction(String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            JsonElement predictionElement = response.get("prediction");
            JsonArray probabilities = response.getAsJsonArray("probabilities");
            if (probabilities != null && probabilities.size() == 1
                    && probabilities.get(0).isJsonArray()) {
                probabilities = probabilities.get(0).getAsJsonArray();
            }
            if (predictionElement == null || !predictionElement.isJsonPrimitive()
                    || !predictionElement.getAsJsonPrimitive().isNumber()
                    || probabilities == null || probabilities.size() != 2) {
                throw new PredictionException(
                        "Prediction service returned an invalid response"
                );
            }

            int prediction = predictionElement.getAsInt();
            List<Double> probabilityValues = List.of(
                    probabilities.get(0).getAsDouble(),
                    probabilities.get(1).getAsDouble()
            );
            if (prediction != 0 && prediction != 1
                    || probabilityValues.stream().anyMatch(probability ->
                    !Double.isFinite(probability)
                            || probability < 0.0
                            || probability > 1.0)) {
                throw new PredictionException(
                        "Prediction service returned an invalid response"
                );
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("prediction", prediction);
            result.put("probabilities", probabilityValues);
            return result;
        } catch (PredictionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PredictionException(
                    "Prediction service returned an invalid response", exception
            );
        }
    }

    public static class PredictionException extends RuntimeException {

        public PredictionException(String message) {
            super(message);
        }

        public PredictionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
