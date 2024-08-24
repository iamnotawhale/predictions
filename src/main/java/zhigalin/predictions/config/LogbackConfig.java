package zhigalin.predictions.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.status.NopStatusListener;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zhigalin.predictions.util.CustomTimeBasedRollingPolicy;

@Configuration
public class LogbackConfig {

    @Bean
    public LoggerContext loggerContext() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getStatusManager().add(new NopStatusListener());
        return context;
    }

    @Bean
    public RollingFileAppender<ILoggingEvent> serverAppender() {
        return createAppender();
    }

    @Bean
    public ConsoleAppender<ILoggingEvent> consoleAppender() {
        LoggerContext context = loggerContext();
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d [%c{0}] %-5level: %m%n");
        encoder.start();

        appender.setEncoder(encoder);
        appender.start();

        return appender;
    }

    private RollingFileAppender<ILoggingEvent> createAppender() {
        LoggerContext context = loggerContext();
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setName("ServerLogger");

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);

        encoder.setPattern("%d [%c{0}] %-5level: %m%n");
        encoder.start();

        CustomTimeBasedRollingPolicy rollingPolicy = new CustomTimeBasedRollingPolicy();
        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern("./logs/" + "server-%d{yyyy-MM-dd}.log.gz");
        rollingPolicy.setMaxHistory(14);
        rollingPolicy.setParent(appender);
        rollingPolicy.start();

        appender.setEncoder(encoder);
        appender.setRollingPolicy(rollingPolicy);
        appender.start();

        return appender;
    }

    @Bean
    public Logger serverLogger() {
        LoggerContext context = loggerContext();
        Logger logger = context.getLogger("server");
        logger.setLevel(Level.TRACE);
        logger.addAppender(serverAppender());
        logger.addAppender(consoleAppender());
        logger.setAdditive(false);
        return logger;
    }

    @Bean
    public Logger springLogger() {
        LoggerContext context = loggerContext();
        Logger logger = context.getLogger("org.springframework");
        logger.setLevel(Level.WARN);
        logger.addAppender(serverAppender());
        return logger;
    }

    @Bean
    public Logger apacheLogger() {
        LoggerContext context = loggerContext();
        Logger logger = context.getLogger("org.apache");
        logger.setLevel(Level.WARN);
        logger.addAppender(serverAppender());
        return logger;
    }

    @Bean
    public Logger httpClientLogger() {
        LoggerContext context = loggerContext();
        Logger logger = context.getLogger("httpclient");
        logger.setLevel(Level.WARN);
        logger.addAppender(serverAppender());
        return logger;
    }
}
