package zhigalin.predictions.service_impl.audit;

import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.service.audit.AuditService;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class AuditServiceImpl implements AuditService {

    DateFormat formatter = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Prediction> getAllPredictedAuditions() {
        LocalDateTime localDateTime = null;

        AuditQuery query = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(Prediction.class, false, true);

        List<Object[]> revision = (List<Object[]>) query.getResultList();

        for (Object[] objects : revision) {
            predictions.add((Prediction) objects[0]);
            DefaultRevisionEntity defaultRevisionEntity = (DefaultRevisionEntity) objects[1];
            try {
                listOfDates.add(formatter.parse(defaultRevisionEntity.getRevisionDate().toString())
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime());
            } catch (ParseException e) {
                e.printStackTrace();
            }
            RevisionType revisionType = (RevisionType) objects[2];
            listOfModes.add(revisionType.name());
        }
        Collections.reverse(predictions);
        Collections.reverse(listOfDates);
        Collections.reverse(listOfModes);

        return predictions;
    }

    @Override
    public List<LocalDateTime> getDateOfChanges() {
        return listOfDates;
    }

    @Override
    public List<String> getModes() {
        return listOfModes;
    }


    @Override
    public String example() {
        AuditQuery query = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(Prediction.class, false, true);

        List<Object[]> revision = (List<Object[]>) query.getResultList();
        return revision.get(2)[0].toString() + " ||| " + revision.get(2)[1].toString() + " |||| " + revision.get(2)[2].toString();
    }
}
