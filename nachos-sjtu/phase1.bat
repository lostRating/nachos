java -classpath bin nachos.machine.Machine -[] conf/proj1rr.conf -- nachos.ag.BoatGrader -# adults=3,children=3 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1rr.conf -- nachos.ag.JoinGrader -# waitTicks=1000,times=3 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1rr.conf -- nachos.ag.LockGrader11 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1pq.conf -- nachos.ag.DonationGrader -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1pq.conf -- nachos.ag.PriorityGrader -# threads=10,times=10,length=1000 -s 23049701 
java -classpath bin nachos.machine.Machine -[] conf/proj1pq.conf -- nachos.ag.PriorityGraderS1 -# threads=100,times=100,locks=1 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1rr.conf -- nachos.ag.ThreadGrader1 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1rr.conf -- nachos.ag.ThreadGrader2 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1rr.conf -- nachos.ag.ThreadGrader3 -s 23049701
java -classpath bin nachos.machine.Machine -[] conf/proj1rr.conf -- nachos.ag.ThreadGrader4 -s 23049701 
java -classpath bin nachos.machine.Machine -[] conf/proj1pq.conf -- nachos.ag.ThreadGrader5 -s 23049701 
java -classpath bin nachos.machine.Machine -[] conf/proj1pq.conf -- nachos.ag.ThreadGrader6 -s 23049701
pause
