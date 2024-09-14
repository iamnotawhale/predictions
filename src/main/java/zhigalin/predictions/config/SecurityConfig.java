//package zhigalin.predictions.config;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.Customizer;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//import org.springframework.security.core.userdetails.User;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.provisioning.InMemoryUserDetailsManager;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
//import zhigalin.predictions.service.user.UserService;
//
//@EnableWebSecurity
//@Configuration
//public class SecurityConfig {
//
//    private final UserService userService;
//    private final LoginSuccessHandler loginSuccessHandler;
//
//    public SecurityConfig(UserService userService, LoginSuccessHandler loginSuccessHandler) {
//        this.userService = userService;
//        this.loginSuccessHandler = loginSuccessHandler;
//    }
//
//    @Bean
//    public static PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder(12);
//    }
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .formLogin(httpSecurityFormLoginConfigurer -> httpSecurityFormLoginConfigurer
//                        .loginPage("/login")
//                        .successHandler(loginSuccessHandler)
//                        .usernameParameter("j_login")
//                        .passwordParameter("j_password")
//                        .failureUrl("/login?error")
//                        .permitAll()
//                );
//        http
//                .logout(httpSecurityLogoutConfigurer -> httpSecurityLogoutConfigurer
//                        .permitAll()
//                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
//                        .logoutSuccessUrl("/login?logout")
//                );
//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .authorizeHttpRequests(authorize ->
//                        authorize
//                                .requestMatchers("/login").permitAll()
//                                .requestMatchers("/registration").permitAll()
//                                .requestMatchers("/user").hasAnyRole("USER", "ADMIN")
//                                .requestMatchers("/admin/**").hasRole("ADMIN")
//                                .anyRequest().authenticated()
//                ).httpBasic(Customizer.withDefaults());
//        return http.build();
//    }
//
//    @Bean
//    public UserDetailsService userDetailsService() {
//        List<zhigalin.predictions.model.user.User> allUsers = userService.findAll();
//        List<UserDetails> userDetails = new ArrayList<>();
//        allUsers.forEach(user -> {
//            String name = user.getLogin();
//            String password = user.getPassword();
//            String role = user.getRole();
//            userDetails.add(
//                    User.builder()
//                            .username(name)
//                            .password(password)
//                            .roles(role)
//                            .build()
//            );
//        });
//        return new InMemoryUserDetailsManager(userDetails);
//    }
//}
