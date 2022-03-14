package zhigalin.predictions.dto.user;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class RoleDto {
    private Long id;
    private String role;
}
