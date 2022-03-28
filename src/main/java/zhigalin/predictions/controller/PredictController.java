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

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/predict")
public class PredictController {

    private final PredictionService predictionService;
    private final MatchService matchService;
    private final MatchMapper mapper;
    private final PredictionMapper predictionMapper;

    @Autowired
    public PredictController(PredictionService predictionService, MatchService matchService, MatchMapper mapper, PredictionMapper predictionMapper) {
        this.predictionService = predictionService;
        this.matchService = matchService;
        this.mapper = mapper;
        this.predictionMapper = predictionMapper;
    }

    @GetMapping("/week/{id}")
    public ModelAndView getByWeekId(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PredictionDto> list = predictionService.getAllByWeekId(id);
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("header", "Прогнозы " + id + " тура");
        model.addObject("currentUser", user);
        model.addObject("list", list);
        model.addObject("points", getPredictPoints(list));
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
                .match(mapper.toEntity(matchDto))
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

    @GetMapping("/week")
    public ModelAndView getByUserIdAndWeekId(@RequestParam Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PredictionDto> list = predictionService.getAllByUserIdAndWeekId(user.getId(), id);
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("header", "Мои прогнозы " + id + " тура");
        model.addObject("currentUser", user);
        model.addObject("list", list);
        model.addObject("points", getPredictPoints(list));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping()
    public ModelAndView getByUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PredictionDto> list = predictionService.getAllByUser_Id(user.getId());
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("header", "Мои прогнозы ");
        model.addObject("currentUser", user);
        model.addObject("list", list);
        model.addObject("points", getPredictPoints(list));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    public  List<Integer> getPredictPoints(List<PredictionDto> list) {
        List<Integer> pointsList = new ArrayList<>();
        for (PredictionDto dto : list) {
            Integer points;
            Integer realHomeScore = dto.getMatch().getHomeTeamScore();
            Integer realAwayScore = dto.getMatch().getAwayTeamScore();
            Integer predictHomeScore = dto.getHomeTeamScore();
            Integer predictAwayScore = dto.getAwayTeamScore();
            if (realHomeScore == null || realAwayScore == null) {
                points = null;
            } else if (realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore)) {
                points = 5;
            } else if (realHomeScore - realAwayScore == predictHomeScore - predictAwayScore) {
                points = 3;
            } else if (realHomeScore > realAwayScore && predictHomeScore > predictAwayScore) {
                points = 1;
            } else if (realHomeScore < realAwayScore && predictHomeScore < predictAwayScore) {
                points = 1;
            } else {
                points = 0;
            }
            pointsList.add(points);
        }
        return pointsList;
    }
}
