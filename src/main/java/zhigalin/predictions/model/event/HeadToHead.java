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
@Table(name = "h2h")
public class HeadToHead {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "h2h_generator")
    @SequenceGenerator(name = "h2h_generator", sequenceName = "h2h_sequence", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    private Integer homeTeamScore;

    private Integer awayTeamScore;

    private LocalDateTime localDateTime;

    private String leagueName;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HeadToHead that = (HeadToHead) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (homeTeam != null ? !homeTeam.equals(that.homeTeam) : that.homeTeam != null) return false;
        if (awayTeam != null ? !awayTeam.equals(that.awayTeam) : that.awayTeam != null) return false;
        if (homeTeamScore != null ? !homeTeamScore.equals(that.homeTeamScore) : that.homeTeamScore != null)
            return false;
        if (awayTeamScore != null ? !awayTeamScore.equals(that.awayTeamScore) : that.awayTeamScore != null)
            return false;
        if (localDateTime != null ? !localDateTime.equals(that.localDateTime) : that.localDateTime != null)
            return false;
        if (leagueName != null ? !leagueName.equals(that.leagueName) : that.leagueName != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (homeTeam != null ? homeTeam.hashCode() : 0);
        result = 31 * result + (awayTeam != null ? awayTeam.hashCode() : 0);
        result = 31 * result + (homeTeamScore != null ? homeTeamScore.hashCode() : 0);
        result = 31 * result + (awayTeamScore != null ? awayTeamScore.hashCode() : 0);
        result = 31 * result + (localDateTime != null ? localDateTime.hashCode() : 0);
        result = 31 * result + (leagueName != null ? leagueName.hashCode() : 0);
        return result;
    }
}
