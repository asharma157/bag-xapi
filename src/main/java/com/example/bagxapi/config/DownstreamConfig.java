package com.example.bagxapi.config;

import com.example.bagxapi.routing.RoutingPropagationInterceptor;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * The downstream HTTP client.
 *
 * <p>Every outbound call goes through this one {@code RestTemplate}, which is where routing
 * propagation is installed. Note there is no version anywhere in the client's configuration:
 * the backend base URL is a constant, and version selection is the mesh's job.</p>
 */
@Configuration
public class DownstreamConfig {

    @Bean
    public RestTemplate bagServiceRestTemplate(RestTemplateBuilder builder,
                                               RoutingPropagationInterceptor propagationInterceptor) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .additionalInterceptors(propagationInterceptor)
                .build();
    }
}
