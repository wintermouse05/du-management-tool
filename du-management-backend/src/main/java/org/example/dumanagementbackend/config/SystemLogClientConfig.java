package org.example.dumanagementbackend.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import org.example.dumanagementbackend.service.SystemLogCreateRequest;
import org.example.dumanagementbackend.service.SystemLogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Configuration
public class SystemLogClientConfig {

    @Bean
    public RestTemplate restTemplate(SystemLogService systemLogService) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new LoggingRestTemplateInterceptor(systemLogService));
        return restTemplate;
    }

    @Bean
    public WebClient webClient(SystemLogService systemLogService) {
        return WebClient.builder()
                .filter(loggingWebClientFilter(systemLogService))
                .build();
    }

    private ExchangeFilterFunction loggingWebClientFilter(SystemLogService systemLogService) {
        return (request, next) -> {
            long startedAt = System.nanoTime();
            return next.exchange(request)
                    .doOnSuccess(response -> {
                        int status = response.statusCode().value();
                        logExternalCall(
                                systemLogService,
                                request.method().name(),
                                request.url(),
                                status,
                                (System.nanoTime() - startedAt) / 1_000_000,
                                status >= 500 ? SystemLogSeverity.ERROR : status >= 400 ? SystemLogSeverity.WARN : SystemLogSeverity.INFO,
                                status >= 500 ? SystemLogStatus.FAILED : SystemLogStatus.SUCCESS,
                                null
                        );
                    })
                    .doOnError(ex -> logExternalCall(
                            systemLogService,
                            request.method().name(),
                            request.url(),
                            null,
                            (System.nanoTime() - startedAt) / 1_000_000,
                            SystemLogSeverity.ERROR,
                            SystemLogStatus.FAILED,
                            ex
                    ))
                    .onErrorResume(Mono::error);
        };
    }

    private static void logExternalCall(
            SystemLogService systemLogService,
            String method,
            URI uri,
            Integer status,
            long durationMs,
            SystemLogSeverity severity,
            SystemLogStatus logStatus,
            Throwable failure
    ) {
        String safeUrl = SystemLogSanitizer.safeUrl(uri.toString());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", method);
        details.put("url", safeUrl);
        details.put("host", uri.getHost());
        details.put("status", status);

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.EXTERNAL_API,
                severity,
                logStatus,
                method,
                uri.getHost(),
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                "ExternalApi",
                uri.getHost(),
                durationMs,
                method + " " + safeUrl + (status != null ? " -> " + status : " failed"),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }

    private static class LoggingRestTemplateInterceptor implements ClientHttpRequestInterceptor {

        private final SystemLogService systemLogService;

        private LoggingRestTemplateInterceptor(SystemLogService systemLogService) {
            this.systemLogService = systemLogService;
        }

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution
        ) throws java.io.IOException {
            long startedAt = System.nanoTime();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                int status = response.getStatusCode().value();
                logExternalCall(
                        systemLogService,
                        request.getMethod().name(),
                        request.getURI(),
                        status,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        status >= 500 ? SystemLogSeverity.ERROR : status >= 400 ? SystemLogSeverity.WARN : SystemLogSeverity.INFO,
                        status >= 500 ? SystemLogStatus.FAILED : SystemLogStatus.SUCCESS,
                        null
                );
                return response;
            } catch (RestClientException | java.io.IOException ex) {
                logExternalCall(
                        systemLogService,
                        request.getMethod().name(),
                        request.getURI(),
                        null,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        SystemLogSeverity.ERROR,
                        SystemLogStatus.FAILED,
                        ex
                );
                throw ex;
            }
        }
    }
}
