import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * 实现的不太好，不是原子的
 */
public class SimultaneousEntriesLockByKey {

    private static final int ALLOWED_THREADS = 1;
    
    private static ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<String, Semaphore>();
    
    public void lock(String key) {
        Semaphore semaphore = semaphores.compute(key, (k, v) -> v == null ? new Semaphore(ALLOWED_THREADS) : v);
        semaphore.acquireUninterruptibly();
    }
    
    public void unlock(String key) {
        Semaphore semaphore = semaphores.get(key);
        semaphore.release();
        // 还是不能保证原子性
        if (!semaphore.hasQueuedThreads() && semaphore.availablePermits() == ALLOWED_THREADS) {
            System.out.printf("%s remove from map%n", Thread.currentThread());
            semaphores.remove(key, semaphore);
        }

    }

    static SimultaneousEntriesLockByKey lockByKey = new SimultaneousEntriesLockByKey();

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        String key = "key";
        ExecutorService executor = Executors.newFixedThreadPool(3);
        Future<Long> f = executor.submit(new CallableC(key, false));
        Thread.sleep(10);
        Future<Long> f2 = executor.submit(new CallableC(key, true));
        Long end1 = f.get();
        Long end2 = f2.get();
        System.out.println(end2 >= end1);
        executor.shutdownNow();
    }
    
}

class CallableC implements Callable<Long> {
    String key;
    boolean returnStart;
    CallableC(String key, boolean returnStart) {
        this.key = key;
        this.returnStart = returnStart;
    }

    @Override
    public Long call() throws Exception {
        try {
            SimultaneousEntriesLockByKey.lockByKey.lock(key);
            long start = System.currentTimeMillis();
            System.out.printf("%s start at %s, lock key is %s%n", Thread.currentThread(), start, key);
            Thread.sleep(1000);
            // insert the code that needs to be executed only if the key lock is available
            return returnStart ? start : System.currentTimeMillis();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally { // CRUCIAL
            SimultaneousEntriesLockByKey.lockByKey.unlock(key);
            System.out.printf("%s end at %s%n", Thread.currentThread(), System.currentTimeMillis());
        }
    }
}