package nachos.vm;

import java.util.*;

import nachos.machine.CoffSection;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;
import nachos.userprog.UserProcess;
import nachos.vm.*;

public class Swap{
	static void read(Pair addr, byte[] data, int offset) {
		int pos = position.get(addr);
		file.read(pos * pageSize, data, offset, pageSize);
	}

	static void write(Pair addr, byte[] data, int offset) {
		int pos = 0;
		if (position.containsKey(addr))
			pos = position.get(addr);
		else if (!freeSpace.isEmpty())
			pos = freeSpace.pollFirst();
		else
			pos = cnt++;
		file.write(pos * pageSize, data, offset, pageSize);
		position.put(addr, pos);
	}

	static void remove(Pair addr) {
		int pos = position.remove(addr);
		freeSpace.add(pos);
	}

	public static void close() {
		file.close();
	}

	public static OpenFile file = VMKernel.fileSystem.open("SWAP", true);
	private static TreeMap<Pair, Integer> position = new TreeMap<Pair, Integer>();
	private static TreeSet<Integer> freeSpace = new TreeSet<Integer>();
	private static int cnt = 0;

	private static final int pageSize = Processor.pageSize;
}