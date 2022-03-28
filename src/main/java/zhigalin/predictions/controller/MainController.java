package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;

@RestController
@RequestMapping()
public class MainController {

    private final MatchService matchService;

    private final StandingService standingService;

    @Autowired
    public MainController(MatchService matchService, StandingService standingService) {
        this.matchService = matchService;
        this.standingService = standingService;
    }

    @GetMapping
    public ModelAndView getMainPage(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setLogin(user.getLogin());
        ModelAndView model = new ModelAndView("main");
        model.addObject("currentUser", dto);
        model.addObject("matchList", matchService.getAllByCurrentWeek(true));
        model.addObject("standings", standingService.getAll());

        return model;
    }
}
