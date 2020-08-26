package me.mrs.aop;

import lombok.extern.slf4j.Slf4j;
import me.mrs.CacheService;
import me.mrs.Cacheable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.annotation.Nullable;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
public class AopCacheInterceptorService implements MethodInterceptor {
    private final Provider<CacheService> cacheServiceProvider;
    private final Map<Method, List<Integer>> parametersIndexMap;

    public AopCacheInterceptorService(final Provider<CacheService> cacheServiceProvider) {
        parametersIndexMap = new ConcurrentHashMap<>();
        this.cacheServiceProvider = cacheServiceProvider;
    }

    @Override
    public Object invoke(final MethodInvocation methodInvocation) throws Throwable {
        var method = (Method) methodInvocation.getStaticPart();

        @Nullable final Cacheable annotation = method.getAnnotation(Cacheable.class);
        if (annotation == null) {
            return methodInvocation.proceed();
        }

        var arguments = methodInvocation.getArguments();
        var key = createKey(method, arguments, annotation);
        var ttl = annotation.ttl();

        return cacheServiceProvider.get()
                .apply(ttl, key, valueResolver(methodInvocation, method));
    }

    private Function<String, Object> valueResolver(final MethodInvocation methodInvocation, final Method method) {
        return k -> {
            try {
                log.trace("Retrieving key {}", k);
                return methodInvocation.proceed();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                log.error("Can't invoke method {}", method, e);
                throw new CacheException(e);
            }
        };
    }

    private String createKey(final Method method, final Object[] arguments, final Cacheable annotation) {
        var cachePrefix = annotation.namespace();
        var argumentsIndex = parametersIndexMap.computeIfAbsent(method, AopCacheInterceptorService::extractParametersIndex);
        return createKey(cachePrefix, arguments, argumentsIndex);
    }

    private static String createKey(final String cachePrefix, final Object[] arguments, final List<Integer> argumentsIndex) {
        final var sb = new StringBuilder(cachePrefix);
        for (var i : argumentsIndex) {
            sb.append('.')
                    .append(arguments[i]);
        }
        return sb.toString();
    }

    @SuppressWarnings("java:S881")
    private static List<Integer> extractParametersIndex(final Method serviceMethod) {
        final Annotation[][] annotations = serviceMethod.getParameterAnnotations();
        final List<Integer> index = new ArrayList<>(annotations.length);

        for (int pi = annotations.length; --pi >= 0; ) {
            for (int ai = annotations[pi].length; --ai >= 0; ) {
                if (annotations[pi][ai].annotationType() == Cacheable.Key.class) {
                    index.add(pi);
                    break;
                }
            }
        }

        if (index.isEmpty()) {
            for (int pi = annotations.length; --pi >= 0; ) {
                index.add(pi);
            }
        }

        assert !index.isEmpty() : "Key arguments required";

        return index;
    }

    private static class CacheException extends RuntimeException {
        public CacheException(final Throwable e) {
            super(e);
        }
    }
}
