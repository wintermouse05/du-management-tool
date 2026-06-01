package org.example.dumanagementbackend.logging;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.repository.SystemLogRepository;
import org.example.dumanagementbackend.service.SystemLogCreateRequest;
import org.example.dumanagementbackend.service.SystemLogService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class SystemLogRepositoryAspect {

    private final SystemLogService systemLogService;

    @Around("""
            (execution(* org.example.dumanagementbackend.repository..*.save*(..))
             || execution(* org.example.dumanagementbackend.repository..*.delete*(..))
             || @annotation(org.springframework.data.jpa.repository.Modifying))
            && !target(org.example.dumanagementbackend.repository.SystemLogRepository)
            """)
    public Object captureRepositoryMutation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object target = joinPoint.getTarget();
        if (target instanceof SystemLogRepository) {
            return joinPoint.proceed();
        }

        long startedAt = System.nanoTime();
        Throwable failure = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            logMutation(joinPoint, result, failure, startedAt);
        }
    }

    private void logMutation(ProceedingJoinPoint joinPoint, Object result, Throwable failure, long startedAt) {
        String method = joinPoint.getSignature().getName();
        Object primary = result != null ? result : firstArgument(joinPoint.getArgs());
        String targetType = resolveTargetType(primary);
        String targetId = resolveTargetId(primary);
        String source = joinPoint.getSignature().getDeclaringType().getSimpleName();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("repository", joinPoint.getSignature().getDeclaringTypeName());
        details.put("method", method);
        details.put("argumentCount", joinPoint.getArgs() != null ? joinPoint.getArgs().length : 0);

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.DATABASE,
                failure == null ? SystemLogSeverity.INFO : SystemLogSeverity.ERROR,
                failure == null ? SystemLogStatus.SUCCESS : SystemLogStatus.FAILED,
                method,
                source,
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                targetType,
                targetId,
                durationMs,
                source + "." + method + (failure == null ? " completed" : " failed"),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }

    private Object firstArgument(Object[] args) {
        return args != null && args.length > 0 ? args[0] : null;
    }

    private String resolveTargetType(Object value) {
        Object target = unwrapIterable(value);
        if (target == null) {
            return null;
        }
        if (target instanceof Number || target instanceof String) {
            return "id";
        }
        return target.getClass().getSimpleName();
    }

    private String resolveTargetId(Object value) {
        Object target = unwrapIterable(value);
        if (target == null) {
            return null;
        }
        if (target instanceof Number || target instanceof String) {
            return String.valueOf(target);
        }
        try {
            Method method = target.getClass().getMethod("getId");
            Object id = method.invoke(target);
            return id != null ? String.valueOf(id) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object unwrapIterable(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext() ? iterable.iterator().next() : null;
        }
        return value;
    }
}
