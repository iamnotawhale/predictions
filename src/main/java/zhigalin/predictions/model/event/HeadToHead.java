package zhigalin.predictions.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import zhigalin.predictions.model.football.Team;

import javax.persistence.*;
import java.time.LocalDateTime;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer"})
@Table(name = "h2h")
public class HeadToHead {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "h2h_generator")
    @SequenceGenerator(name = "h2h_generator", sequenceName = "h2h_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", referencedColumnName = "id")
    private Team homeTeam;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", referencedColumnName = "id")
    private Team awayTeam;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private LocalDateTime localDateTime;
    private String leagueName;
}
