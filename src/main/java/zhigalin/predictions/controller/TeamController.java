package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;

@RestController
@RequestMapping("/team")
public class TeamController {
    private final TeamService teamService;
    private final MatchService matchService;
    private final WeekService weekService;

    @Autowired
    public TeamController(TeamService teamService, MatchService matchService, WeekService weekService) {
        this.teamService = teamService;
        this.matchService = matchService;
        this.weekService = weekService;
    }

    @PostMapping("/save")
    public TeamDto saveTeam(@RequestBody TeamDto teamDto) {
        return teamService.saveTeam(teamDto);
    }

    @GetMapping("/{id}")
    public ModelAndView getTeamById(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ModelAndView model = new ModelAndView("team");
        model.addObject("header", teamService.getById(id).getTeamName());
        model.addObject("currentUser", user);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("last5", matchService.getLast5MatchesByTeamId(id));
        model.addObject("last5Result", matchService.getLast5MatchesResultByTeamId(id));
        return model;
    }

    @GetMapping("/byName")
    public TeamDto findByTeamName(@RequestParam String name) {
        return teamService.getByName(name);
    }

    @GetMapping("/byCode")
    public TeamDto findByTeamCode(@RequestParam String code) {
        return teamService.getByCode(code);
    }
}
