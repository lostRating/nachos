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
			store[i] = new TranslationEntry(-1, -1, false, false, false, false);
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		int amount = 0;
		for (int i = 0; i < length; i++) {
			int page = (vaddr + i) / pageSize;
			if (page < 0 || page >= numPages) break;
			if (!pageTable[page].valid) loadPage(page);
			pageTable[page].used = true;
			int addr = pageTable[page].ppn * pageSize + (vaddr + i) % pageSize;
			data[offset + i] = memory[addr];
			++amount;
		}
		return amount;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		int amount = 0;
		for (int i = 0; i < length; i++) {
			int page = (vaddr + i) / pageSize;
			if (page < 0 || page >= numPages || pageTable[page].readOnly) break;
			if (!pageTable[page].valid)	loadPage(page);
			pageTable[page].used = pageTable[page].dirty = true;
			int addr = pageTable[page].ppn * pageSize + (vaddr + i) % pageSize;
			memory[addr] = data[offset + i];
			++amount;
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
				TLBPos[now.vpn] = -1;
			}
			store[i] = now;
		}
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		for (int i = 0; i < TLBSize; i++) {
			if (store[i].valid && !pageTable[store[i].vpn].valid) store[i].valid = false;
			if (store[i].valid && pageTable[store[i].vpn].valid) TLBPos[store[i].vpn] = i;
			Machine.processor().writeTLBEntry(i, store[i]);
		}
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		TLBPos = new int[numPages];
		pageTable = new TranslationEntry[numPages];

		for (int i = 0; i < numPages; i++) {
			TLBPos[i] = -1;
			pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		}

		// load Sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				// for now, just assume virtual addresses=physical addresses
				idToCoff.put(vpn, section);
				pageTable[vpn].readOnly = section.isReadOnly();
			}
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadidToCoff()</tt>.
	 */
	protected void unloadSections() {
		for (int page:in) {
			mem.remove(page);
			VMKernel.freePage.add(page);
		}
		for (int page:out)
			Swap.remove(new Pair(processID, page));
	}

	void loadPage(int page) {
		lock.acquire();
		Processor processor = Machine.processor();
		byte[] memory = processor.getMemory();
		int ppn = -1;
		if (!VMKernel.freePage.isEmpty()) ppn = VMKernel.freePage.pollFirst();
		else {
			Pair ID = null;
			VMProcess now = null;
			for (int i = 0; i < 26; i++) {
				ppn = Lib.random(processor.getNumPhysPages());
				ID = mem.get(ppn);
				now = (VMProcess) idToProcess.get(ID.processID);
				if (now.TLBPos[ID.page] == -1) break;
			}
			now.pageTable[ID.page].valid = false;
			if (now.TLBPos[ID.page] != -1) {
				TranslationEntry tmp = processor.readTLBEntry(now.TLBPos[ID.page]);
				now.TLBPos[ID.page] = -1;
				if (tmp.valid && now.pageTable[ID.page].valid) {
					now.pageTable[ID.page].used |= tmp.used;
					now.pageTable[ID.page].dirty |= tmp.dirty;
				}
			}
			if (now.pageTable[ID.page].dirty) {
				Swap.write(new Pair(now.processID, ID.page), memory, ppn * pageSize);
				now.out.add(ID.page);
			}
			now.in.remove(ppn);
		}
		in.add(ppn);
		mem.put(ppn, new Pair(processID, page));
		if (out.contains(page))
			Swap.read(new Pair(processID, page), memory, ppn * pageSize);
		else if (idToCoff.containsKey(page)) {
			CoffSection section = idToCoff.get(page);
			section.loadPage(page - section.getFirstVPN(), ppn);
		}
		else {
			for (int i = ppn * pageSize; i < (ppn + 1) * pageSize; i++)
				memory[i] = 0;
		}
		pageTable[page].valid = true;
		pageTable[page].ppn = ppn;
		pageTable[page].used = pageTable[page].dirty = false;
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
				TLBPos[now.vpn] = -1;
			}
		}
		int page = addr / pageSize;
		if (!pageTable[page].valid) loadPage(page);
		processor.writeTLBEntry(pos, pageTable[page]);
		TLBPos[page] = pos;
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
	
	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	
	//addition
	private static Lock lock = new Lock();
	private int TLBPos[];
	private HashSet<Integer> out = new HashSet<Integer>();
	private HashSet<Integer> in = new HashSet<Integer>();
	private HashMap<Integer, CoffSection> idToCoff = new HashMap<Integer, CoffSection>();
	private TranslationEntry[] store = new TranslationEntry[TLBSize];
	private static final int TLBSize = Machine.processor().getTLBSize();
	private static HashMap<Integer, Pair> mem = new HashMap<Integer, Pair>();
}
