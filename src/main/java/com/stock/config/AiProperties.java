package com.stock.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 情感分析配置（DeepSeek）。未配置 api-key 或 enabled=false 时降级为「未分析」。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.deepseek")
public class AiProperties {
    private boolean enabled = false;
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-chat";

    public boolean isReady() {
        return enabled && apiKey != null && !apiKey.trim().isEmpty();
    }
}
