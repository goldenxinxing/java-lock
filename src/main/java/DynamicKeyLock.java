import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * https://stackoverflow.com/questions/11124539/how-to-acquire-a-lock-by-a-key
 *
 * @param <T>
 */
public class DynamicKeyLock<T> implements Lock {
    private static final ConcurrentHashMap<Object, LockAndCounter> locksMap = new ConcurrentHashMap<>();

    private final T key;

    public DynamicKeyLock(T lockKey) {
        this.key = lockKey;
    }

    private static class LockAndCounter {
        private final Lock lock = new ReentrantLock();
        private final AtomicInteger counter = new AtomicInteger(0);
    }

    private LockAndCounter getLock() {
        return locksMap.compute(key, (key, lockAndCounterInner) -> {
            if (lockAndCounterInner == null) {
                lockAndCounterInner = new LockAndCounter();
            }
            lockAndCounterInner.counter.incrementAndGet();
            return lockAndCounterInner;
        });
    }

    private void cleanup(LockAndCounter lockAndCounterOuter) {
        if (lockAndCounterOuter.counter.decrementAndGet() == 0) {
            locksMap.compute(key, (key, lockAndCounterInner) -> {
                if (lockAndCounterInner == null || lockAndCounterInner.counter.get() == 0) {
                    return null;
                }
                return lockAndCounterInner;
            });
        }
    }

    @Override
    public void lock() {
        LockAndCounter lockAndCounter = getLock();

        lockAndCounter.lock.lock();
    }

    @Override
    public void unlock() {
        LockAndCounter lockAndCounter = locksMap.get(key);
        lockAndCounter.lock.unlock();

        cleanup(lockAndCounter);
    }


    @Override
    public void lockInterruptibly() throws InterruptedException {
        LockAndCounter lockAndCounter = getLock();

        try {
            lockAndCounter.lock.lockInterruptibly();
        } catch (InterruptedException e) {
            cleanup(lockAndCounter);
            throw e;
        }
    }

    @Override
    public boolean tryLock() {
        LockAndCounter lockAndCounter = getLock();

        boolean acquired = lockAndCounter.lock.tryLock();

        if (!acquired) {
            cleanup(lockAndCounter);
        }

        return acquired;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        LockAndCounter lockAndCounter = getLock();

        boolean acquired;
        try {
            acquired = lockAndCounter.lock.tryLock(time, unit);
        } catch (InterruptedException e) {
            cleanup(lockAndCounter);
            throw e;
        }

        if (!acquired) {
            cleanup(lockAndCounter);
        }

        return acquired;
    }

    @Override
    public Condition newCondition() {
        LockAndCounter lockAndCounter = locksMap.get(key);

        return lockAndCounter.lock.newCondition();
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        String key = "key";
        ExecutorService executor = Executors.newFixedThreadPool(3);
        Future<Long> f = executor.submit(new CallableE(key, false));
        Thread.sleep(10);
        Future<Long> f2 = executor.submit(new CallableE(key, true));
        Long end1 = f.get();
        Long end2 = f2.get();
        System.out.println(end2 >= end1);
        executor.shutdownNow();
    }
}

class CallableE implements Callable<Long> {
    String key;
    boolean returnStart;

    CallableE(String key, boolean returnStart) {
        this.key = key;
        this.returnStart = returnStart;
    }

    @Override
    public Long call() throws Exception {
        DynamicKeyLock<Object> lock = new DynamicKeyLock<>(key);
        lock.lock();

        try {
            long start = System.currentTimeMillis();
            System.out.printf("%s start at %s, lock key is %s%n", Thread.currentThread(), start, key);
            Thread.sleep(1000);
            // insert the code that needs to be executed only if the key lock is available
            return returnStart ? start : System.currentTimeMillis();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally { // CRUCIAL
            lock.unlock();
            System.out.printf("%s end at %s%n", Thread.currentThread(), System.currentTimeMillis());
        }
    }
}