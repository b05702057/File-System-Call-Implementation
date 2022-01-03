package nachos.threads;

import nachos.machine.*;
import java.util.*;  // for the library heap

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}
	
	class waitUntilThread  // a thread that has a waitUntilTime attribute
	{
	    public long wakeTime; 
	    public KThread thread;
	    public waitUntilThread(long wakeTime, KThread thread) {
	    	this.wakeTime = wakeTime;
	    	this.thread = thread;
	    }
	};
	
	public waitUntilThreadComparator heapComparator = new waitUntilThreadComparator();
	class waitUntilThreadComparator implements Comparator<waitUntilThread> {
		// override the compare()method of the comparator 
		public int compare(waitUntilThread t1, waitUntilThread t2) {
			return (int) (t1.wakeTime - t2.wakeTime);  // cast to an integer before returning the value
		}
	}
	
	// the initial capacity is 10, and the heap will grow automatically if needed
	public PriorityQueue<waitUntilThread> minHeap = new PriorityQueue<waitUntilThread>(10, heapComparator);
	public HashMap<KThread, waitUntilThread> threadMap = new HashMap<>();
	
	
	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		while (!minHeap.isEmpty() && minHeap.peek().wakeTime <= Machine.timer().getTime()) {  // the top thread can be pushed to the ready queue
			minHeap.poll().thread.ready();
		}
		KThread.yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		if (x <= 0) {
			return;  // return without waiting
		}
		
		long wakeTime = Machine.timer().getTime() + x;
		// We should use "if" instead of "while" because the thread may be awaken by the CV wake function. 
		// Otherwise, the wake time would still be bigger and the thread would be stuck in the while loop.
		if (wakeTime > Machine.timer().getTime()) {  
			waitUntilThread currentWaitUntilThread = new waitUntilThread(wakeTime, KThread.currentThread());
			threadMap.put(KThread.currentThread(), currentWaitUntilThread);
			minHeap.add(currentWaitUntilThread);
			
			// disable interrupt before making the thread sleep to pass the assertion in sleep()
			boolean intStatus = Machine.interrupt().disable();  // curStatus becomes false
			KThread.sleep();  // blocking state
			Machine.interrupt().restore(intStatus);
		}
	}

     /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
		boolean intStatus = Machine.interrupt().disable();
		waitUntilThread currentWaitUntilThread = threadMap.get(thread);		
		
		if (minHeap.remove(currentWaitUntilThread)) {
			Machine.interrupt().restore(intStatus);
			return true;
		}
		return false;  // thread not found (had no timer)
	}
	
	// when a class is static, we can access it without creating an alarm object
	private static class durationTest implements Runnable {
    	durationTest(int[] durations, int testId, int threadId) {
    		this.durations = durations;
    		this.testId = testId;
    		this.threadId = threadId;
    	}

    	public void run() {
    		long t0;  // arrive time
    		long t1;  // finish time
    		for (int d : durations) {
    	    	t0 = Machine.timer().getTime();
    	        ThreadedKernel.alarm.waitUntil(d);
    	   	    t1 = Machine.timer().getTime();
    	   	    System.out.printf("alarmTest%d thread%d waited for %d ticks\n", testId, threadId, t1 - t0);  // print formatted output
    	    }
    	}
    	private int[] durations;
    	private int testId;
    	private int threadId;
    }
	
	// only the main thread
	public static void alarmTest1() {
		int durations[] = {1000, 10 * 1000, 100 * 1000};
		new durationTest(durations, 1, 0).run();
	}	
	
	// two children
	public static void alarmTest2() {
	    int durations[] =  {1000, 1000, 10 * 1000};
	    int durations2[] = {1000, 10 * 1000, 100 * 1000};
	    int durations3[] = {10, 10, 10};

	    KThread child1 = new KThread(new durationTest(durations, 2, 1));
	    child1.setName("forked thread1").fork();
	    KThread child2 = new KThread(new durationTest(durations2, 2, 2));
	    child2.setName("forked thread2").fork();

		new durationTest(durations3, 2, 0).run();
		child1.join();
		child2.join();
    }
	
	// the main thread doesn't need to wait at all
	public static void alarmTest3() {
	    int durations[] =  {0, 100, 200};
	    int durations2[] = {10, 1000, 100 * 1000};
	    int durations3[] = {0, 0, 0};

	    KThread child1 = new KThread(new durationTest(durations, 3, 1));
	    child1.setName("forked thread1").fork();
	    KThread child2 = new KThread(new durationTest(durations2, 3, 2));
	    child2.setName("forked thread2").fork();

		new durationTest(durations3, 3, 0).run();
		child1.join();
		child2.join();
    }
	
	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
    	alarmTest1();
    	System.out.println("");
    	alarmTest2();
    	System.out.println("");
    	alarmTest3();
    }
}
