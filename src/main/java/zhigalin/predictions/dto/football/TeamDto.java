package zhigalin.predictions.dto.football;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class TeamDto {
    private Long id;
    private String teamName;
    private String code;
}
