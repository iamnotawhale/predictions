package zhigalin.predictions.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {
    private final UserRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info(Thread.currentThread().getStackTrace()[1].getMethodName());
        User user = repository.findByLogin(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return UserDetailsImpl.build(user);
    }
}
