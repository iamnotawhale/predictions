package zhigalin.predictions.service.audit;

import zhigalin.predictions.model.predict.Prediction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public interface AuditService {

    List<LocalDateTime> listOfDates = new ArrayList<>();

    List<String> listOfModes = new ArrayList<>();

    List<Prediction> predictions = new ArrayList<>();

    List<Prediction> getAllPredictedAuditions();

    List<LocalDateTime> getDateOfChanges();

    List<String> getModes();

    String example();
}
