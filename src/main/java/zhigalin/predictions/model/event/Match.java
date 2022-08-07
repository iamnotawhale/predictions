package zhigalin.predictions.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "match")
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "Match_generator")
    @SequenceGenerator(sequenceName = "Match_sequence", name = "Match_generator", allocationSize = 1)
    private Long id;

    private Long publicId;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinColumn(name = "matchweek_id")
    private Week week;

    private LocalDateTime localDateTime;

    private LocalDate matchDate;

    private LocalTime matchTime;

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

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL)
    private Set<Prediction> predictions;
}
