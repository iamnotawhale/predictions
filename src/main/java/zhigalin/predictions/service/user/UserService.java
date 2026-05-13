package zhigalin.predictions.service.user;

import java.util.List;

import org.springframework.stereotype.Service;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.user.UserDao;


@Service
public class UserService {
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public List<User> findAll() {
        return userDao.findAll();
    }
}
