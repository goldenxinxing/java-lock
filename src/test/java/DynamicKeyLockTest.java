import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DynamicKeyLockTest
{
    String key1 = "1";
    String key2 = "2";
    ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    public void tearDown() {
        this.executor.shutdownNow();
    }

    @Test
    public void testUseDifferentKeysLock() throws ExecutionException, InterruptedException, TimeoutException {
        DynamicKeyLock<Object> lock = new DynamicKeyLock<>(key1);
        lock.lock();
        AtomicBoolean anotherThreadWasExecutedBeforeLock = new AtomicBoolean(false);
        AtomicBoolean anotherThreadWasExecutedAfterLock = new AtomicBoolean(false);
        try {
            Future<?> x = executor.submit(() -> {
                DynamicKeyLock<Object> anotherLock = new DynamicKeyLock<>(key2);
                anotherThreadWasExecutedBeforeLock.set(true);
                anotherLock.lock();
                try {
                    anotherThreadWasExecutedAfterLock.set(true);
                } finally {
                    anotherLock.unlock();
                }
                return "success";
            });

            Assertions.assertEquals("success", x.get(1000, TimeUnit.MICROSECONDS));
        } finally {
            Assertions.assertTrue(anotherThreadWasExecutedBeforeLock.get());
            Assertions.assertTrue(anotherThreadWasExecutedAfterLock.get());
            lock.unlock();
        }
    }

    @Test
    public void testUseSameKeysLock() {
        DynamicKeyLock<Object> lock = new DynamicKeyLock<>(key1);
        lock.lock();
        AtomicBoolean anotherThreadWasExecutedBeforeLock = new AtomicBoolean(false);
        AtomicBoolean anotherThreadWasUnExecutedAfterLock = new AtomicBoolean(false);
        try {
            Future<?> x = executor.submit(() -> {
                DynamicKeyLock<Object> anotherLock = new DynamicKeyLock<>(key1);
                anotherThreadWasExecutedBeforeLock.set(true);
                anotherLock.lock();
                try {
                    anotherThreadWasUnExecutedAfterLock.set(true);
                } finally {
                    anotherLock.unlock();
                }
                return "success";
            });
            Assertions.assertThrows(TimeoutException.class, () -> x.get(1000, TimeUnit.MICROSECONDS));

        } finally {
            Assertions.assertTrue(anotherThreadWasExecutedBeforeLock.get());
            Assertions.assertFalse(anotherThreadWasUnExecutedAfterLock.get());
            lock.unlock();
        }
    }

    @Test
    public void testMultiThreadsUseSameDynamicKeyLock() throws InterruptedException, ExecutionException {
        class TestCallable implements Callable<Long> {
            final String key;
            final boolean returnStart;

            TestCallable(String key, boolean returnStart) {
                this.key = key;
                this.returnStart = returnStart;
            }

            @Override
            public Long call() throws Exception {
                DynamicKeyLock<Object> lock = new DynamicKeyLock<>(key);
                lock.lock();

                try {
                    long start = System.currentTimeMillis();
                    // mock execute process
                    Thread.sleep(100);
                    return returnStart ? start : System.currentTimeMillis();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally { // CRUCIAL
                    lock.unlock();
                }
            }
        }

        Future<Long> first = executor.submit(new TestCallable(key1, false));
        Thread.sleep(10);
        Future<Long> second = executor.submit(new TestCallable(key1, true));

        Assertions.assertTrue(second.get() >= first.get());
    }
}