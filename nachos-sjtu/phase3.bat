java -classpath bin nachos.machine.Machine -[] conf/proj3_4p.conf -- nachos.ag.VMGrader -x vm_echo.coff -# output=vm_echo.out -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_4p.conf -- nachos.ag.VMGrader -x vm_lazyload.coff -# swapFile=SWAP -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_16p.conf -- nachos.ag.VMGrader -x vm_matrix.coff -# output=vm_matrix.out -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_16p.conf -- nachos.ag.VMGrader -x vm_matrix2.coff -# output=vm_matrix.out,swapFile=SWAP -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_8p.conf -- nachos.ag.VMGrader -x vm_matrix.coff -# output=vm_matrix.out -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_8p.conf -- nachos.ag.VMGrader -x vm_matrix2.coff -# output=vm_matrix.out,swapFile=SWAP -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_4p.conf -- nachos.ag.VMGrader -x vm_reuse.coff -# coffPar0=10,swapFile=SWAP  -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_16p.conf -- nachos.ag.VMGrader -x vm_skipfill.coff -# coffPar0=5,coffPar1=32,coffPar2=6 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj3_recursion.conf -- nachos.ag.VMGrader -x vm_recursion.coff -# coffPar0=20,coffPar1=6,swapFile=SWAP -s 23049701
pause
