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

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		for (int i = 0; i < TLBSize; i++)
			bakcup[i] = new TranslationEntry(-1, -1, false, false, false, false);
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int amount = 0;

		for (int i = 0; i < length; i++) {
			int page = (vaddr + i) / pageSize;
			if (page < 0 || page >= numPages)
				break;
			if (!pageTable[page].valid)
				loadPage(page);
			pageTable[page].used = true;
			int addr = pageTable[page].ppn * pageSize + (vaddr + i) % pageSize;
			data[offset + i] = memory[addr];
			amount++;
		}

		return amount;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int amount = 0;

		for (int i = 0; i < length; i++) {
			int page = (vaddr + i) / pageSize;
			if (page < 0 || page >= numPages || pageTable[page].readOnly)
				break;
			if (!pageTable[page].valid)
				loadPage(page);
			pageTable[page].used = true;
			pageTable[page].dirty = true;
			int addr = pageTable[page].ppn * pageSize + (vaddr + i) % pageSize;
			memory[addr] = data[offset + i];
			amount++;
		}

		return amount;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		for (int i = 0; i < TLBSize; i++) {
			TranslationEntry now = Machine.processor().readTLBEntry(i);
			if (now.valid && pageTable[now.vpn].valid) {
				pageTable[now.vpn].used |= now.used;
				pageTable[now.vpn].dirty |= now.dirty;
				position[now.vpn] = -1;
			}
			bakcup[i] = now;
		}
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		for (int i = 0; i < TLBSize; i++) {
			if (bakcup[i].valid && !pageTable[bakcup[i].vpn].valid)
				bakcup[i].valid = false;
			if (bakcup[i].valid && pageTable[bakcup[i].vpn].valid)
				position[bakcup[i].vpn] = i;
			Machine.processor().writeTLBEntry(i, bakcup[i]);
		}
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {

		position = new int[numPages];
		pageTable = new TranslationEntry[numPages];

		for (int i = 0; i < numPages; i++) {
			position[i] = -1;
			pageTable[i] = new TranslationEntry(i, i, false, false, false,
					false);
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				// for now, just assume virtual addresses=physical addresses
				sections.put(vpn, section);
				if (section.isReadOnly())
					pageTable[vpn].readOnly = true;
			}
		}

		return true;

	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int page : owned) {
			physic.remove(page);
			VMKernel.freePage.add(page);
		}
		for (int page : swapped)
			Swap.remove(new Pair(processID, page));
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {

		switch (cause) {
		case Processor.exceptionTLBMiss:
			TLBMiss();
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	void loadPage(int page) {
		lock.acquire();
		Processor processor = Machine.processor();
		byte[] memory = processor.getMemory();
		int ppn = -1;
		if (!VMKernel.freePage.isEmpty())
			ppn = VMKernel.freePage.pollFirst();
		else {
			VMProcess now = null;
			Pair ID = null;
			for (int i = 0; i < 26; i++) {
				ppn = Lib.random(processor.getNumPhysPages());
				ID = physic.get(ppn);
				now = (VMProcess) idToProcess.get(ID.pid);
				if (now.position[ID.page] == -1)
					break;
			}
			now.pageTable[ID.page].valid = false;
			if (now.position[ID.page] != -1) {
				TranslationEntry tmp = processor
						.readTLBEntry(now.position[ID.page]);
				now.position[ID.page] = -1;
				if (tmp.valid && now.pageTable[ID.page].valid) {
					now.pageTable[ID.page].used |= tmp.used;
					now.pageTable[ID.page].dirty |= tmp.dirty;
				}
			}
			if (now.pageTable[ID.page].dirty) {
				Swap.write(new Pair(now.processID, ID.page), memory, ppn * pageSize);
				now.swapped.add(ID.page);
			}
			now.owned.remove(ppn);
		}
		owned.add(ppn);
		physic.put(ppn, new Pair(processID, page));
		if (swapped.contains(page))
			Swap.read(new Pair(processID, page), memory, ppn * pageSize);
		else if (sections.containsKey(page)) {
			CoffSection section = sections.get(page);
			section.loadPage(page - section.getFirstVPN(), ppn);
		} else
			for (int i = ppn * pageSize; i < (ppn + 1) * pageSize; i++)
				memory[i] = 0;
		pageTable[page].valid = true;
		pageTable[page].ppn = ppn;
		pageTable[page].used = false;
		pageTable[page].dirty = false;
		lock.release();
	}

	void TLBMiss() {
		Processor processor = Machine.processor();
		int addr = processor.readRegister(Processor.regBadVAddr), pos = -1;
		for (int i = 0; i < TLBSize; i++)
			if (!processor.readTLBEntry(i).valid) {
				pos = i;
				break;
			}
		if (pos == -1) {
			pos = Lib.random(TLBSize);
			TranslationEntry now = processor.readTLBEntry(pos);
			if (now.valid && pageTable[now.vpn].valid) {
				pageTable[now.vpn].used |= now.used;
				pageTable[now.vpn].dirty |= now.dirty;
				position[now.vpn] = -1;
			}
		}
		int page = addr / pageSize;
		if (!pageTable[page].valid)
			loadPage(page);
		processor.writeTLBEntry(pos, pageTable[page]);
		position[page] = pos;
	}

	private HashSet<Integer> swapped = new HashSet<Integer>();
	private HashSet<Integer> owned = new HashSet<Integer>();
	private HashMap<Integer, CoffSection> sections = new HashMap<Integer, CoffSection>();
	private int position[];
	private TranslationEntry[] bakcup = new TranslationEntry[TLBSize];

	private static final int TLBSize = Machine.processor().getTLBSize();
	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	private static HashMap<Integer, Pair> physic = new HashMap<Integer, Pair>();
	private static Lock lock = new Lock();

	class Pair implements Comparable<Pair> {
		int pid, page;

		Pair(int _pid, int _page) {
			pid = _pid;
			page = _page;
		}

		public int compareTo(Pair a) {
			if (pid != a.pid)
				return (pid - a.pid);
			return (page - a.page);
		}
	}

	static class Swap {

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

		static void close() {
			file.close();
		}

		private static OpenFile file = VMKernel.fileSystem.open("SWAP", true);
		private static TreeMap<Pair, Integer> position = new TreeMap<Pair, Integer>();
		private static TreeSet<Integer> freeSpace = new TreeSet<Integer>();
		private static int cnt = 0;
	}
}
