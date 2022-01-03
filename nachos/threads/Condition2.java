package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * re-acquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		
		waitQueue.waitForAccess(KThread.currentThread());
		
		conditionLock.release();  // release the lock before sleeping
		
		// the scheduler has to work atomically
		KThread.sleep();
		Machine.interrupt().restore(intStatus);

		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();  
		
		// we have to disable interrupts for the scheduler
		KThread thread = waitQueue.nextThread();
		if (thread != null) {
			thread.ready();
		}
		
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();
		
		KThread thread = waitQueue.nextThread();
		while(thread != null) {
			thread.ready();
			thread = waitQueue.nextThread();
		}
		
		Machine.interrupt().restore(intStatus);
	}

     /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically re-acquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
	// We can put the thread back to the ready queue when it has waited to long in the CV queue.
	// The scheduling policy can then be modified with this function.
    public void sleepFor(long timeout) {
    	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    	
    	boolean intStatus = Machine.interrupt().disable();
    	
    	conditionLock.release();
		waitQueue.waitForAccess(KThread.currentThread());  // wait on the condition variable
		ThreadedKernel.alarm.waitUntil(timeout);  // wait in the time queue (sleeps after this function is called)
		
		/** Thread Woken Here **/
		// If the thread is woken by the condition variable, cancel its timer.
		// Otherwise, remove itself from condition variable queue.
		boolean wokenByTimeout = !ThreadedKernel.alarm.cancel(KThread.currentThread());
		if (wokenByTimeout) {  // remove the thread in the CV queue 
			KThread topThread = waitQueue.nextThread();  // the first thread in the priority queue
			while (topThread != KThread.currentThread()){  // check if it is the target thread
				waitQueue.waitForAccess(topThread);
				topThread = waitQueue.nextThread();
			}
		}
		conditionLock.acquire();
		
		Machine.interrupt().restore(intStatus);
	}

    // Example of the interlock pattern where two threads strictly alternate their execution with each other 
    // using a condition variable.  (Also see the slide showing this pattern at the end of Lecture 6.)
    private static class InterlockTest {
        private static Lock lock;
        private static Condition2 cv;

        private static class Interlocker implements Runnable {
            public void run () {
                lock.acquire();
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
            cv = new Condition2(lock);

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
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
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
    
    private static void sleepForTest1 () {
    	Lock lock = new Lock();
    	Condition2 cv = new Condition2(lock);

    	lock.acquire();
    	long t0 = Machine.timer().getTime();
    	System.out.println (KThread.currentThread().getName() + " sleeping");
    	
    	// no other threads will wake it up, so timeout should happen
    	cv.sleepFor(2000);
    	long t1 = Machine.timer().getTime();
    	System.out.println (KThread.currentThread().getName() + " woke up, slept for " + (t1 - t0) + " ticks");
    	lock.release();
        }
    
    // test if the sleeping thread can be awaken by the wake function
    private static void sleepForTest2 () {
        Lock lock = new Lock();
        Condition2 cv = new Condition2(lock);

        lock.acquire();
        KThread wakeSleeper = new KThread( new Runnable () {
            public void run() {
                ThreadedKernel.alarm.waitUntil(500);
                lock.acquire();
				System.out.println("About to wake main: " + Machine.timer().getTime());
                cv.wake();
				System.out.println("wakeSleep finished running: " + Machine.timer().getTime());
                lock.release();
            }
        });
        wakeSleeper.setName("wakeSleeper");
        wakeSleeper.fork();
        long t0 = Machine.timer().getTime();
        System.out.println (KThread.currentThread().getName() + " sleeping");
		System.out.println("About to place main to sleep: " + Machine.timer().getTime());
        cv.sleepFor(3000);
		System.out.println("Main just awoke: " + Machine.timer().getTime() + " (expected to sleep for fewer than 3000 ticks)");
        long t1 = Machine.timer().getTime();
        lock.release();
        System.out.println (KThread.currentThread().getName() + " woke up, slept for " + (t1 - t0) + " ticks");
    }
    
    // Invoke Condition2.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
//        new InterlockTest();
//        cvTest5();
//    	sleepForTest1();
    	sleepForTest2();
    }

    private Lock conditionLock;
    
    // Initialize a queue that can store threads
    private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
}
