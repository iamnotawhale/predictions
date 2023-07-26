package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.user.RoleService;
import zhigalin.predictions.service.user.UserService;

import java.util.Collections;

@RequiredArgsConstructor
@RestController
@RequestMapping("/registration")
public class RegistrationController {
    private final RoleService roleService;
    private final UserService userService;

    @GetMapping()
    public ModelAndView getRegistrationPage() {
        ModelAndView model = new ModelAndView("registration");
        model.addObject("newUser", User.builder().build());
        return model;
    }

    @PostMapping()
    public ModelAndView saveUser(@ModelAttribute User userDto) {
        ModelAndView model = new ModelAndView("/login");
        User user = User.builder()
                .login(userDto.getLogin())
                .password(userDto.getPassword())
                .roles(Collections.singleton(roleService.findById(1L)))
                .build();
        userService.save(user);
        return model;
    }
}
