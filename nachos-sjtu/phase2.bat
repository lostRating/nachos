java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.AutoGrader -x shCXR.coff -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_files.coff -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_files2.coff -# output=test_files2.out -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_matmult.coff -# output=test_matmult.out -s 23049701 
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_proc.coff -# output=test_proc.out -s 23049701 
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.UserGrader1 -x grader_user1.coff  -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_files3.coff -# output=test_files3.out -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_illegal.coff -# output=test_illegal.out -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc.coff -# output=test_memalloc.out  -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc2.coff  -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc3.coff -# output=test_memalloc3.out  -s 23049701
pause
