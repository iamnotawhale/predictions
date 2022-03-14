package zhigalin.predictions.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import zhigalin.predictions.model.football.Team;

import javax.persistence.*;
import java.time.LocalDateTime;

@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "matches")
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Match_generator")
    @SequenceGenerator(sequenceName = "Match_sequence", name = "Match_generator")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchweek_id")
    private Week week;

    @OrderBy
    private LocalDateTime matchDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    private Integer homeTeamScore;

    private Integer awayTeamScore;

    private String result;

    private String status;
}
