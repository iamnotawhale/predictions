package zhigalin.predictions.dto.event;

import lombok.*;
import zhigalin.predictions.model.football.Team;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class StatsDto {
    private Long id;
    private Long matchPublicId;
    private Team team;
    private Integer possessionPercent;
    private Integer shots; //всего ударов
    private Integer shotsOnTarget; //удары в створ ворот
    private Integer shotsOffTarget; //удары мимо ворот
    private Integer shotsBlocked; //отбитые мячи
    private Integer corners;
    private Integer offsides;
    private Integer freeKick; //свободные удары
    private Integer fouls;
    private Integer throwIn; //вбрасывания
    private Integer goalKick; //удары от ворот
    private Integer ballSafe;
    private Integer yellowCards;
    private Integer yellowRedCards;
    private Integer redCards;
}
