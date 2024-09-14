//package zhigalin.predictions.controller;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.ModelAttribute;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.servlet.ModelAndView;
//import zhigalin.predictions.model.user.User;
//import zhigalin.predictions.service.user.UserService;
//
//@RequiredArgsConstructor
//@RestController
//@RequestMapping("/registration")
//public class RegistrationController {
//    private final UserService userService;
//    private final PasswordEncoder passwordEncoder;
//
//
//    @GetMapping()
//    public ModelAndView getRegistrationPage() {
//        ModelAndView model = new ModelAndView("registration");
//        model.addObject("newUser", User.builder().build());
//        return model;
//    }
//
//    @PostMapping()
//    public ModelAndView saveUser(@ModelAttribute User userDto) {
//        ModelAndView model = new ModelAndView("/login");
//        User user = User.builder()
//                .login(userDto.getLogin())
//                .password(passwordEncoder.encode(userDto.getPassword()))
//                .role("ADMIN")
//                .build();
//        userService.save(user);
//        return model;
//    }
//}
