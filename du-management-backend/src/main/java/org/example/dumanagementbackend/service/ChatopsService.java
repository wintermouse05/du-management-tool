package org.example.dumanagementbackend.service;

import java.util.List;
import java.util.Map;

import org.example.dumanagementbackend.entity.enums.ChatopsChannelPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class ChatopsService {

    private static final Logger log = LoggerFactory.getLogger(ChatopsService.class);

    private final RestTemplate restTemplate;
    private final ChatopsChannelConfigService chatopsChannelConfigService;

    @Value("${chatops.api.base-url}")
    private String defaultBaseUrl;

    @Value("${chatops.api.token}")
    private String defaultToken;

    @Value("${chatops.api.assistant-id}")
    private String assistantId;

    @Value("${chatops.channel-id}")
    private String defaultChannelId;

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public Map<String, Object> getUserByUsername(String username) {
        ChatopsChannelConfigService.ResolvedChatopsConfig config = resolveConfig(ChatopsChannelPurpose.NOTIFICATION_OUTPUT);
        String url = config.baseUrl() + "/api/v4/users/username/" + username;
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders(config.token())),
                (Class<Map>) (Class<?>) Map.class
        );
        return response.getBody();
    }

    public Map<String, Object> getUserByEmail(String email) {
        ChatopsChannelConfigService.ResolvedChatopsConfig config = resolveConfig(ChatopsChannelPurpose.NOTIFICATION_OUTPUT);
        String url = config.baseUrl() + "/api/v4/users/email/" + email;
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders(config.token())),
                (Class<Map>) (Class<?>) Map.class
        );
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUsersByIds(List<String> userIds, ChatopsChannelPurpose purpose) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        ChatopsChannelConfigService.ResolvedChatopsConfig config = resolveConfig(purpose);
        String url = config.baseUrl() + "/api/v4/users/ids";
        HttpEntity<List<String>> request = new HttpEntity<>(userIds, createHeaders(config.token()));
        ResponseEntity<List> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                List.class
        );
        if (response.getBody() == null) {
            return List.of();
        }
        return (List<Map<String, Object>>) (List<?>) response.getBody();
    }

    public String getDirectChannelId(String senderId, String receiverId) {
        ChatopsChannelConfigService.ResolvedChatopsConfig config = resolveConfig(ChatopsChannelPurpose.NOTIFICATION_OUTPUT);
        String url = config.baseUrl() + "/api/v4/channels/direct";
        List<String> participants = (assistantId != null && !assistantId.isBlank())
                ? List.of(assistantId, receiverId)
                : List.of(senderId, receiverId);
        HttpEntity<List<String>> request = new HttpEntity<>(participants, createHeaders(config.token()));
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                (Class<Map>) (Class<?>) Map.class
        );
        if (response.getBody() == null || response.getBody().get("id") == null) {
            throw new RuntimeException("Mattermost direct channel creation returned no ID");
        }
        return (String) response.getBody().get("id");
    }

    public void sendMessage(String targetChannelId, String message) {
        ChatopsChannelConfigService.ResolvedChatopsConfig config = resolveConfig(ChatopsChannelPurpose.NOTIFICATION_OUTPUT);
        String url = config.baseUrl() + "/api/v4/posts";
        Map<String, String> body = Map.of(
                "channel_id", targetChannelId,
                "message", message
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, createHeaders(config.token()));
        restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
    }

    public String sendMessageWithResponse(String targetChannelId, String message, String rootId) {
        try {
            ChatopsChannelConfigService.ResolvedChatopsConfig config = resolveConfig(ChatopsChannelPurpose.NOTIFICATION_OUTPUT);
            String url = config.baseUrl() + "/api/v4/posts";
            Map<String, String> body = new java.util.HashMap<>();
            body.put("channel_id", targetChannelId);
            body.put("message", message);
            if (rootId != null) {
                body.put("root_id", rootId);
            }
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, createHeaders(config.token()));
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request,
                    (Class<Map>) (Class<?>) Map.class
            );
            if (response.getBody() != null && response.getBody().get("id") != null) {
                return response.getBody().get("id").toString();
            }
        } catch (Exception e) {
            log.error("Failed to send chat message: {}", e.getMessage());
        }
        return null;
    }

    public Map<String, Object> getChannelPosts(String targetChannelId, long sinceTimestamp) {
        ChatopsChannelConfigService.ResolvedChatopsConfig config = resolveConfig(ChatopsChannelPurpose.LATE_INPUT);
        String url = String.format("%s/api/v4/channels/%s/posts?since=%d", config.baseUrl(), targetChannelId, sinceTimestamp);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(config.token()));
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                (Class<Map>) (Class<?>) Map.class
        );
        return response.getBody();
    }

    public String getChannelId() {
        return getInputChannelId();
    }

    public String getInputChannelId() {
        return chatopsChannelConfigService.getResolvedConfig(ChatopsChannelPurpose.LATE_INPUT)
                .map(ChatopsChannelConfigService.ResolvedChatopsConfig::channelId)
                .orElse(defaultChannelId);
    }

    public String getOutputChannelId() {
        return chatopsChannelConfigService.getResolvedConfig(ChatopsChannelPurpose.NOTIFICATION_OUTPUT)
                .map(ChatopsChannelConfigService.ResolvedChatopsConfig::channelId)
                .orElse(defaultChannelId);
    }

    private ChatopsChannelConfigService.ResolvedChatopsConfig resolveConfig(ChatopsChannelPurpose purpose) {
        return chatopsChannelConfigService.getResolvedConfig(purpose)
                .orElseGet(() -> {
                    if (defaultBaseUrl == null || defaultBaseUrl.isBlank()
                            || defaultToken == null || defaultToken.isBlank()
                            || defaultChannelId == null || defaultChannelId.isBlank()) {
                        throw new IllegalStateException("ChatOps fallback configuration is missing");
                    }
                    return new ChatopsChannelConfigService.ResolvedChatopsConfig(
                            defaultBaseUrl,
                            defaultChannelId,
                            defaultToken
                    );
                });
    }
}
