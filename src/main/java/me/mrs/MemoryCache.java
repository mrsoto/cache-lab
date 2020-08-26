package me.mrs;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.function.Function;

@Slf4j
public class MemoryCache implements CacheService {
    private final HashMap<String, Object> cache = new HashMap<>();

    @Override
    public Object apply(final long ttl, final String key, Function<String, Object> resolver) {
        return cache.computeIfAbsent(key, resolver);
    }
}
