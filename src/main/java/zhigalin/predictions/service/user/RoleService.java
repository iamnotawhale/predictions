package zhigalin.predictions.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.user.Role;
import zhigalin.predictions.repository.user.RoleRepository;

@RequiredArgsConstructor
@Service
public class RoleService {
    private final RoleRepository repository;

    public Role save(Role role) {
        Role roleFromDB = repository.findByRole(role.getRole());
        if (roleFromDB != null) {
            return roleFromDB;
        }
        return repository.save(role);
    }

    public Role findById(Long id) {
        return repository.findById(id).orElse(null);
    }
}
