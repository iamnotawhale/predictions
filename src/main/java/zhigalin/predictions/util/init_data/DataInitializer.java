package zhigalin.predictions.util.init_data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private final DataInitServiceImpl init;

    @Autowired
    public DataInitializer(DataInitServiceImpl init) {
        this.init = init;
    }

    @Override
    public void run(String... args) {
        init.allInit();
    }
}
