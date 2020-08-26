package me.mrs.aop;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.matcher.Matchers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.mrs.*;
import org.assertj.core.api.WithAssertions;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Singleton;

@Slf4j
public class AopCacheableServiceTest implements WithAssertions {
    @Inject
    private UppercaseService target;

    @Before
    public void setup() {
        final var injector = Guice.createInjector(new AopModule());
        injector.injectMembers(this);
        log.trace("Test started");
    }

    @Test
    public void testInvoke() throws InterruptedException {
        var v1 = target.apply("text");
        log.info("v1 = {}", v1);
        Thread.sleep(10L);
        var v2 = target.apply("text");
        log.info("v2 = {}", v2);

        assertThat(v1).isEqualTo(v2)
                .containsPattern("[0-9]Z:");
    }

    @Test
    public void testInvokeWithPrefix() throws InterruptedException {

        var v1 = target.applyWithPrefix("text", "Prefix");
        log.info("v1 = {}", v1);
        Thread.sleep(10L);
        var v2 = target.applyWithPrefix("text", "Prefix");
        log.info("v2 = {}", v2);

        assertThat(v1).isEqualTo(v2)
                .contains("Prefix")
                .containsPattern("[0-9]");
    }
}

@Slf4j
class AopModule extends AbstractModule {
    @SneakyThrows
    @Override
    protected void configure() {
        bind(UppercaseService.class).to(MyService.class);
        bind(CacheService.class).to(MemoryCache.class)
                .in(Singleton.class);

        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Cacheable.class), new AopCacheInterceptorService(getProvider(CacheService.class)));
    }
}

