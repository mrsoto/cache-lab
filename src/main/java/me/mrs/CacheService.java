package me.mrs;

import javax.annotation.Nonnull;
import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
public interface CacheService {
    Object apply(long ttl, @Nonnull String key, @Nonnull Function<String, Object> resolver);

    default BiFunction<String, Function<String, Object>, Object> withTtl(long ttl) {
        return (k, r) -> apply(ttl, k, r);
    }
}
