package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.user.UserService;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    @GetMapping("/all")
    public List<User> findAllUsers() {
        return userService.findAll();
    }

    @GetMapping()
    public User findByLogin(@RequestParam String login) {
        return userService.findByLogin(login);
    }

    @GetMapping("/{id}")
    public User findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.ok().build();
    }
}
