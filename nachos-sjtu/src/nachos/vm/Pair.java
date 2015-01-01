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

class Pair implements Comparable<Pair> {
	Pair(int _processID, int _page) {
		processID = _processID;
		page = _page;
	}
	public int compareTo(Pair a) {
		if (processID != a.processID) return processID - a.processID;
		return page - a.page;
	}
	int processID, page;
}