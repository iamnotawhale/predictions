//package zhigalin.predictions.controller;
//
//import java.util.List;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//import zhigalin.predictions.model.user.User;
//import zhigalin.predictions.service.user.UserService;
//
//@RequiredArgsConstructor
//@RestController
//@RequestMapping("/user")
//public class UserController {
//    private final UserService userService;
//
//    @GetMapping("/all")
//    public List<User> findAllUsers() {
//        return userService.findAll();
//    }
//
//    @GetMapping()
//    public User findByLogin(@RequestParam String login) {
//        return userService.findByLogin(login);
//    }
//
//    @GetMapping("/{id}")
//    public User findById(@PathVariable int id) {
//        return userService.findById(id);
//    }
//
//}
