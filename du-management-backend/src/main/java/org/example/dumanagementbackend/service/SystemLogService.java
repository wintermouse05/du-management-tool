package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.example.dumanagementbackend.dto.systemlog.SystemLogDetailResponse;
import org.example.dumanagementbackend.dto.systemlog.SystemLogListResponse;
import org.example.dumanagementbackend.entity.SystemLogEntry;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import org.example.dumanagementbackend.repository.SystemLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void log(SystemLogCreateRequest request) {
        if (request == null || request.category() == null) {
            return;
        }

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> systemLogRepository.save(toEntity(request)));
        } catch (Exception ignored) {
            // Logging must never break the operation that produced the log.
        }
    }

    public void logAll(List<SystemLogCreateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        try {
            List<SystemLogEntry> entries = requests.stream()
                    .filter(request -> request != null && request.category() != null)
                    .map(this::toEntity)
                    .toList();
            if (entries.isEmpty()) {
                return;
            }
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> systemLogRepository.saveAll(entries));
        } catch (Exception ignored) {
            // Best-effort batch persistence.
        }
    }

    @Transactional(readOnly = true)
    public Page<SystemLogListResponse> search(SystemLogSearchCriteria criteria, Pageable pageable) {
        Pageable resolvedPageable = resolvePageable(pageable);
        return systemLogRepository.findAll(toSpecification(criteria), resolvedPageable).map(this::toListResponse);
    }

    @Transactional(readOnly = true)
    public SystemLogDetailResponse getById(Long id) {
        return systemLogRepository.findById(id)
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("System log entry not found with id=" + id));
    }

    @Transactional
    public int deleteOlderThan(LocalDateTime cutoff) {
        return systemLogRepository.deleteOlderThan(cutoff);
    }

    private Pageable resolvePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "occurredAt"));
        }
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "occurredAt");
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private Specification<SystemLogEntry> toSpecification(SystemLogSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria == null) {
                return cb.conjunction();
            }
            if (criteria.categories() != null && !criteria.categories().isEmpty()) {
                predicates.add(root.get("category").in(criteria.categories()));
            }
            if (criteria.severity() != null) {
                predicates.add(cb.equal(root.get("severity"), criteria.severity()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), criteria.to()));
            }
            addLike(predicates, cb, root.get("source"), criteria.source());
            addLike(predicates, cb, root.get("actorUsername"), criteria.actor());
            addLike(predicates, cb, root.get("correlationId"), criteria.correlationId());

            String q = trimToNull(criteria.q());
            if (q != null) {
                String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("message")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(root.get("source")), pattern),
                        cb.like(cb.lower(root.get("actorUsername")), pattern),
                        cb.like(cb.lower(root.get("correlationId")), pattern),
                        cb.like(cb.lower(root.get("targetType")), pattern),
                        cb.like(cb.lower(root.get("targetId")), pattern),
                        cb.like(cb.lower(root.get("exceptionClass")), pattern)
                ));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addLike(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                         jakarta.persistence.criteria.Expression<String> expression, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            predicates.add(cb.like(cb.lower(expression), "%" + trimmed.toLowerCase(Locale.ROOT) + "%"));
        }
    }

    private SystemLogEntry toEntity(SystemLogCreateRequest request) {
        SystemLogEntry entry = new SystemLogEntry();
        entry.setOccurredAt(LocalDateTime.now());
        entry.setCategory(request.category());
        entry.setSeverity(Optional.ofNullable(request.severity()).orElse(SystemLogSeverity.INFO));
        entry.setStatus(Optional.ofNullable(request.status()).orElse(SystemLogStatus.SUCCESS));
        entry.setAction(SystemLogSanitizer.truncate(request.action(), 120));
        entry.setSource(SystemLogSanitizer.truncate(request.source(), 200));
        entry.setActorUsername(SystemLogSanitizer.truncate(request.actorUsername(), 100));
        entry.setCorrelationId(SystemLogSanitizer.truncate(request.correlationId(), 100));
        entry.setTargetType(SystemLogSanitizer.truncate(request.targetType(), 120));
        entry.setTargetId(SystemLogSanitizer.truncate(request.targetId(), 120));
        entry.setDurationMs(request.durationMs());
        entry.setMessage(SystemLogSanitizer.truncate(SystemLogSanitizer.maskText(request.message())));
        entry.setDetailsJson(toDetailsJson(request.details()));
        entry.setExceptionClass(SystemLogSanitizer.truncate(request.exceptionClass(), 255));
        entry.setStackTrace(SystemLogSanitizer.truncateStackTrace(request.stackTrace()));
        return entry;
    }

    private String toDetailsJson(Object details) {
        if (details == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(SystemLogSanitizer.sanitizeDetails(details));
            return SystemLogSanitizer.truncateDetails(json);
        } catch (Exception ex) {
            return SystemLogSanitizer.truncateDetails(SystemLogSanitizer.maskText(String.valueOf(details)));
        }
    }

    private SystemLogListResponse toListResponse(SystemLogEntry entry) {
        return new SystemLogListResponse(
                entry.getId(),
                entry.getOccurredAt(),
                entry.getCategory(),
                entry.getSeverity(),
                entry.getStatus(),
                entry.getAction(),
                entry.getSource(),
                entry.getActorUsername(),
                entry.getCorrelationId(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getDurationMs(),
                entry.getMessage(),
                entry.getExceptionClass()
        );
    }

    private SystemLogDetailResponse toDetailResponse(SystemLogEntry entry) {
        return new SystemLogDetailResponse(
                entry.getId(),
                entry.getOccurredAt(),
                entry.getCategory(),
                entry.getSeverity(),
                entry.getStatus(),
                entry.getAction(),
                entry.getSource(),
                entry.getActorUsername(),
                entry.getCorrelationId(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getDurationMs(),
                entry.getMessage(),
                entry.getDetailsJson(),
                entry.getExceptionClass(),
                entry.getStackTrace()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
