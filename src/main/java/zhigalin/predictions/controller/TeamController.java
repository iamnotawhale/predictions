package zhigalin.predictions.controller;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.service.football.TeamService;

@RestController
@RequestMapping("/team")
@AllArgsConstructor
public class TeamController {

    @Autowired
    private TeamService teamService;

    @PostMapping("/save")
    public TeamDto saveTeam(@RequestBody TeamDto teamDto) {
        return teamService.saveTeam(teamDto);
    }

    @GetMapping("/{id}")
    public TeamDto findById(@PathVariable Long id) {
        return teamService.getById(id);
    }

    @GetMapping("/byName")
    public TeamDto findByTeamName(@RequestParam String name) {
        return teamService.getByName(name);
    }

    @GetMapping("/byCode")
    public TeamDto findByTeamCode(@RequestParam String code) {
        return teamService.getByCode(code);}
}
