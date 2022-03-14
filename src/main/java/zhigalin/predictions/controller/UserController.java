package zhigalin.predictions.controller;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.service.user.UserService;

import java.util.List;

@RestController
@RequestMapping("/user")
@AllArgsConstructor
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/save")
    public UserDto saveUser(@RequestBody UserDto userDto) {
        return userService.saveUser(userDto);
    }

    @GetMapping("/all")
    public List<UserDto> findAllUsers() {
        return userService.getAll();
    }

    @GetMapping()
    public UserDto findByLogin(@RequestParam String login) {
        return userService.getByLogin(login);
    }

    @GetMapping("/{id}")
    public UserDto findById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.ok().build();
    }
}
