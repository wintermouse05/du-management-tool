package org.example.dumanagementbackend.service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.example.dumanagementbackend.dto.chatops.ChatopsChannelConfigResponse;
import org.example.dumanagementbackend.dto.chatops.ChatopsChannelConfigUpsertRequest;
import org.example.dumanagementbackend.entity.ChatopsChannelConfig;
import org.example.dumanagementbackend.entity.enums.ChatopsChannelPurpose;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.repository.ChatopsChannelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatopsChannelConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatopsChannelConfigService.class);

    private final ChatopsChannelConfigRepository repository;
    private final SecretEncryptionService secretEncryptionService;
    private final RestTemplate restTemplate;

    public List<ChatopsChannelConfigResponse> getConfigs() {
        return List.of(ChatopsChannelPurpose.LATE_INPUT, ChatopsChannelPurpose.NOTIFICATION_OUTPUT).stream()
                .map(this::getConfigByPurpose)
                .toList();
    }

    public ChatopsChannelConfigResponse getConfigByPurpose(ChatopsChannelPurpose purpose) {
        return repository.findByPurpose(purpose)
                .map(this::toResponse)
                .orElseGet(() -> new ChatopsChannelConfigResponse(
                        null,
                        purpose,
                        null,
                        null,
                        false,
                        null,
                        null
                ));
    }

    @Transactional
    public ChatopsChannelConfigResponse upsert(ChatopsChannelPurpose purpose, ChatopsChannelConfigUpsertRequest request) {
        ChatopsChannelConfig existing = repository.findByPurpose(purpose).orElse(null);
        String token = resolveToken(existing, request.token());
        ParsedChannel parsed = parseChannelUrl(request.channelUrl());
        String channelId = resolveChannelId(parsed, token);

        ChatopsChannelConfig config = existing != null ? existing : new ChatopsChannelConfig();
        config.setPurpose(purpose);
        config.setBaseUrl(parsed.baseUrl());
        config.setChannelUrl(request.channelUrl().trim());
        config.setChannelId(channelId);
        config.setEncryptedToken(secretEncryptionService.encrypt(token));
        return toResponse(repository.save(config));
    }

    public Optional<ResolvedChatopsConfig> getResolvedConfig(ChatopsChannelPurpose purpose) {
        return repository.findByPurpose(purpose)
                .flatMap(config -> {
                    if (isBlank(config.getBaseUrl()) || isBlank(config.getChannelId()) || isBlank(config.getEncryptedToken())) {
                        return Optional.empty();
                    }
                    String token = secretEncryptionService.decrypt(config.getEncryptedToken());
                    return Optional.of(new ResolvedChatopsConfig(config.getBaseUrl(), config.getChannelId(), token));
                });
    }

    private String resolveToken(ChatopsChannelConfig existing, String providedToken) {
        String token = trimToNull(providedToken);
        if (token != null) {
            return token;
        }
        if (existing != null && !isBlank(existing.getEncryptedToken())) {
            return secretEncryptionService.decrypt(existing.getEncryptedToken());
        }
        throw new BadRequestException("token is required");
    }

    private String resolveChannelId(ParsedChannel parsed, String token) {
        String teamId = resolveTeamId(parsed.baseUrl(), parsed.teamName(), token);
        return resolveChannelId(parsed.baseUrl(), teamId, parsed.channelName(), token);
    }

    private String resolveTeamId(String baseUrl, String teamName, String token) {
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v4/teams/name/{teamName}")
                .buildAndExpand(teamName)
                .toUriString();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders(token)),
                    (Class<Map>) (Class<?>) Map.class
            );
            Object teamId = response.getBody() != null ? response.getBody().get("id") : null;
            if (!(teamId instanceof String teamIdValue) || teamIdValue.isBlank()) {
                throw new BadRequestException("Unable to resolve team id from channel URL");
            }
            return teamIdValue;
        } catch (RestClientException ex) {
            LOGGER.error("Unable to resolve team id from Mattermost API. url={}", url, ex);
            throw new BadRequestException("Unable to resolve team from Mattermost. Please verify URL and token.");
        }
    }

    private String resolveChannelId(String baseUrl, String teamId, String channelName, String token) {
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v4/teams/{teamId}/channels/name/{channelName}")
                .buildAndExpand(teamId, channelName)
                .toUriString();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders(token)),
                    (Class<Map>) (Class<?>) Map.class
            );
            Object channelId = response.getBody() != null ? response.getBody().get("id") : null;
            if (!(channelId instanceof String channelIdValue) || channelIdValue.isBlank()) {
                throw new BadRequestException("Unable to resolve channel id from channel URL");
            }
            return channelIdValue;
        } catch (RestClientException ex) {
            LOGGER.error("Unable to resolve channel id from Mattermost API. url={}, teamId={}, channelName={}",
                    url,
                    teamId,
                    channelName,
                    ex
            );
            throw new BadRequestException("Unable to resolve channel from Mattermost. Please verify URL and token.");
        }
    }

    private ParsedChannel parseChannelUrl(String channelUrl) {
        String normalized = trimToNull(channelUrl);
        if (normalized == null) {
            throw new BadRequestException("channelUrl is required");
        }

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid channel URL format");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (isBlank(scheme) || isBlank(host)) {
            throw new BadRequestException("channelUrl must be a valid absolute URL");
        }

        List<String> segments = Arrays.stream(Optional.ofNullable(uri.getPath()).orElse("")
                        .split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
        int channelsIndex = segments.indexOf("channels");
        if (channelsIndex <= 0 || channelsIndex >= segments.size() - 1) {
            throw new BadRequestException("channelUrl must contain '/{team}/channels/{channel}'");
        }

        String teamName = segments.get(channelsIndex - 1);
        String channelName = segments.get(channelsIndex + 1);
        String baseUrl = buildBaseUrl(uri);
        return new ParsedChannel(baseUrl, teamName, channelName);
    }

    private String buildBaseUrl(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = ("https".equals(scheme) && port == 443)
                || ("http".equals(scheme) && port == 80)
                || port < 0;
        if (defaultPort) {
            return scheme + "://" + uri.getHost();
        }
        return scheme + "://" + uri.getHost() + ":" + port;
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ChatopsChannelConfigResponse toResponse(ChatopsChannelConfig config) {
        return new ChatopsChannelConfigResponse(
                config.getId(),
                config.getPurpose(),
                config.getChannelUrl(),
                config.getChannelId(),
                !isBlank(config.getEncryptedToken()),
                !isBlank(config.getEncryptedToken()) ? "********" : null,
                config.getUpdatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParsedChannel(String baseUrl, String teamName, String channelName) {
    }

    public record ResolvedChatopsConfig(String baseUrl, String channelId, String token) {
    }
}
