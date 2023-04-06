package zhigalin.predictions.service.user;

import zhigalin.predictions.dto.user.RoleDto;

public interface RoleService {
    RoleDto save(RoleDto dto);
    RoleDto findById(Long id);
}
