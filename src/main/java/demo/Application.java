package demo;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

    @Bean
    RestTemplate restTemplate (){
        return new RestTemplate();
    }

    public static void main(String[] args) {
      new SpringApplicationBuilder( Application.class)
              .web(false)
              .run( args);
    }
}


