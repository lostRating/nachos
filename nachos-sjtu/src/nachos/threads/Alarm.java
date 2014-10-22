package nachos.threads;

import java.util.ArrayList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
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

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		//KThread.yield();
		
		boolean intStatus = Machine.interrupt().disable();
		
		long currentTime = Machine.timer().getTime();
		
		for (int i = 0; i < time.size(); ++i)
		{
			if (time.get(i) <= currentTime)
			{
				thread.get(i).ready();
				time.remove(i);
				thread.remove(i);
				--i;
			}
		}
		
		Machine.interrupt().setStatus(intStatus);
		
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
	 * @param x
	 *            the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		/*long wakeTime = Machine.timer().getTime() + x;
		while (wakeTime > Machine.timer().getTime())
			KThread.yield();*/
		
		boolean intStatus = Machine.interrupt().disable();
		
		long wakeTime = Machine.timer().getTime() + x;
		time.add(wakeTime);
		thread.add(KThread.currentThread());
		KThread.sleep();
		
		int i = time.size() - 1;
		while (i > 0 && time.get(i) < time.get(i - 1))
		{
			long t = time.get(i - 1);
			time.set(i - 1, time.get(i));
			time.set(i, t);
			
			KThread tt = thread.get(i - 1);
			thread.set(i - 1, thread.get(i));
			thread.set(i, tt);
			
			--i;
		}
		
		Machine.interrupt().restore(intStatus);
	}
	
	private ArrayList<Long> time = new ArrayList<Long>();
	private ArrayList<KThread> thread = new ArrayList<KThread>();
}
