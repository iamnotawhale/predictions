package zhigalin.predictions.service.odds.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Bookmaker {

    private String key;
    private String title;
    @JsonProperty("last_update")
    private String lastUpdate;
    private List<Market> markets;
}
