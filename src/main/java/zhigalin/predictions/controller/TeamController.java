package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.StatsService;
import zhigalin.predictions.service.football.TeamService;

@RestController
@RequestMapping("/team")
public class TeamController {

    private final TeamService teamService;

    private final MatchService matchService;

    private final StatsService statsService;

    @Autowired
    public TeamController(TeamService teamService, MatchService matchService, StatsService statsService) {
        this.teamService = teamService;
        this.matchService = matchService;
        this.statsService = statsService;
    }

    @PostMapping("/save")
    public TeamDto saveTeam(@RequestBody TeamDto teamDto) {
        return teamService.saveTeam(teamDto);
    }

    @GetMapping("/{id}")
    public ModelAndView getTeamById(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ModelAndView model = new ModelAndView("team");
        model.addObject("stats", statsService.getAvgStatsByTeamId(id));
        model.addObject("header", teamService.getById(id).getTeamName());
        model.addObject("currentUser", user);
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
        return teamService.getByCode(code);}
}
