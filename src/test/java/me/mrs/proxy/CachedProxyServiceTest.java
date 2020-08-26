package me.mrs.proxy;

import lombok.extern.slf4j.Slf4j;
import me.mrs.CacheService;
import me.mrs.MemoryCache;
import me.mrs.MyService;
import me.mrs.UppercaseService;
import org.assertj.core.api.WithAssertions;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Provider;

@Slf4j
public class CachedProxyServiceTest implements WithAssertions {
    CacheService cacheService;
    Provider<CacheService> cacheServiceProvider = () -> cacheService;

    @Before
    public void setup() {
        cacheService = new MemoryCache();
        log.trace("Test started");
    }

    @Test
    public void testInvoke() throws InterruptedException {
        UppercaseService myService = new MyService();
        @SuppressWarnings("unchecked") UppercaseService cachedService = CachedProxyService.instance(myService, cacheServiceProvider, UppercaseService.class);

        var v1 = cachedService.apply("text");
        log.info("v1 = {}", v1);
        Thread.sleep(10L);
        var v2 = cachedService.apply("text");
        log.info("v2 = {}", v2);

        assertThat(v1).isEqualTo(v2)
                .containsPattern("[0-9]Z:");
    }

    @Test
    public void testInvokeWithPrefix() throws InterruptedException {
        var myService = new MyService();
        @SuppressWarnings("unchecked") UppercaseService cachedService = CachedProxyService.instance(myService, cacheServiceProvider, UppercaseService.class);

        var v1 = cachedService.applyWithPrefix("text", "Prefix");
        log.info("v1 = {}", v1);
        Thread.sleep(10L);
        var v2 = cachedService.applyWithPrefix("text", "Prefix");
        log.info("v2 = {}", v2);
        var v3 = cachedService.applyWithPrefix("text", "X-Prefix");
        log.info("v3 = {}", v3);

        assertThat(v1).isEqualTo(v2)
                .isEqualTo(v3)
                .contains("Prefix")
                .containsPattern("[0-9]");
    }
}
