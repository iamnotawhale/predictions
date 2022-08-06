package zhigalin.predictions.repository.user;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zhigalin.predictions.model.user.User;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    User findByLogin(String login);
    Optional<User> findById(Long id);
    @Query("SELECT sum(points) FROM Prediction where Prediction.user.id = :#{#id}")
    Integer getPointsByUserId(@Param("id")Long id);
}
