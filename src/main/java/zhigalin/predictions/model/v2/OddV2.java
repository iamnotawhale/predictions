package zhigalin.predictions.model.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OddV2 {
    private Provider provider;
    private OddStatV2 homeTeamOdds;
    private OddStatV2 awayTeamOdds;
    private OddStatV2 drawOdds;
    private Moneyline moneyline;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Moneyline {
        private MoneylineEntry home;
        private MoneylineEntry away;
        private MoneylineEntry draw;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MoneylineEntry {
        private MoneylineOdds open;
        private MoneylineOdds close;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MoneylineOdds {
        private String odds;
    }
}
