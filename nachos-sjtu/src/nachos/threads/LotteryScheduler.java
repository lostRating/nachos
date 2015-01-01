package nachos.threads;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.TreeMap;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public LotteryQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
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

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

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
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected State getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new State(thread);

		return (State) thread.schedulingState;
	}

	protected class LotteryQueue extends PriorityQueue {
		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
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
				State now = pickNextThread();
				ret = now.thread;
				remove(now);
				now.belong = null;
				if (transferPriority && owner != null) {
					now.own.add(this);
					now.modify();
					owner = now;
				}
			}
			return ret;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected State pickNextThread() {
			State ret = null;
			if (!waitQueue.isEmpty()) {
				int lottery = Lib.random(sum);
				ret = waitQueue.higherEntry(lottery).getValue();
			}
			return (ret);
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// for (Pair x : waitQueue.keySet())
			// System.out.println(x.priority + " " + x.time + " ");
			System.out.println(waitQueue.size() + "=====================");
		}

		void remove(State state) {
			rebuild(state);
		}

		void insert(State state) {
			waitQueue.put(-1, state);
			rebuild(null);
		}

		void modify(State state) {
			rebuild(null);
			if (owner != null)
				owner.modify();
		}

		void rebuild(State del) {
			ArrayList<Integer> lottery = new ArrayList<Integer>();
			ArrayList<State> state = new ArrayList<State>();
			for (Entry<Integer, State> tmp : waitQueue.entrySet()) {
				if (del != null && del.thread.PID() == tmp.getValue().thread.PID())	continue;
				lottery.add(tmp.getValue().getEffectivePriority());
				state.add(tmp.getValue());
			}
			sum = 0;
			waitQueue.clear();
			for (int i = 0; i < lottery.size(); i++) {
				sum += lottery.get(i);				
				waitQueue.put(sum, state.get(i));
			}
		}

		State owner;
		int sum;
		TreeMap<Integer, State> waitQueue = new TreeMap<Integer, State>();
	}

	protected class State extends ThreadState {
		LotteryQueue belong;
		LinkedList<LotteryQueue> own = new LinkedList<LotteryQueue>();
		
		public State(KThread thread) {
			super(thread);
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
			return priority + donatePriority;
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
		public void waitForAccess(LotteryQueue waitQueue) {
			belong = waitQueue;
			belong.insert(this);
			if (belong.transferPriority) belong.modify(this);
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
		public void acquire(LotteryQueue waitQueue) {
			if (waitQueue.transferPriority) {
				waitQueue.owner = this;
				own.add(waitQueue);
				modify();
			}
		}

		void modify() {
			int now = 0;
			for (LotteryQueue queue : own) now += queue.sum;
			if (now != donatePriority) {
				donatePriority = now;
				if (belong != null) belong.modify(this);
			}
		}

	}
}
