package com.stock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${market.eastmoney.fetch.connect-timeout-ms:5000}")
    private int connectTimeout;

    @Value("${market.eastmoney.fetch.read-timeout-ms:8000}")
    private int readTimeout;

    @Bean
    public RestTemplate marketRestTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        RestTemplate restTemplate = builder
                .requestFactory(() -> factory)
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .build();
        forceUtf8(restTemplate);
        return restTemplate;
    }

    /** 调用大模型(DeepSeek)用：读超时更长。 */
    @Bean
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(60000);
        RestTemplate restTemplate = builder
                .requestFactory(() -> factory)
                .setConnectTimeout(Duration.ofMillis(8000))
                .setReadTimeout(Duration.ofMillis(60000))
                .build();
        forceUtf8(restTemplate);
        return restTemplate;
    }

    private static void forceUtf8(RestTemplate restTemplate) {
        // 默认 String 转换器用 ISO-8859-1，会导致中文乱码，这里改为 UTF-8
        for (int i = 0; i < restTemplate.getMessageConverters().size(); i++) {
            HttpMessageConverter<?> c = restTemplate.getMessageConverters().get(i);
            if (c instanceof StringHttpMessageConverter) {
                restTemplate.getMessageConverters().set(i,
                        new StringHttpMessageConverter(StandardCharsets.UTF_8));
            }
        }
    }
}
