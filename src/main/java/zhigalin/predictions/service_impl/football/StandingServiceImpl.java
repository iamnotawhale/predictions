package zhigalin.predictions.service_impl.football;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.football.StandingMapper;
import zhigalin.predictions.dto.football.StandingDto;
import zhigalin.predictions.model.football.Standing;
import zhigalin.predictions.repository.football.StandingRepository;
import zhigalin.predictions.service.football.StandingService;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StandingServiceImpl implements StandingService {

    private final StandingRepository repository;
    private final StandingMapper mapper;

    @Autowired
    public StandingServiceImpl(StandingRepository repository, StandingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<StandingDto> getAll() {
        List<Standing> list = (List<Standing>) repository.findAll();
        return list.stream().sorted((s1, s2) -> s2.getPoints().compareTo(s1.getPoints())).map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public StandingDto save(StandingDto dto) {
        Standing standing = repository.getByTeam_Id(dto.getTeam().getId());
        return mapper.toDto(Objects.requireNonNullElseGet(standing, () -> repository.save(mapper.toEntity(dto))));
    }

    @Override
    public StandingDto findByTeam_Id(Long id) {
        Standing standing = repository.getByTeam_Id(id);
        if (standing != null) {
            return mapper.toDto(standing);
        }
        return null;
    }
}
