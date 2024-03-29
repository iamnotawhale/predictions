package zhigalin.predictions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@EnableScheduling
@SpringBootApplication
@PropertySource("classpath:application-prod.properties")
public class PredictionsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PredictionsApplication.class, args);
    }
}
