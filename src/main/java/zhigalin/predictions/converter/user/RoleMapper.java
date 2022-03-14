package zhigalin.predictions.converter.user;

import org.mapstruct.Mapper;
import zhigalin.predictions.converter.CustomMapper;
import zhigalin.predictions.dto.user.RoleDto;
import zhigalin.predictions.model.user.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper extends CustomMapper<Role, RoleDto> {
}
