package com.offercoach.app.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "oc.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:5174");
}
