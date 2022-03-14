package zhigalin.predictions.controller;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.converter.event.MatchMapper;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/predict")
@AllArgsConstructor
public class PredictController {

    @Autowired
    private PredictionService predictionService;
    @Autowired
    private UserService userService;
    @Autowired
    private MatchService matchService;
    @Autowired
    private MatchMapper mapper;

    @GetMapping("/week/{id}")
    public ModelAndView getByWeekId(@PathVariable Long id) {
        List<PredictionDto> list = predictionService.getAllByWeekId(id);
        List<Integer> pointsList = new ArrayList<>();
        Set<PredictionDto> singleDto = new HashSet<>();
        for (PredictionDto dto : list) {
            Integer points = null;
            Integer realHomeScore = dto.getMatch().getHomeTeamScore();
            Integer realAwayScore = dto.getMatch().getAwayTeamScore();
            Integer predictHomeScore = dto.getHomeTeamScore();
            Integer predictAwayScore = dto.getAwayTeamScore();
            if (realHomeScore.equals(predictHomeScore) && realAwayScore.equals(predictAwayScore)) {
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
            singleDto.add(dto);
        }
        ModelAndView model = new ModelAndView("predict");
        model.addObject("list", list);
        model.addObject("single", singleDto);
        model.addObject("points", pointsList);
        return model;
    }

    //    @GetMapping("/match/{id}")
//    public ModelAndView getByMatchId(@PathVariable Long id) {
//        ModelAndView model = new ModelAndView("pred");
//        model.addObject("predict", service.getAllByMatchId(id));
//        return model;
//    }
    @GetMapping("/match/{id}")
    public List<PredictionDto> getByMatchId(@PathVariable Long id) {
        return predictionService.getAllByMatchId(id);
    }

    @GetMapping("/{id}")
    public PredictionDto getById(@PathVariable Long id) {
        return predictionService.getById(id);
    }

    @PostMapping("/saveByMatchId/{id}")
    public PredictionDto createPredict(@ModelAttribute PredictionDto dto, @PathVariable Long id) {
        MatchDto matchDto = matchService.getById(id);
        dto.setMatch(mapper.toEntity(matchDto));
        return predictionService.save(dto);
    }

}
