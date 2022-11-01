import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * https://www.baeldung.com/java-acquire-lock-by-key
 */
public class LockByKey {
    
    private static class LockWrapper {
        private final Lock lock = new ReentrantLock();
        private final AtomicInteger numberOfThreadsInQueue = new AtomicInteger(1);
        
        private LockWrapper addThreadInQueue() {
            numberOfThreadsInQueue.incrementAndGet(); 
            return this;
        }
        
        private int removeThreadFromQueue() {
            return numberOfThreadsInQueue.decrementAndGet(); 
        }
        
    }
    
    private static final ConcurrentHashMap<String, LockWrapper> locks = new ConcurrentHashMap<String, LockWrapper>();
    
    public void lock(String key) {
        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        lockWrapper.lock.lock();
    }
    
    public void unlock(String key) {
        LockWrapper lockWrapper = locks.get(key);
        if (lockWrapper.removeThreadFromQueue() == 0) { 
            // NB : We pass in the specific value to remove to handle the case where another thread would queue right before the removal
            System.out.printf("%s remove from map%n", Thread.currentThread());
            locks.remove(key, lockWrapper);
        }
        lockWrapper.lock.unlock();
    }

    static LockByKey lockByKey = new LockByKey();
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        String key = "key";
        Thread t1 = new Thread(new RunableB(key));
        Thread t2 = new Thread(new RunableB(key));
//        t1.setDaemon(false);
//        t2.setDaemon(false);

        t1.start();
        Thread.sleep(100);
        t2.start();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        Future<Long> f = executor.submit(new CallableB(key, false));
        Thread.sleep(10);
        Future<Long> f2 = executor.submit(new CallableB(key, true));
        Long end1 = f.get();
        Long end2 = f2.get();
        System.out.println(end2 >= end1);
        executor.shutdown();
    }
    
}
class RunableB implements Runnable {
    String key;
    RunableB(String key) {
        this.key = key;
    }
    @Override
    public void run() {
        try {
            LockByKey.lockByKey.lock(key);
            System.out.printf("%s start at %s, lock key is %s%n", Thread.currentThread(), System.currentTimeMillis(), key);
            Thread.sleep(1000);
            // insert the code that needs to be executed only if the key lock is available
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally { // CRUCIAL
            LockByKey.lockByKey.unlock(key);
            System.out.printf("%s end at %s%n", Thread.currentThread(), System.currentTimeMillis());
        }
    }
}
class CallableB implements Callable<Long> {
    String key;
    boolean returnStart;
    CallableB(String key, boolean returnStart) {
        this.key = key;
        this.returnStart = returnStart;
    }

    @Override
    public Long call() throws Exception {
        try {
            LockByKey.lockByKey.lock(key);
            long start = System.currentTimeMillis();
            System.out.printf("%s start at %s, lock key is %s%n", Thread.currentThread(), start, key);
            Thread.sleep(1000);
            // insert the code that needs to be executed only if the key lock is available
            return returnStart ? start : System.currentTimeMillis();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally { // CRUCIAL
            LockByKey.lockByKey.unlock(key);
            System.out.printf("%s end at %s%n", Thread.currentThread(), System.currentTimeMillis());
        }
    }
}