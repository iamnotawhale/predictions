package zhigalin.predictions.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/miniapp", "/miniapp/");
        registry.addViewController("/miniapp/").setViewName("forward:/miniapp/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/miniapp/**")
                .addResourceLocations("classpath:/static/miniapp/")
                .setCacheControl(CacheControl.noStore().mustRevalidate());
    }
}
