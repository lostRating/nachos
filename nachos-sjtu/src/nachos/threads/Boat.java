package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
	static BoatGrader bg;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		// begin(1, 2, b);

		// System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		// begin(3, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		done = pair = false;
		boat = Oahu;
		adult = adults;
		child = children;
		lock = new Lock();
		adultOahu = new Condition(lock);
		childOahu = new Condition(lock);
		childMolokai = new Condition(lock);

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		for (int i = 0; i < adults; i++) {
			Runnable r = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			new KThread(r).fork();
		}
		for (int i = 0; i < children; i++) {
			Runnable r = new Runnable() {
				public void run() {
					ChildItinerary();
				}
			};
			new KThread(r).fork();
		}

		while (!done)
			KThread.yield();

	}
	
	static void adultRowToMolokai() {
		adult--;
		boat = Molokai;
		bg.AdultRowToMolokai();
	}

	static void childRowToMolokai() {
		child--;
		bg.ChildRowToMolokai();
	}

	static void childRideToMolokai() {
		child--;
		boat = Molokai;
		bg.ChildRideToMolokai();
	}

	static void childRowToOahu() {
		child++;
		boat = Oahu;
		bg.ChildRowToOahu();
	}

	static void AdultItinerary() {
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */
		int position = Oahu;
		lock.acquire();
		while (true)
		{
			if (position == Oahu) {
				if (!pair && child <= 1 && boat == Oahu) {
					adultRowToMolokai();
					position = Molokai;
					childMolokai.wake();
				} else
					adultOahu.sleep();
			}
			else
				break;
		}
		lock.release();
	}

	static void ChildItinerary() {
		int position = Oahu;
		lock.acquire();
		while (true) {
			if (position == Oahu) {
				if (child >= 2 && boat == Oahu && !pair) {
					pair = true;
					position = Molokai;
					childRowToMolokai();
					childOahu.wake();
				} else if (pair) {
					boolean flag = false;
					if (child == 1 && adult == 0)
						flag = true;
					childRideToMolokai();
					pair = false;
					position = Molokai;
					if (flag) {
						done = true;
						break;
					}
				} else
					childOahu.sleep();
			} else {
				if (!done && boat == Molokai) {
					childRowToOahu();
					position = Oahu;
					childOahu.wake();
					adultOahu.wake();
				} else
					childMolokai.sleep();
			}
		}
		lock.release();
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out
				.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}
	
	static private Lock lock;
	static private int Oahu = 0, Molokai = 1;
	static private int boat = 0;
	static private boolean done, pair;
	static private int child, adult;
	static Condition adultOahu, childOahu, childMolokai;
}
