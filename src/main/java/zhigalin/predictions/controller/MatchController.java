package zhigalin.predictions.controller;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;

import java.util.List;

@RestController
@RequestMapping("/match")
@AllArgsConstructor
public class MatchController {

    @Autowired
    private MatchService service;
    @Autowired
    private PredictionService predictionService;
    @Autowired
    private MatchMapper mapper;
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/week/{id}")
    public ModelAndView findByWeekId(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        ModelAndView model = new ModelAndView("match");
        model.addObject("currentUser", dto);
        model.addObject("byWeekList", service.getAllByWeekId(id));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping("/{id}")
    public MatchDto findById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/byNames")
    public MatchDto findByTeamNames(@RequestParam String home, @RequestParam String away) {
        return service.getByTeamNames(home, away);
    }

    @GetMapping("/byCodes")
    public MatchDto findByTeamCodes(@RequestParam String home, @RequestParam String away) {
        return service.getByTeamCodes(home, away);
    }

    @GetMapping("/result/byNames")
    public List<Integer> getResultByTeamNames(@RequestParam String homeTeamName, @RequestParam String awayTeamName) {
        return service.getResultByTeamNames(homeTeamName, awayTeamName);
    }

    @PostMapping("/saveByMatchId/{id}")
    public PredictionDto createPredict(@ModelAttribute PredictionDto dto, @PathVariable Long id) {
        MatchDto matchDto = service.getById(id);
        dto.setMatch(mapper.toEntity(matchDto));
        return predictionService.save(dto);
    }
}
