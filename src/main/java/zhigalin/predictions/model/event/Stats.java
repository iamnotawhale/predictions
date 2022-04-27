package zhigalin.predictions.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import zhigalin.predictions.model.football.Team;

import javax.persistence.*;

@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "stats")
public class Stats {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Stats_generator")
    @SequenceGenerator(sequenceName = "Stats_sequence", name = "Stats_generator", allocationSize = 1)
    private Long id;

    private Long matchPublicId;

    @OneToOne
    @JoinColumn(name = "team_id")
    private Team team;

    private Integer possessionPercent;

    //attack
    private Integer shots; //всего ударов

    private Integer shotsOnTarget; //удары в створ ворот

    private Integer shotsOffTarget; //удары мимо ворот

    private Integer shotsBlocked; //отбитые мячи

    private Integer corners;

    private Integer offsides;

    private Integer freeKick; //свободные удары

    //def

    private Integer fouls;

    private Integer throwIn; //вбрасывания

    private Integer goalKick; //удары от ворот

    private Integer ballSafe;


    //cards

    private Integer yellowCards;

    private Integer yellowRedCards;

    private Integer redCards;
}
