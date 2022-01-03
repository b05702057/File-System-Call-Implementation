package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * An implementation of condition variables built upon semaphores.
 * 
 * <p>
 * A condition variable is a synchronization primitive that does not have a
 * value (unlike a semaphore or a lock), but threads may still be queued.
 * 
 * <p>
 * <ul>
 * 
 * <li><tt>sleep()</tt>: atomically release the lock and relinquish the CPU
 * until woken; then re-acquire the lock.
 * 
 * <li><tt>wake()</tt>: wake up a single thread sleeping in this condition variable, if possible.
 * 
 * <li><tt>wakeAll()</tt>: wake up all threads sleeping in this condition variable.
 * 
 * </ul>
 * 
 * <p>
 * Every condition variable is associated with some lock. 
 * Multiple condition variables may be associated with the same lock. 
 * All three condition variable operations can only be used while holding the associated lock.
 * 
 * <p>
 * In Nachos, condition variables are summed to obey <i>Mesa-style</i> semantics. 
 * When a <tt>wake()</tt> or <tt>wakeAll()</tt> wakes up another thread, 
 * the woken thread is simply put on the ready list, and it is the responsibility 
 * of the woken thread to re-acquire the lock (this re-acquire is taken core of in <tt>sleep()</tt>).
 * 
 * <p>
 * By contrast, some implementations of condition variables obey <i>Hoare-style</i> semantics, 
 * where the thread that calls <tt>wake()</tt> gives up the lock and the CPU to the woken thread, 
 * which runs immediately and gives the lock and CPU back to the waker when the woken thread exits the critical section.
 * 
 * <p>
 * The consequence of using Mesa-style semantics is that some other thread can acquire 
 * the lock and change data structures, before the woken thread gets a chance to run. 
 * The advance to Mesa-style semantics is that it is a lot easier to implement.
 * 
 * When a spin-lock is used, it causes busy-waiting (100% CPU usage).
 * Thus, we should use a mutex to block a thread if it cannot acquire the lock.
 * 
 * A thread can wait on a condition variable, and the resource producer can signal the variable, 
 * in which case the threads that wait for the condition variable get notified and can continue execution. 
 * A mutex is combined with a condition variable to avoid the race condition where a thread starts to wait on a CV at the same time another thread wants to signal it; 
 * then it is not controllable whether the signal is delivered or gets lost. 
 */
public class Condition {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition(Lock conditionLock) { 
		this.conditionLock = conditionLock;

		waitQueue = new LinkedList<Semaphore>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition variable until another thread wakes it using <tt>wake()</tt>. 
	 * The current thread must hold the associated lock. 
	 * The thread will automatically re-acquire the lock before <tt>sleep()</tt> returns.
	 * 
	 * <p>
	 * This implementation uses semaphores to implement this, by allocating a semaphore for each waiting thread. 
	 * The waker will <tt>V()</tt> this semaphore, so there is no chance the sleeper will miss the wake-up, 
	 * even though the lock is released before calling <tt>P()</tt>.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// It's a new semaphore each time we call sleep().
		// Thus, the thread always sleep and wait for the resources when we call sleep().
		Semaphore waiter = new Semaphore(0);
		waitQueue.add(waiter);

		// The current thread releases the lock before sleeping, so that another thread can acquire the lock.
		conditionLock.release();  
		waiter.P();  // The reason why we use a semaphore is because the thread can sleep on it atomically
		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. 
	 * The current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		if (!waitQueue.isEmpty())
			((Semaphore) waitQueue.removeFirst()).V();
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		while (!waitQueue.isEmpty())
			wake();
	}

	// Example of the interlock pattern where two threads strictly alternate their execution with each other 
    // using a condition variable. (Also see the slide showing this pattern at the end of Lecture 6.)
    private static class InterlockTest {
        private static Lock lock;
        private static Condition cv;

        private static class Interlocker implements Runnable {
            public void run () {
                lock.acquire();  // the condition lock is first acquired here
                for (int i = 0; i < 10; i++) {
                    System.out.println(KThread.currentThread().getName());
                    cv.wake();   // signal
                    cv.sleep();  // wait
                }
                lock.release();
            }
        }

        public InterlockTest () {
            lock = new Lock();
            cv = new Condition(lock);

            KThread ping = new KThread(new Interlocker());
            ping.setName("ping");
            KThread pong = new KThread(new Interlocker());
            pong.setName("pong");

            ping.fork();
            pong.fork();

            // We need to wait for ping to finish, and the proper way to do so is to join on ping.  
            // Note that, when ping is done, pong is sleeping on the condition variable; 
            // if we were also to join on pong, we would block forever.
            ping.join();
            
            // For the above to work, join must be implemented.  
            // If you have not implemented join yet, then comment out the call to join and instead uncomment the loop with yields; 
            // the below loop has the same effect, but is a kludgy way to do it.
            // for (int i = 0; i < 50; i++) { KThread.yield(); }
        }
    }
    
    public static void cvTest5() {
        final Lock lock = new Lock();
        final Condition empty = new Condition(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
        	public void run() {
        		lock.acquire();
        		while(list.isEmpty()){
        			empty.sleep();
        		}
        		Lib.assertTrue(list.size() == 5, "List should have 5 values.");
        		while(!list.isEmpty()) {
        			// context switch for fun
        			// The consumer is still holding the lock, so no other consumers or producers can modify the list.
        			KThread.yield();
        			System.out.println("Removed " + list.removeFirst());
        		}
        		lock.release();
        	}
        });

        KThread producer = new KThread( new Runnable () {
        	public void run() {
        		lock.acquire();
        		for (int i = 0; i < 5; i++) {
        			list.add(i);
        			System.out.println("Added " + i);
                    // context switch for fun
        			// The producer is still holding the lock, so no other consumers or producers can modify the list.
        			KThread.yield();
        		}
        		empty.wake();
        		lock.release();
        	}
        });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish, and the proper way to do so is to join on them.  
        // For this to work, join must be implemented.  
        consumer.join();
        producer.join();
        
        // If you have not implemented join yet, 
        // then comment out the calls to join and instead uncomment the below loop with yield; 
        // the loop has the same effect, but is a kludgy way to do it.
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }

    // Invoke Condition.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
        new InterlockTest();
        cvTest5();
    }
	
	private Lock conditionLock;

	// A condition variable also has its own wait queue, just like a semaphore.
	private LinkedList<Semaphore> waitQueue;
}
