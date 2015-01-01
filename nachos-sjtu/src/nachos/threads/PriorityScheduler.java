package nachos.threads;

import java.util.LinkedList;
import java.util.TreeMap;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer priority from
	 *            waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum	&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum) {
			Machine.interrupt().restore(intStatus); // bug identified by Xiao
													// Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum) {
			Machine.interrupt().restore(intStatus); // bug identified by Xiao
													// Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
			// print();
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			// print();
			Lib.assertTrue(Machine.interrupt().disabled());
			KThread ret = null;
			if (owner != null) {
				owner.own.remove(this);
				owner.modify();
			}
			if (!waitQueue.isEmpty()) {
				ThreadState now = waitQueue.firstEntry().getValue();
				ret = now.thread;
				remove(now);
				allState.remove(now);
				now.belong = null;
				maximum = waitQueue.isEmpty() ? priorityMinimum : waitQueue.firstKey().priority;
				if (transferPriority && owner != null) {
					now.own.add(this);
					now.modify();
					owner = now;
				}
			}
			return (ret);
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			ThreadState ret = null;
			if (!waitQueue.isEmpty())
				ret = waitQueue.firstEntry().getValue();
			return (ret);
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// for (Pair x : waitQueue.keySet())
			// System.out.println(x.priority + " " + x.time + " ");
			System.out.println(waitQueue.size() + "=====================");
		}

		void insert(ThreadState state) {
			waitQueue.put(state.hash(), state);
		}

		void remove(ThreadState state) {
			waitQueue.remove(allState.get(state));
		}

		void modify(ThreadState state) {
			remove(state);
			allState.put(state, state.hash());
			insert(state);
			if (maximum != waitQueue.firstKey().priority) {
				maximum = waitQueue.firstKey().priority;
				if (owner != null)
					owner.modify();
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		TreeMap<ThreadState, Pair> allState = new TreeMap<ThreadState, Pair>();
		TreeMap<Pair, ThreadState> waitQueue = new TreeMap<Pair, ThreadState>();
		ThreadState owner;
		int maximum = priorityMinimum;
	}

	class Pair implements Comparable<Pair> {
		int priority, id;
		long time;

		Pair(int _priority, long _time, int _id) {
			priority = _priority;
			time = _time;
			id = _id;
		}

		public int compareTo(Pair a) {
			if (priority != a.priority)	return a.priority - priority;
			if (time != a.time)	return time < a.time ? -1 : 1;
			return id - a.id;
		}
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState implements Comparable<ThreadState> {
		Pair hash() {return new Pair(getEffectivePriority(), time, thread.PID());}
		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		LinkedList<PriorityQueue> own = new LinkedList<PriorityQueue>();
		protected int priority, donatePriority;
		PriorityQueue belong;
		long time;
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread
		 *            the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			donatePriority = priorityMinimum;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			return Math.max(priority, donatePriority);
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority
		 *            the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority) return;
			this.priority = priority;
			if (belong != null)	belong.modify(this);
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue
		 *            the queue that the associated thread is now waiting on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			belong = waitQueue;
			time = Machine.timer().getTime();
			belong.allState.put(this, hash());
			belong.insert(this);
			if (belong.transferPriority	&& getEffectivePriority() > belong.maximum)
				belong.modify(this);
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			if (waitQueue.transferPriority) {
				waitQueue.owner = this;
				own.add(waitQueue);
				modify();
			}
		}

		public int compareTo(ThreadState x) {
			return thread.PID() - x.thread.PID();
		}

		void modify() {
			int now = priorityMinimum;
			for (PriorityQueue queue : own)
				now = Math.max(now, queue.maximum);
			if (now != donatePriority) {
				donatePriority = now;
				if (belong != null) belong.modify(this);
			}
		}
	}
}
