package zhigalin.predictions.dto.user;

import lombok.*;
import zhigalin.predictions.model.user.Role;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class UserDto {
    private Long id;
    private String login;
    private String password;
    private Role role;
}
