package me.mrs.proxy;

import lombok.extern.slf4j.Slf4j;
import me.mrs.CacheService;
import me.mrs.Cacheable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
public class CachedProxyService implements InvocationHandler {
    private final Provider<CacheService> cacheService;
    private final Map<Method, List<Integer>> parametersIndexMap;
    private final Object target;

    public CachedProxyService(final Provider<CacheService> cacheService, final Object target) {
        parametersIndexMap = new ConcurrentHashMap<>();
        this.cacheService = cacheService;
        this.target = target;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    public static <T> T instance(@Nonnull T base, @Nonnull final Provider<CacheService> cacheService, final Class<T>... interfaces) {
        return (T) Proxy.newProxyInstance(base.getClass()
                .getClassLoader(), interfaces, new CachedProxyService(cacheService, base));
    }

    @Override
    public Object invoke(@Nonnull final Object me, @Nonnull final Method method, @Nonnull final Object[] arguments) throws Throwable {
        var targetMethod = target.getClass()
                .getMethod(method.getName(), method.getParameterTypes());

        @Nullable final var annotation = targetMethod.getAnnotation(Cacheable.class);
        if (annotation == null) {
            return method.invoke(target, arguments);
        }

        var key = createKey(annotation, targetMethod, arguments);

        return cacheService.get()
                .apply(annotation.ttl(), key, keyResolver(method, arguments));
    }

    private Function<String, Object> keyResolver(final Method method, final Object[] arguments) {
        return k -> {
            try {
                log.trace("Retrieving key {}", k);
                return method.invoke(target, arguments);
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.error("Can't invoke method {}", method, e);
                throw new CacheRuntimeException(e);
            }
        };
    }

    private String createKey(@Nonnull final Cacheable annotation, @Nonnull final Method targetMethod, @Nonnull final Object[] arguments) {
        var cachePrefix = annotation.namespace();
        var argumentsIndex = parametersIndexMap.computeIfAbsent(targetMethod, CachedProxyService::extractParametersIndex);
        return createKey(cachePrefix, arguments, argumentsIndex);
    }

    private static String createKey(final String cachePrefix, final Object[] arguments, final List<Integer> argumentsIndex) {
        var sb = new StringBuilder(cachePrefix);
        for (var i : argumentsIndex) {
            sb.append('.')
                    .append(arguments[i]);
        }
        return sb.toString();
    }

    @SuppressWarnings("java:S881")
    private static List<Integer> extractParametersIndex(final Method serviceMethod) {
        final Annotation[][] annotations = serviceMethod.getParameterAnnotations();
        var index = new ArrayList<Integer>(annotations.length);
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
        return index;
    }

    private static class CacheRuntimeException extends RuntimeException {
        public CacheRuntimeException(final ReflectiveOperationException e) {
            super(e);
        }
    }
}
