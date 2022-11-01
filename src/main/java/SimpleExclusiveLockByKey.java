import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleExclusiveLockByKey {

    private static final Set<String> usedKeys = ConcurrentHashMap.newKeySet();

    public boolean tryLock(String key) {
        return usedKeys.add(key);
    }

    public void unlock(String key) {
        usedKeys.remove(key);
    }


    public static SimpleExclusiveLockByKey lockByKey = new SimpleExclusiveLockByKey();

    public static void main(String[] args) throws InterruptedException {
        String key = "key";
        Thread t1 = new Thread(new RunableA(key));
        Thread t2 = new Thread(new RunableA(key));
        t1.setDaemon(false);
        t2.setDaemon(false);

        t1.start();
        Thread.sleep(100);
        t2.start();
    }
}

class RunableA implements Runnable {
    String key;

    RunableA(String key) {
        this.key = key;
    }

    @Override
    public void run() {
        try {
            SimpleExclusiveLockByKey.lockByKey.tryLock(key);
            System.out.printf("%s start at %s, lock key is %s%n", Thread.currentThread(), System.currentTimeMillis(), key);
            Thread.sleep(1000);
            // insert the code that needs to be executed only if the key lock is available
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally { // CRUCIAL
            SimpleExclusiveLockByKey.lockByKey.unlock(key);
            System.out.printf("%s end at %s%n", Thread.currentThread(), System.currentTimeMillis());
        }
    }
}