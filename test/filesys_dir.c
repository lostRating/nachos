#include "filesysgrader.h"
#include "stdio.h"
#include "stdlib.h"

char buf[100];

int main(int argc, char** argv)
{
    int size = getFreeDiskSize();
    assertTrueWMsg(!mkdir("dir1"), "error1\n");
    assertTrueWMsg(!mkdir("dir1/dir1_1"), "error2\n");
    printCwd();
    chdir("dir1/dir1_1");
    printCwd();
    chdir("../../dir1/dir1_1/.././..");// /dir1/dir1_1/../../dir1/dir1_1/.././..==/
    printCwd();
    mkdir("dir2");// mkdir /dir2
    chdir("dir2");// cwd /dir2
	//printCwd();
    //assertTrueWMsg(rmdir("/dir1")); // for dir1_1 in /dir1 corrected editor:MLSheng
    assertTrueWMsg(rmdir("dir1"), "error3\n");  // test path added by MLSheng
	//printCwd();
    assertTrueWMsg(rmdir("/dir1/dir1"), "error4\n");//of couse only /dir1/dir1_1 exists
	//printCwd();
	assertTrueWMsg(!rmdir("/dir1/dir1_1"), "error5\n");// or size is not correct corrected editor:MLSheng
	//printCwd();
	assertTrueWMsg(!rmdir("/dir1"), "error6\n");// or size is not correct corrected editor:MLSheng
    printCwd();// /dir2
    chdir("/");// cwd /
    assertTrueWMsg(!rmdir("/dir2"), "error7\n");
    assertTrueWMsg(getFreeDiskSize() == size, "error8\n");
    done();
    return 0;
}
