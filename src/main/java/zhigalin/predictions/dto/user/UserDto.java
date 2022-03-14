package zhigalin.predictions.dto.user;

import lombok.*;
import zhigalin.predictions.model.user.Role;

import java.util.Set;

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
    private Set<Role> roles;
}
