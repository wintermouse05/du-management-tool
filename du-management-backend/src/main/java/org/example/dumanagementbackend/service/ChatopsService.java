package org.example.dumanagementbackend.service;

import java.util.List;
import java.util.Map;

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

@Service
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class ChatopsService {

    private static final Logger log = LoggerFactory.getLogger(ChatopsService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${chatops.api.base-url}")
    private String baseUrl;

    @Value("${chatops.api.token}")
    private String token;

    @Value("${chatops.api.assistant-id}")
    private String assistantId;

    @Value("${chatops.channel-id}")
    private String channelId;

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public Map<String, Object> getUserByUsername(String username) {
        String url = baseUrl + "/api/v4/users/username/" + username;
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                (Class<Map>) (Class<?>) Map.class
        );
        return response.getBody();
    }

    public Map<String, Object> getUserByEmail(String email) {
        String url = baseUrl + "/api/v4/users/email/" + email;
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders()),
                (Class<Map>) (Class<?>) Map.class
        );
        return response.getBody();
    }

    public String getDirectChannelId(String senderId, String receiverId) {
        String url = baseUrl + "/api/v4/channels/direct";
        List<String> participants = (assistantId != null && !assistantId.isBlank())
                ? List.of(assistantId, receiverId)
                : List.of(senderId, receiverId);
        HttpEntity<List<String>> request = new HttpEntity<>(participants, createHeaders());
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
        String url = baseUrl + "/api/v4/posts";
        Map<String, String> body = Map.of(
                "channel_id", targetChannelId,
                "message", message
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, createHeaders());
        restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
    }

    public String sendMessageWithResponse(String targetChannelId, String message, String rootId) {
        String url = baseUrl + "/api/v4/posts";
        Map<String, String> body = new java.util.HashMap<>();
        body.put("channel_id", targetChannelId);
        body.put("message", message);
        if (rootId != null) {
            body.put("root_id", rootId);
        }
        try {
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, createHeaders());
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
        String url = String.format("%s/api/v4/channels/%s/posts?since=%d", baseUrl, targetChannelId, sinceTimestamp);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                (Class<Map>) (Class<?>) Map.class
        );
        return response.getBody();
    }

    public String getChannelId() {
        return channelId;
    }
}
