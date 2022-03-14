package zhigalin.predictions.converter.user;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;

@Mapper(componentModel = "spring")
public interface UserMapper extends CustomMapper<User, UserDto> {

}
