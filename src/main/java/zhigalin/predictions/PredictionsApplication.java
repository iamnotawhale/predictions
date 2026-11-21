package zhigalin.predictions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import zhigalin.predictions.util.ProcessLaunch;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class PredictionsApplication {
    public static void main(String[] args) {
        ProcessLaunch.preferForkLaunch();
        kong.unirest.Unirest.config().connectTimeout(5_000).socketTimeout(15_000);
        SpringApplication.run(PredictionsApplication.class, args);
    }
}
