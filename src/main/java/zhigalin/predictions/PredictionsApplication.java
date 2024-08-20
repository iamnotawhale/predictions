package zhigalin.predictions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PredictionsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PredictionsApplication.class, args);
    }
}
