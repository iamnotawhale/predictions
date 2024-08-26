package zhigalin.predictions.service.odds.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Market {

    private String key;
    @JsonProperty("last_update")
    private String lastUpdate;
    private List<Outcome> outcomes;
}
