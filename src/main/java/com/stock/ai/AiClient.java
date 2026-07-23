package com.stock.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 通用 DeepSeek 对话客户端：强制 JSON 输出，返回助手消息正文（应为 JSON 字符串）。
 * 未配置 key 或调用失败时返回 null，由调用方降级处理。
 */
@Slf4j
@Component
public class AiClient {

    private final AiProperties props;
    private final RestTemplate aiRestTemplate;
    private final ObjectMapper objectMapper;

    public AiClient(AiProperties props, RestTemplate aiRestTemplate, ObjectMapper objectMapper) {
        this.props = props;
        this.aiRestTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isReady() {
        return props.isReady();
    }

    /** 发起一次对话，要求模型返回 JSON 对象；返回其文本内容或 null。 */
    public String completeJson(String system, String user, double temperature) {
        if (!props.isReady()) {
            return null;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", props.getModel());
            root.put("temperature", temperature);
            root.putObject("response_format").put("type", "json_object");
            ArrayNode msgs = root.putArray("messages");
            msgs.addObject().put("role", "system").put("content", system);
            msgs.addObject().put("role", "user").put("content", user);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getApiKey());
            String url = props.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

            String body = objectMapper.writeValueAsString(root);
            String resp = aiRestTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            if (resp == null) {
                return null;
            }
            JsonNode content = objectMapper.readTree(resp)
                    .path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? null : content.asText();
        } catch (Exception e) {
            log.error("AI 对话失败 err={}", e.getMessage());
            return null;
        }
    }
}
