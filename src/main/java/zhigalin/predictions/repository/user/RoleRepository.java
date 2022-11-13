package zhigalin.predictions.repository.user;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.user.Role;

@Repository
public interface RoleRepository extends CrudRepository<Role, Long> {

    Role findByRole(String name);
}
