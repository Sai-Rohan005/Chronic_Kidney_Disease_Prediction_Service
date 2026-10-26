package services;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import services.CkdModelClient.PredictionException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CkdPredictionService {

    private static final Logger logger =
            LoggerFactory.getLogger(CkdPredictionService.class);
    private static final double DECISION_THRESHOLD = 0.5;

    private final AppointmentClient appointmentClient;
    private final ReportClient reportClient;
    private final CkdModelClient modelClient;

    public CkdPredictionService(
            AppointmentClient appointmentClient,
            ReportClient reportClient,
            CkdModelClient modelClient
    ) {
        this.appointmentClient = appointmentClient;
        this.reportClient = reportClient;
        this.modelClient = modelClient;
    }

    public PredictionResult predict(Map<String, Object> body) {
        String appointmentId = body == null
                ? null : stringValue(body.get("appointment_id"));
        if (!ObjectId.isValid(appointmentId)) {
            return failure(400, "A valid appointment_id is required");
        }

        Map<String, Object> appointment;
        try {
            appointment = appointmentClient.getAppointment(appointmentId);
        } catch (RestClientException exception) {
            logger.error("Could not retrieve appointment {}", appointmentId, exception);
            return failure(502, "Appointment service is unavailable or returned an invalid response");
        }
        if (appointment == null) {
            return failure(404, "Appointment not found");
        }

        Object clinicalValue = appointment.get("clinical_data");
        if (!(clinicalValue instanceof Map<?, ?> clinicalData)) {
            return failure(400, "Appointment has no valid clinical_data");
        }

        List<String> imageUrls = imageUrls(appointment.get("ultrasound_data"));
        if (imageUrls == null || imageUrls.isEmpty()) {
            return failure(400, "Appointment has no valid ultrasound images");
        }

        try {
            Map<String, Object> tabularPrediction =
                    modelClient.predictClinicalData(toStringKeyMap(clinicalData));

            List<Map<String, Object>> imagePredictions = new ArrayList<>();
            double imageCkdProbability = 0.0;
            for (String imageUrl : imageUrls) {
                Map<String, Object> imagePrediction = modelClient.predictImage(imageUrl);
                imagePredictions.add(imagePrediction);
                imageCkdProbability += ckdProbability(imagePrediction);
            }
            imageCkdProbability /= imagePredictions.size();

            double tabularCkdProbability = ckdProbability(tabularPrediction);
            double combinedCkdProbability =
                    (tabularCkdProbability + imageCkdProbability) / 2.0;
            int combinedPrediction =
                    combinedCkdProbability >= DECISION_THRESHOLD ? 1 : 0;

            Map<String, Object> imageSummary = new LinkedHashMap<>();
            imageSummary.put("prediction",
                    imageCkdProbability >= DECISION_THRESHOLD ? 1 : 0);
            imageSummary.put("probabilities", List.of(
                    1.0 - imageCkdProbability,
                    imageCkdProbability
            ));
            imageSummary.put("per_image", imagePredictions);

            Map<String, Object> results = new LinkedHashMap<>();
            results.put("final_diagnosis",
                    combinedPrediction == 1 ? "CKD" : "No CKD");
            results.put("combined_probability", combinedCkdProbability);
            results.put("threshold", DECISION_THRESHOLD);

            Map<String, Object> reportData = new LinkedHashMap<>();
            reportData.put("appointment_id", appointmentId);
            reportData.put("tabres", tabularPrediction);
            reportData.put("unetRes", imageSummary);
            reportData.put("results", results);

            Map<String, Object> report;
            try {
                report = reportClient.storeReport(reportData);
            } catch (RestClientException exception) {
                logger.error("Could not store prediction report for appointment {}", appointmentId, exception);
                return failure(502, "Report service is unavailable or returned an invalid response");
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "CKD prediction completed successfully");
            response.put("report", report);
            return new PredictionResult(200, response);
        } catch (PredictionException exception) {
            logger.error("CKD prediction failed for appointment {}", appointmentId, exception);
            return failure(502, "CKD prediction service is unavailable or returned invalid data");
        }
    }

    private static double ckdProbability(Map<String, Object> prediction) {
        Object value = prediction.get("probabilities");
        if (!(value instanceof List<?> probabilities) || probabilities.size() != 2
                || !(probabilities.get(1) instanceof Number probability)) {
            throw new PredictionException("Prediction service returned invalid probabilities");
        }
        double result = probability.doubleValue();
        if (!Double.isFinite(result) || result < 0.0 || result > 1.0) {
            throw new PredictionException("Prediction service returned invalid probabilities");
        }
        return result;
    }

    private static List<String> imageUrls(Object value) {
        if (!(value instanceof List<?> values)) {
            return null;
        }
        List<String> urls = new ArrayList<>(values.size());
        for (Object image : values) {
            if (!(image instanceof String url) || url.isBlank()) {
                return null;
            }
            urls.add(url);
        }
        return urls;
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                result.put(stringKey, value);
            }
        });
        return result;
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string.trim() : null;
    }

    private static PredictionResult failure(int status, String message) {
        return new PredictionResult(status, Map.of("message", message));
    }

    public record PredictionResult(int status, Map<String, Object> body) {}
}
