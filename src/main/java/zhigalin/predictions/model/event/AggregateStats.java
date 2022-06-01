package zhigalin.predictions.model.event;

import lombok.*;

@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AggregateStats {
    private Long teamId;
    private Double possessionPercent;
    //attack
    private Double shots; //всего ударов
    private Double shotsOnTarget; //удары в створ ворот
    private Double shotsOffTarget; //удары мимо ворот
    private Double shotsBlocked; //отбитые мячи
    private Double corners;
    private Double offsides;
    private Double passes;
    private Double passesAccurate;
    private Double passPercent;
    private Double insideBoxShots;
    private Double outsideBoxShots;
    //private Double freeKick; //свободные удары
    //def
    private Double fouls;
    //private Double throwIn; //вбрасывания
    //private Double goalKick; //удары от ворот
    private Double ballSafe;
    //cards
    private Double yellowCards;
    //private Double yellowRedCards;
    private Double redCards;
}
