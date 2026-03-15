package com.serhatsgr.focusflow_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP istemci ve JSON Mapper bean konfigürasyonu.
 *
 * RestClient.Builder Spring Boot auto-configure tarafından sağlanır
 * ancak bazı ortamlarda doğrudan inject edilememektedir.
 * Bu sınıf builder kullanmadan RestClient bean'ini explicit olarak tanımlar.
 * * Ek olarak: Yapay zeka (Gemini/DeepSeek) entegrasyonu için
 * 45 saniyelik okuma zaman aşımı (Read Timeout) zırhı eklenmiştir.
 */
@Configuration
public class RestClientConfig {

    // 1. WeeklyAiCoachService'in beklediği JSON Çevirici (Hata çözen kısım)
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // 2. Zaman Aşımı (Timeout) Korumalı RestClient
    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 saniye bağlantı kurma süresi
        factory.setReadTimeout(45000);    // 45 saniye LLM'in JSON üretmesini bekleme süresi

        // Senin orijinal mantığına sadık kalarak kendi builder'ımızı yaratıp dönüyoruz
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}