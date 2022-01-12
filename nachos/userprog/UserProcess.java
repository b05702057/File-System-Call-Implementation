package nachos.userprog;

import nachos.machine.*;

import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional system calls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		vaUpperBound = numPhysPages * pageSize;
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		for (int i = 2; i <= 15; i++)  // initialize the heap with fileDescriptors of 2 ~ 15 (0 and 1 are already used)
			pQueue.add(i);

		OpenFile stdin = UserKernel.console.openForReading();
		OpenFile stdout = UserKernel.console.openForWriting();
		map.put(0, stdin);
		map.put(1, stdout);
		map2.put(stdin.getName(), 1);
		map2.put(stdout.getName(), 1);
	}
	
	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

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
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
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
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
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
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
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
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
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
		}
		catch (EOFException e) {
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
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, vpn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
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
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	    // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		Kernel.kernel.terminate();

		return 0;
	}

	private int handleCreate(int vaName) {
		if (pQueue.isEmpty()) {  // All fileDescriptors are used.
			return -1;
		}	
		String vaNameString = readVirtualMemoryString(vaName, 256); // the maximum size is 256 bytes
		if (map2.containsKey(vaNameString)) { // already exists
			return handleOpen(vaName);
		}
		else {  // new file
			OpenFile newFile = ThreadedKernel.fileSystem.open(vaNameString, true); // use open(_, true) to create a file
			return modifyQueueMap(vaNameString, newFile);
		}	
	}

	// opening the same file multiple times returns different file descriptors for each open
	private int handleOpen(int vaName) {
		if (pQueue.isEmpty()) { // all fileDescriptors are used
			return -1;
		}
		String vaNameString = readVirtualMemoryString(vaName, 256); // the maximum size is 256 bytes
		// If the file doesn't exist in the file system, the return OpenFile will be null.
		OpenFile newFile = ThreadedKernel.fileSystem.open(vaNameString, false); // use open(_, false) to open a file
		return modifyQueueMap(vaNameString, newFile) ;
	}
	
	// modify the queue and maps when we open a file
	private int modifyQueueMap(String vaNameString, OpenFile newFile) {
		if (newFile == null) { // fail to create/open the file
			return -1;
		}
		int fileDescriptor = pQueue.remove(); // get the smallest available fileDescriptor 
		map.put(fileDescriptor, newFile);
		if (!map2.containsKey(vaNameString)) {  // The file doesn't exist before.
			map2.put(vaNameString, 0);
		}
		map2.put(vaNameString, map2.get(vaNameString) + 1);
		return fileDescriptor;
	}
	
	private int handleClose(int fileDescriptor) {
		if (map.containsKey(fileDescriptor)) {  // the file exists
			OpenFile curFile = map.get(fileDescriptor);  // get the file with the file descriptor
			curFile.close();  // release the resource held by the file

			map.remove(fileDescriptor);
			
			String vaNameString = curFile.getName();
			map2.put(vaNameString, map2.get(vaNameString) - 1);
			// If we unlink a file and close all corresponding file descriptors, the file needs to be created again next time.
			if (map2.get(vaNameString) == 0) {  // The file is not associated with any file descriptors now, which means it is not open.
				map2.remove(vaNameString);
			}
			if (fileDescriptor > 1) {  // the fileDesctiptor can be used by other files now
				pQueue.add(fileDescriptor); 
			}
			return 0;
		}
		return -1; // the file does not exist (we don't know which file the fileDescriptor indicates)
	}
	
	// The existing OpenFiles can still be accessed even though the file is unlinked (disappear from the file system).
	private int handleUnlink(int vaName) {
		String vaNameString = readVirtualMemoryString(vaName, 256);  // the maximum size is 256 bytes	
		boolean removed = ThreadedKernel.fileSystem.remove(vaNameString);
		if (!removed) { // didn't remove successfully
			return -1;
		}
		return 0;
	}
	
	// Normally, user programs read from STDIN to know the input and write to STDOUT to show the output.
	// There will not be test cases of writing to STDIN and reading from STDOUT, so we don't have to deal with them.
	private int handleRead(int fileDescriptor, int buffer, int count) {
		if (count < 0 || !map.containsKey(fileDescriptor)) {  // count < 0 or invalid file descriptor
			return -1;
		}
		OpenFile curFile = map.get(fileDescriptor);
		
		// If the user program has no input, length() and tell() of STDIN will always return -1.
		int fileLength = curFile.length();
		int filePosition = curFile.tell();
		if (count == 0 || fileLength == 0) {  // return 0 with empty file even with a bad buffer
			return 0;
		}

		int fileLeft = fileLength - filePosition;
		count = Math.min(count, fileLeft);

		if (buffer < 0 || buffer + count >= vaUpperBound) { // check if the buffer and byte count to read is valid
			return -1;
		}
		
		int totalByteRead = 0;  // the number of total bytes we have successfully read
		int byteRead = 0;  // the number of successfully read byte in the first step
		int finalByteRead = 0;  // the number of successfully read byte in the second step
		int curCount = 0;  // the number of bytes we want to read in this iteration
		
		while (count > 0) {  // still need to read
			curCount = Math.min(pageSize, count);  // curCount <= pageSize (the size of the buffer)
			byte[] byteArray = new byte[curCount];  // We use a page-sized buffer to pass data from the file to the user memory.
			byteRead = curFile.read(byteArray, 0, curCount);  // the number of bytes read
			if (byteRead == 0) {  // We have reached the end of the file.
				return totalByteRead;  // simply return the number of bytes we have read (prevent infinite loop)
			}
			if (byteRead == -1) {  // some errors happen
				return -1;
			}

			// copy the elements to a shorter array because the writeVirtualMemory function will try to use the full array
			byte[] byteArray2 = new byte[byteRead];  
			for (int i = 0; i < byteRead; i++) {
				byteArray2[i] = byteArray[i];
			}

			// try to write everything in the byteArray into the user memory
			finalByteRead = writeVirtualMemory(buffer + totalByteRead, byteArray2); 
			if (finalByteRead != byteRead){
				return -1;
			}
			totalByteRead += finalByteRead;
			count -= finalByteRead;
		}
		return totalByteRead;
	}
	
	private int handleWrite(int fileDescriptor, int buffer, int count) {
		if (count < 0 || !map.containsKey(fileDescriptor)) {  // check if the byte count to read and the file descriptor is valid
			return -1;
		}
		if (count == 0){  // no need to read anything (return 0 even though the buffer is invalid)
			return 0;
		}
		if (buffer < 0 || buffer + count >= vaUpperBound) {  // Some part of the buffer is invalid.
			return -1;
		}
		
		int totalByteWritten = 0;  // the number of total bytes we have successfully written
		int byteWritten = 0;  // the number of successfully written byte in the first step
		int finalByteWritten = 0;  // the number of successfully written byte in the second step
		int curCount = 0;  // the number of bytes we want to write this iteration
		OpenFile curFile = map.get(fileDescriptor);

		while (count > 0) {  // still need to write
			curCount = Math.min(pageSize, count);  // The size of the byteArray matters because readVirtualMemory() reads as much as the array can handle
			byte[] byteArray = new byte[curCount];  // use a page-sized buffer to pass data from the user memory to the file
			byteWritten = readVirtualMemory(buffer + totalByteWritten, byteArray);  // read data from the user buffer to the kernel buffer
			if (byteWritten == 0){  // fail to write anything
				return -1;
			}

			finalByteWritten = curFile.write(byteArray, 0, byteWritten);  // Since we always declare a new byteArray, the offset is 0.
			if (finalByteWritten == -1) {  // fail to write data to the file
				return -1;
			}
			totalByteWritten += finalByteWritten; // totalByteWritten helps us know where we should start reading the process buffer
			count -= finalByteWritten;
		}
		return totalByteWritten;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a system call exception. Called by <tt>handleException()</tt>. The
	 * <i>system call</i> argument identifies which system call the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>system call#</td>
	 * <td>system call prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the system call number.
	 * @param a0 the first system call argument.
	 * @param a1 the second system call argument.
	 * @param a2 the third system call argument.
	 * @param a3 the fourth system call argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return 0;
		case syscallJoin:
			return 0;
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);

		default:
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
	 * @param cause the user exception that occurred.
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
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	// The methods or data members declared as protected can be accessed in restricted situations.
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;
	
	protected int vaUpperBound;  // the value would be initialized for each process
	
	protected HashMap<Integer, OpenFile> map = new HashMap<>();  // {key: value} of {fileDescriptor: openFile}
	
	// We need this map to know if the file is already created.
	protected HashMap<String, Integer> map2 = new HashMap<>();  // {key: value} of {fileName: the number of associated fileDescriptors}
	
	protected PriorityQueue<Integer> pQueue = new PriorityQueue<Integer>();  // storing available fileDesctiptors

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
}
