package Controllers;

import services.CkdPredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/predictions")
public class PredictionController {

    private final CkdPredictionService predictionService;

    public PredictionController(CkdPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> predict(
            @RequestBody Map<String, Object> body
    ) {
        CkdPredictionService.PredictionResult result = predictionService.predict(body);
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
