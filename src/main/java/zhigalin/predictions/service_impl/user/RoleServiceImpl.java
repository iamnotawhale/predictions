package zhigalin.predictions.service_impl.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.user.RoleMapper;
import zhigalin.predictions.dto.user.RoleDto;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.repository.user.RoleRepository;
import zhigalin.predictions.service.user.RoleService;

@Service
public class RoleServiceImpl implements RoleService {
    @Autowired
    private RoleRepository repository;
    @Autowired
    private RoleMapper mapper;

    @Override
    public RoleDto save(RoleDto dto) {
        Role savedRole = repository.save(mapper.toEntity(dto));
        return mapper.toDto(savedRole);
    }

    @Override
    public RoleDto findById(Long id) {
        return mapper.toDto(repository.findById(id).get());
    }
}
