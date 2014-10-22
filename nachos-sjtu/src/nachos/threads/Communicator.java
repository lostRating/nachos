package nachos.threads;

import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		lock = new Lock();
		speaker = new LinkedList<event>();
		listener = new LinkedList<event>();
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) {
		lock.acquire();
		if (listener.isEmpty())
		{
			Condition c = new Condition(lock);
			speaker.add(new event(word, c));
			c.sleep();
		}
		else
		{
			event t = listener.getFirst();
			t.word = word;
			listener.removeFirst();
			t.c.wake();
		}
		lock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		event active;
		int ret = -1;
		
		lock.acquire();
		if (speaker.isEmpty()) {
			Condition c = new Condition(lock);
			listener.add(active = new event(-1, c));
			c.sleep();
			ret = active.word;
		}
		else
		{
			event t = speaker.getFirst();
			ret = t.word;
			speaker.removeFirst();
			t.c.wake();
		}
		lock.release();
		return ret;
	}
	
	private class event {
		int word;
		Condition c;
		event(int word, Condition c) {
			this.word = word;
			this.c = c;
		}
	}
	
	private Lock lock;
	private LinkedList<event> listener;
	private LinkedList<event> speaker;
}
