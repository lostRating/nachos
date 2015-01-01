package nachos.userprog;

import java.io.EOFException;
import java.util.TreeMap;
import java.util.TreeSet;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Config;
import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.ThreadedKernel;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {

		/*
		 * int numPhysPages = Machine.processor().getNumPhysPages(); pageTable =
		 * new TranslationEntry[numPhysPages]; for (int i = 0; i < numPhysPages;
		 * i++) pageTable[i] = new TranslationEntry(i, i, true, false, false,
		 * false);
		 */

		processID = ++processCnt;

		idToFile = new TreeMap<Integer, OpenFile>();
		idToFile.put(0, UserKernel.console.openForReading());
		idToFile.put(1, UserKernel.console.openForWriting());

		idToName = new TreeMap<Integer, String>();

		children = new TreeSet<Integer>();

		idToProcess.put(processID, this);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args)) return false;

		++activeProcess;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int amount = 0;
		for (int i = 0; i < length; i++) {
			int page = (vaddr + i) / pageSize;
			if (page >= numPages || !pageTable[page].valid) break;
			pageTable[page].used = true;
			int addr = pageTable[page].ppn * pageSize + (vaddr + i) % pageSize;
			data[offset + i] = memory[addr];
			amount++;
		}
		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int amount = 0;
		for (int i = 0; i < length; i++) {
			int page = (vaddr + i) / pageSize;
			if (page >= numPages || !pageTable[page].valid || pageTable[page].readOnly) break;
			pageTable[page].used = pageTable[page].dirty = true;
			int addr = pageTable[page].ppn * pageSize + (vaddr + i) % pageSize;
			memory[addr] = data[offset + i];
			amount++;
		}
		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
		
		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}
		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > UserKernel.freePage.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		pageNumber = new int[numPages];

		for (int i = 0; i < numPages; i++) {
			int now = UserKernel.freePage.pollFirst();
			pageNumber[i] = now;
			pageTable[i] = new TranslationEntry(i, now, true, false, false, false);
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
				pageTable[vpn].readOnly = section.isReadOnly();
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; i++)
			UserKernel.freePage.add(pageNumber[i]);
		for (int id : idToFile.keySet())
			handleClose(id, false);
		idToFile.clear();
		coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}
	
	private int handleHalt() {
		if (processID != 0) return (0);

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleExit(int status) {
		unloadSections();
		exitStatus = status;
		if (--activeProcess == 0)
			Kernel.kernel.terminate();
		UThread.finish();
		return 0;
	}

	private int handleExec(int name, int argc, int argv) {
		String fileName = readVirtualMemoryString(name, 256);
		if (fileName == null)
			return (-1);
		String arg[] = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] buf = new byte[4];
			readVirtualMemory(argv + i * 4, buf);
			int addr = Lib.bytesToInt(buf, 0);
			arg[i] = readVirtualMemoryString(addr, 256);
		}
		UserProcess child = UserProcess.newUserProcess();
		if (!child.execute(fileName, arg))
			return (-1);
		children.add(child.processID);
		return (child.processID);
	}

	private int handleJoin(int pid, int status) {
		if (!children.contains(pid))
			return (-1);
		UserProcess child = idToProcess.get(pid);
		child.thread.join();
		byte[] buf = Lib.bytesFromInt(child.exitStatus);
		writeVirtualMemory(status, buf);
		return (child.exitSuccess ? 1 : 0);
	}

	private int handleCreate(int name) {
		String fileName = readVirtualMemoryString(name, 256);
		if (removeList.contains(fileName))
			return (-1);
		OpenFile file = UserKernel.fileSystem.open(fileName, true);
		if (file == null)
			return (-1);
		int now = idToFile.lastKey() + 1;
		idToFile.put(now, file);
		idToName.put(now, fileName);
		if (fileCount.containsKey(fileName)) {
			int cnt = fileCount.get(fileName);
			fileCount.put(fileName, cnt + 1);
		} else
			fileCount.put(fileName, 1);
		return (now);
	}

	private int handleOpen(int name) { //ok
		String fileName = readVirtualMemoryString(name, 256);
		if (removeList.contains(fileName)) return -1;
		OpenFile file = UserKernel.fileSystem.open(fileName, false);
		if (file == null) return -1;
		int id = idToFile.lastKey() + 1;
		idToFile.put(id, file);
		idToName.put(id, fileName);
		if (fileCount.containsKey(fileName)) {
			int cnt = fileCount.get(fileName) + 1;
			fileCount.put(fileName, cnt);
		}
		else
			fileCount.put(fileName, 1);
		return id;
	}

	private int handleRead(int fileId, int buffer, int length) { //ok
		OpenFile file = idToFile.get(fileId);
		if (file == null) return -1;
		byte[] buf = new byte[length];
		int amount = file.read(buf, 0, length);
		if (amount == -1) return -1;
		return writeVirtualMemory(buffer, buf, 0, amount);
	}

	private int handleWrite(int fileId, int buffer, int length) { //ok
		OpenFile file = idToFile.get(fileId);
		if (file == null) return -1;
		byte[] buf = new byte[length];
		int amount = readVirtualMemory(buffer, buf, 0, length);
		if (amount == -1) return -1;
		return file.write(buf, 0, amount);
	}

	private int handleClose(int fileId, boolean need) { //ok
		OpenFile file = idToFile.get(fileId);
		if (file == null) return -1;
		file.close();
		if (need) idToFile.remove(fileId);
		String fileName = idToName.get(fileId);
		if (fileName != null) {
			idToName.remove(fileId);
			int cnt = fileCount.get(fileName) - 1;
			fileCount.put(fileName, cnt);
			if (removeList.contains(fileName) && cnt == 0) {
				removeList.remove(fileName);
				UserKernel.fileSystem.remove(fileName);
				fileCount.remove(fileName);
			}
		}
		return 0;
	}

	private int handleUnlink(int name) { //ok
		int ret = 0;
		String fileName = readVirtualMemoryString(name, 256);
		if (!fileCount.containsKey(fileName) || fileCount.get(fileName) == 0) {
			if (!UserKernel.fileSystem.remove(fileName)) ret = -1;
			fileCount.remove(fileName);
		}
		else
			removeList.add(fileName);
		return ret;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt: return handleHalt();
		case syscallExit: return handleExit(a0);
		case syscallExec: return handleExec(a0, a1, a2);
		case syscallJoin: return handleJoin(a0, a1);
		case syscallCreate: return handleCreate(a0);
		case syscallOpen: return handleOpen(a0);
		case syscallRead: return handleRead(a0, a1, a2);
		case syscallWrite: return handleWrite(a0, a1, a2);
		case syscallClose: return handleClose(a0, true);
		case syscallUnlink:	return handleUnlink(a0);

		default:
			exitSuccess = false;
			handleExit(-1);
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			exitSuccess = false;
			handleExit(-1);
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = Config.getInteger("Processor.numStackPages", 8);

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	
	//addition
	private int[] pageNumber;
	protected int processID, exitStatus = -1;
	protected boolean exitSuccess = true;
	protected TreeMap<Integer, OpenFile> idToFile; //file
	protected TreeMap<Integer, String> idToName; //file
	private static TreeMap<String, Integer> fileCount = new TreeMap<String, Integer>(); //file
	protected TreeSet<Integer> children;
	private UThread thread;private static int processCnt = 0, activeProcess = 0;
	private static TreeSet<String> removeList = new TreeSet<String>();
	protected static TreeMap<Integer, UserProcess> idToProcess = new TreeMap<Integer, UserProcess>();
}
