package zhigalin.predictions.dto.user;

import lombok.*;
import lombok.experimental.NonFinal;
import zhigalin.predictions.model.user.Role;

import java.util.Set;

@Value
@Builder
public class UserDto {

    Long id;
    String login;
    @NonFinal
    @Setter
    String password;
    Set<Role> roles;
}
