package zhigalin.predictions.service._impl.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.user.RoleMapper;
import zhigalin.predictions.dto.user.RoleDto;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.repository.user.RoleRepository;
import zhigalin.predictions.service.user.RoleService;

@RequiredArgsConstructor
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository repository;
    private final RoleMapper mapper;

    @Override
    public RoleDto save(RoleDto dto) {
        Role role = repository.findByRole(dto.getRole());
        if (role != null) {
            return mapper.toDto(role);
        }
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public RoleDto findById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }
}
