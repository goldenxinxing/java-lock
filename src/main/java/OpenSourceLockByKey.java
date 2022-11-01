import com.google.common.util.concurrent.Striped;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;

/**
 * https://yanbin.blog/google-guava-striped-key-based-fine-grain-locks/
 * https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/Striped.java
 */
public class OpenSourceLockByKey {
    static Striped<Lock> locks = Striped.lock(1);
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        String key = "key";
        ExecutorService executor = Executors.newFixedThreadPool(3);
        Future<Long> f = executor.submit(new CallableD(key, false));
        Thread.sleep(10);
        Future<Long> f2 = executor.submit(new CallableD(key, true));
        Long end1 = f.get();
        Long end2 = f2.get();
        System.out.println(end2 >= end1);
        executor.shutdownNow();


    }
}
class CallableD implements Callable<Long> {
    String key;
    boolean returnStart;
    CallableD(String key, boolean returnStart) {
        this.key = key;
        this.returnStart = returnStart;
    }

    @Override
    public Long call() throws Exception {
        Lock l = OpenSourceLockByKey.locks.get(key);
        l.lock();

        try {
            long start = System.currentTimeMillis();
            System.out.printf("%s start at %s, lock key is %s%n", Thread.currentThread(), start, key);
            Thread.sleep(1000);
            // insert the code that needs to be executed only if the key lock is available
            return returnStart ? start : System.currentTimeMillis();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally { // CRUCIAL
            l.unlock();
            System.out.printf("%s end at %s%n", Thread.currentThread(), System.currentTimeMillis());
        }
    }
}