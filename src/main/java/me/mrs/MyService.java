package me.mrs;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class MyService implements Runnable, UppercaseService {

    @Override
    public void run() {
        log.info("Ejecutando MyService");
    }

    @Override
    @Cacheable(namespace = "cache1", ttl = 20)
    public String apply(String source) {
        log.trace("apply: {}", source);
        return Instant.now()
                .toString() + ":" + String.valueOf(source)
                .toUpperCase();
    }

    @Override
    @Cacheable(namespace = "cache2", ttl = 20)
    public String applyWithPrefix(@Cacheable.Key final String source, final String prefix) {
        log.trace("applyWithPrefix: {}", source);
        return prefix + '@' + Instant.now()
                .toString() + ":" + String.valueOf(source)
                .toUpperCase();
    }
}
