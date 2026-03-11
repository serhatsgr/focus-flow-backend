package com.serhatsgr.focusflow_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP istemci bean konfigürasyonu.
 *
 * RestClient.Builder Spring Boot auto-configure tarafından sağlanır
 * ancak bazı ortamlarda doğrudan inject edilememektedir.
 * Bu sınıf builder kullanmadan RestClient bean'ini explicit olarak tanımlar.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
