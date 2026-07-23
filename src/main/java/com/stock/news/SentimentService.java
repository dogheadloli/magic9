package com.stock.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 用 DeepSeek 判断单条新闻对个股是利好/利空/中性。未配置 key 时降级为「未分析」。
 */
@Slf4j
@Service
public class SentimentService {

    private final AiProperties props;
    private final RestTemplate aiRestTemplate;
    private final ObjectMapper objectMapper;

    public SentimentService(AiProperties props, RestTemplate aiRestTemplate, ObjectMapper objectMapper) {
        this.props = props;
        this.aiRestTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isReady() {
        return props.isReady();
    }

    public SentimentResult classify(String name, String code, String title, String summary) {
        if (!props.isReady()) {
            return SentimentResult.unknown("AI未启用");
        }
        try {
            String content = buildPayload(name, code, title, summary);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getApiKey());

            String url = props.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            HttpEntity<String> req = new HttpEntity<>(content, headers);
            String resp = aiRestTemplate.postForObject(url, req, String.class);
            return parse(resp);
        } catch (Exception e) {
            log.error("DeepSeek 情感分析失败 code={} err={}", code, e.getMessage());
            return SentimentResult.unknown("分析失败");
        }
    }

    private String buildPayload(String name, String code, String title, String summary) throws Exception {
        String sys = "你是严谨的A股金融分析助手。基于给定新闻文本，判断其对相关股票属于利好、利空还是中性，"
                + "并给出情感分(-1到1，越大越利好)、置信度(0到1)和不超过30字的中文理由。只依据提供的文本，不臆测。"
                + "严格只输出JSON：{\"sentiment\":\"利好|利空|中性\",\"score\":数字,\"confidence\":数字,\"reason\":\"...\"}";
        StringBuilder user = new StringBuilder();
        user.append("股票：").append(name == null ? "" : name).append("(").append(code).append(")\n");
        user.append("标题：").append(title == null ? "" : title).append("\n");
        if (summary != null && !summary.isEmpty()) {
            String s = summary.length() > 500 ? summary.substring(0, 500) : summary;
            user.append("摘要：").append(s);
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", props.getModel());
        root.put("temperature", 0.2);
        ObjectNode fmt = root.putObject("response_format");
        fmt.put("type", "json_object");
        ArrayNode msgs = root.putArray("messages");
        ObjectNode m1 = msgs.addObject();
        m1.put("role", "system");
        m1.put("content", sys);
        ObjectNode m2 = msgs.addObject();
        m2.put("role", "user");
        m2.put("content", user.toString());
        return objectMapper.writeValueAsString(root);
    }

    private SentimentResult parse(String resp) throws Exception {
        if (resp == null) {
            return SentimentResult.unknown("空响应");
        }
        JsonNode content = objectMapper.readTree(resp)
                .path("choices").path(0).path("message").path("content");
        if (content.isMissingNode()) {
            return SentimentResult.unknown("无内容");
        }
        JsonNode j = objectMapper.readTree(content.asText());
        Sentiment s = Sentiment.fromCn(j.path("sentiment").asText(""));
        Double score = j.has("score") ? j.path("score").asDouble() : null;
        Double conf = j.has("confidence") ? j.path("confidence").asDouble() : null;
        String reason = j.path("reason").asText("");
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        return new SentimentResult(s, score, conf, reason);
    }
}
