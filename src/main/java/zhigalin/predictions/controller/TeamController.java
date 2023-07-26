package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
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
    private final TeamService service;
    private final MatchService matchService;
    private final WeekService weekService;

    @PostMapping("/save")
    public Team saveTeam(@RequestBody Team teamDto) {
        return service.save(teamDto);
    }

    @GetMapping("/{id}")
    public ModelAndView getTeamById(@PathVariable Long id) {
        ModelAndView model = new ModelAndView("team");
        model.addObject("header", service.findById(id).getName());
        model.addObject("currentWeek", weekService.findCurrentWeek().getWid());
        model.addObject("last5", matchService.findLast5MatchesByTeamId(id));
        model.addObject("last5Result", matchService.getLast5MatchesResultByTeamId(id));
        return model;
    }

    @GetMapping("/byName")
    public Team findByTeamName(@RequestParam String name) {
        return service.findByName(name);
    }

    @GetMapping("/byCode")
    public Team findByTeamCode(@RequestParam String code) {
        return service.findByCode(code);
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
