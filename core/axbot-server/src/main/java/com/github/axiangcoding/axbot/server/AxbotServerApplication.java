package com.github.axiangcoding.axbot.server;

import com.github.axiangcoding.axbot.server.configuration.props.KookConfProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

@SpringBootApplication(nameGenerator = AnnotationBeanNameGenerator.class, exclude = {SecurityAutoConfiguration.class})
@EnableConfigurationProperties(KookConfProps.class)
public class AxbotServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AxbotServerApplication.class, args);
    }

}
