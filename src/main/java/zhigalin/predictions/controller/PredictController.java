package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.converter.predict.PredictionMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/predict")
public class PredictController {

    private final PredictionService predictionService;
    private final MatchService matchService;
    private final MatchMapper matchMapper;
    private final PredictionMapper predictionMapper;
    private final UserService userService;

    @Autowired
    public PredictController(PredictionService predictionService, MatchService matchService,
                             MatchMapper matchMapper, PredictionMapper predictionMapper, UserService userService) {
        this.predictionService = predictionService;
        this.matchService = matchService;
        this.matchMapper = matchMapper;
        this.predictionMapper = predictionMapper;
        this.userService = userService;
    }

    @GetMapping("/week/{id}")
    public ModelAndView getByWeekId(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PredictionDto> list = predictionService.getAllByWeekId(id);
        ModelAndView model = new ModelAndView("predict");
        model.addObject("weeklyUsersPoints", predictionService.usersPointsByWeek(id));
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("header", "Прогнозы " + id + " тура");
        model.addObject("currentUser", user);
        model.addObject("list", list);
        return model;
    }

    @GetMapping("/match/{id}")
    public List<PredictionDto> getByMatchId(@PathVariable Long id) {
        return predictionService.getAllByMatchId(id);
    }

    @GetMapping("/{id}")
    public PredictionDto getById(@PathVariable Long id) {
        return predictionService.getById(id);
    }

    @PostMapping("/saveByMatchId/{id}")
    public ModelAndView createPredictByMatchId(@ModelAttribute PredictionDto dto, @PathVariable Long id, HttpServletRequest request) {
        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
        MatchDto matchDto = matchService.getById(id);
        Prediction prediction = Prediction.builder()
                .user(dto.getUser())
                .match(matchMapper.toEntity(matchDto))
                .homeTeamScore(dto.getHomeTeamScore())
                .awayTeamScore(dto.getAwayTeamScore())
                .build();
        predictionService.save(predictionMapper.toDto(prediction));
        return model;
    }

    @PostMapping("/update")
    public ModelAndView updatePredict(@ModelAttribute PredictionDto dto, HttpServletRequest request) {
        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
        Prediction prediction = Prediction.builder()
                .user(dto.getUser())
                .match(dto.getMatch())
                .homeTeamScore(dto.getHomeTeamScore())
                .awayTeamScore(dto.getAwayTeamScore())
                .build();
        predictionService.save(predictionMapper.toDto(prediction));
        return model;
    }

    @GetMapping("/byUserAndWeek")
    public ModelAndView getByUserAndWeek(@RequestParam Long user, @RequestParam Long week, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        List<PredictionDto> list = predictionService.getAllByUserIdAndWeekId(user, week);
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("header", "Прогнозы " + userService.getById(user).getLogin() + " " + week + " тура");
        model.addObject("currentUser", currentUser);
        model.addObject("list", list);
        model.addObject("newPredict", new PredictionDto());
        return model;
    }


    @GetMapping("/week")
    public ModelAndView getByCurrentUserAndWeek(@RequestParam Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PredictionDto> list = predictionService.getAllByUserIdAndWeekId(user.getId(), id);
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("header", "Мои прогнозы " + id + " тура");
        model.addObject("currentUser", user);
        model.addObject("list", list);
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping()
    public ModelAndView getByUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PredictionDto> list = predictionService.getAllByUser_Id(user.getId());
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("header", "Мои прогнозы ");
        model.addObject("currentUser", user);
        model.addObject("list", list);
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping("/delete")
    public ModelAndView deletePredict(@RequestParam Long id, HttpServletRequest request) {
        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
        predictionService.deleteById(id);
        return model;
    }
}
