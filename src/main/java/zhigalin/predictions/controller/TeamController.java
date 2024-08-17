package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/team")
public class TeamController {
    private final TeamService teamService;
    private final MatchService matchService;
    private final WeekService weekService;


    @GetMapping("/{id}")
    public ModelAndView getTeamById(@PathVariable int publicId) {
        ModelAndView model = new ModelAndView("team");
        model.addObject("header", teamService.findByPublicId(publicId).getName());
        model.addObject("currentWeek", weekService.findCurrentWeek().getId());
        model.addObject("last5", matchService.findLast5MatchesByTeamId(publicId));
        model.addObject("last5Result", matchService.getLast5MatchesResultByTeamId(publicId));
        return model;
    }

    @GetMapping("/byName")
    public Team findByTeamName(@RequestParam String name) {
        return teamService.findByName(name);
    }

    @GetMapping("/byCode")
    public Team findByTeamCode(@RequestParam String code) {
        return teamService.findByCode(code);
    }

    @ModelAttribute("currentUser")
    public User getCurrentUser() {
        UserDetailsImpl userDetailsImpl = (UserDetailsImpl) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return User.builder()
                .id(userDetailsImpl.getId())
                .login(userDetailsImpl.getLogin())
                .build();
    }
}
