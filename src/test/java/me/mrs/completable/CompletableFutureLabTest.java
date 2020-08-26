package me.mrs.completable;

import org.assertj.core.api.BDDSoftAssertions;
import org.assertj.core.api.WithAssertions;
import org.assertj.core.util.Arrays;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class CompletableFutureLabTest implements WithAssertions {

    public static final String FIRST_1 = "first 1";
    public static final String SECOND_2 = "second 2";
    private ForkJoinPool executor;

    @Before
    public void setUp() {
        executor = new ForkJoinPool(10, ForkJoinPool.defaultForkJoinWorkerThreadFactory, (a, b) -> {
        }, true);
    }

    @Test
    public void completableComplete() {
        var signal = new CompletableFuture<>();
        var greeting = signal.thenApply(v -> "Hello world");

        assertThat(signal.isDone()).isFalse()
                .describedAs("signal is waiting");
        assertThat(greeting.isDone()).isFalse()
                .describedAs("greeting is waiting");

        signal.complete(null);

        BDDSoftAssertions.thenSoftly(softly -> {
            softly.then(signal.isDone())
                    .isTrue()
                    .describedAs("signal done");
            softly.then(greeting.isDone())
                    .isTrue();
            softly.thenCode(() -> softly.then(greeting.get(0, TimeUnit.SECONDS))
                    .isEqualTo("Hello world"))
                    .doesNotThrowAnyException();
        });
    }

    @Test(timeout = 10L)
    public void syncCompleteInOrder() {
        var signal = new CompletableFuture<Void>();
        var index = new AtomicInteger(0);

        var first = signal.thenApply(n -> "first " + index.incrementAndGet());
        var second = signal.thenApply(n -> "second " + index.incrementAndGet());

        signal.complete(null);

        assertThat(first.join()).isEqualTo("first 2");
        assertThat(second.join()).isEqualTo("second 1");
    }

    @Test(timeout = 200L)
    // sometimes fails
    public void syncCompleteInOrderAsync() {
        var signal = new CompletableFuture<Void>();
        var index = new AtomicInteger(0);

        var first = signal.thenApplyAsync(n -> "first " + index.incrementAndGet());
        var second = signal.thenApplyAsync(n -> "second " + index.incrementAndGet());

        signal.complete(null);
        var futures = Arrays.array(first, second);
        CompletableFuture.allOf(futures)
                .join();

        assertThatCode(() -> {
            assertThat(futures).extracting(Future::get)
                    .containsExactly(FIRST_1, SECOND_2);
        }).doesNotThrowAnyException();
    }

    @Test(timeout = 10L)
    public void syncCompleteInOrderAsyncCheckConsistency() {
        var signal = new CompletableFuture<Void>();
        var index = new AtomicInteger(0);

        var first = signal.thenApplyAsync(n -> "first " + index.incrementAndGet(), executor);
        var second = signal.thenApplyAsync(n -> "second " + index.incrementAndGet(), executor);

        signal.complete(null);
        CompletableFuture.allOf(first, second)
                .join();

        assertThat(first.join()).isEqualTo(FIRST_1);
        assertThat(second.join()).isEqualTo(SECOND_2);
    }

    @Test(timeout = 200L)
    public void syncCompleteInOrderAsyncAsyncTask() {
        var signal = new CompletableFuture<Void>();
        var index = new AtomicInteger(0);

        var first = signal.thenApplyAsync(new Task("first", index), executor);
        var second = signal.thenApplyAsync(n -> "second " + index.incrementAndGet(), executor);

        signal.complete(null);
        CompletableFuture.allOf(first, second)
                .join();

        assertThat(first.join()).isEqualTo(FIRST_1);
        assertThat(second.join()).isEqualTo(SECOND_2);
    }

    @Test
    public void syncCompleteInOrderAsyncAsyncTaskConsistency() throws InterruptedException, ExecutionException, TimeoutException {
        var signal = new CompletableFuture<Void>();
        var index = new AtomicInteger(0);

        var first = signal.thenApplyAsync(new Task("first", index), executor);
        var second = signal.thenApplyAsync(new Task("second", index), executor);

        signal.complete(null);
        CompletableFuture.allOf(first, second)
                .join();

        assertThat(first.get(0, TimeUnit.SECONDS)).isEqualTo(FIRST_1);
        assertThat(second.get(0, TimeUnit.SECONDS)).isEqualTo(SECOND_2);
    }

    @Test
    public void oneOfTwoFailing() {
        var f1 = CompletableFuture.failedFuture(new RuntimeException("Fail"));
        var f2 = new CompletableFuture<Integer>();

        var finish = CompletableFuture.allOf(f1, f2);
        assertThatThrownBy(() -> finish.get(1L, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    @Test(timeout = 10L)
    public void oneFailingOneSuccess() {
        var f1 = CompletableFuture.failedFuture(new RuntimeException("Fail"));
        var f2 = CompletableFuture.completedFuture(1);

        var finish = CompletableFuture.allOf(f1, f2);

        assertThat(finish).hasFailed();
        assertThat(finish).isCompletedExceptionally();
    }

    @Test
    public void oneOfTwoCanceled() {
        var f0 = new CompletableFuture<Integer>();
        var f1 = f0.thenApply(x -> 2 * x);
        var f2 = new CompletableFuture<Integer>();
        f2.cancel(true);
        var finish = f1.applyToEither(f2, Function.identity());

        assertThatThrownBy(finish::get).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CancellationException.class);
        assertThat(f0.isCancelled()).isFalse();
        assertThat(f0.isDone()).isFalse();
        assertThat(f1.isCancelled()).isFalse();
        assertThat(f1.isDone()).isFalse();
        assertThat(f2.isCancelled()).isTrue();
    }

    @Test
    public void oneOfTwoTimedOut() throws InterruptedException {
        var f0 = new CompletableFuture<Integer>();
        var f1 = f0.thenApply(x -> 2 * x);
        var f2 = new CompletableFuture<Integer>().orTimeout(0, TimeUnit.MILLISECONDS);
        Thread.sleep(10L);
        var finish = f1.applyToEither(f2, Function.identity());

        assertThatThrownBy(finish::get).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(f0.isCancelled()).isFalse();
        assertThat(f0.isDone()).isFalse();
        assertThat(f1.isCancelled()).isFalse();
        assertThat(f1.isDone()).isFalse();
        assertThat(f2.isCancelled()).isFalse();
    }

    static class Task implements Function<Void, String>, CompletableFuture.AsynchronousCompletionTask {
        private final String label;
        private final AtomicInteger index;

        public Task(final String label, final AtomicInteger index) {
            this.label = label;
            this.index = index;
        }

        @Override
        public String apply(Void n) {
            return label + " " + index.incrementAndGet();
        }
    }
}
