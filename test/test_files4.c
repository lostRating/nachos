/*
 * Author: Ted Yin <ted.sybil@gmail.com>
 * License: GPL v3
 * Purpose: Test file management syscalls, including creat, open, read, write,
 * close, unlink. File descriptor management and critical implementaion
 * requirements are tested. (truncate version)
 */

#include "coffgrader.h"

#define FILE_NOT_EXIST "file_not_exist" /* specify a non-existing filename */
#define FILE_EXIST "cat.c" /* specify an existing filename */
#define FILE_EXIST_CONTENT "#include \"syscall.h\"" /* leading string of the
                                                       existing file */
#define NONSENSE "hello, world!" /* the string for testing read/write */
#define ITER_TIME 10 /* iteration time for testing robustness (less than 16) */

char rbuff[1024], wbuff[] = NONSENSE;
int main() {
    int i, fd, fd2, fdc, ret;
    int ec_length = strlen(FILE_EXIST_CONTENT);
    int nonsense_length = strlen(NONSENSE);
    int fdl = creat("filetest.log");
    for (i = 0; i < ITER_TIME; i++)
    {
        /* test the implementation difference between open() and creat() */
        fd = open(FILE_NOT_EXIST);
        fprintf(fdl, "try to open a non-existing file %s: %d\n", FILE_NOT_EXIST, fd);
        assertTrue(fd == -1); /* open a non-existing file should return -1 */
        fdc = open(FILE_EXIST);
        fprintf(fdl, "open an existing file FILE_EXIST: %d\n", fdc);
        assertTrue(fdc > -1);

        /* test read() syscall, read from the first FILE_EC_LENGTH chars from
         * FILE_EXIST and verify the content FILE_EXIST_CONTENT */
        ret = read(fdc, rbuff, ec_length); /* reading first 6 characters of
                                                     FILE_EXIST */
        assertTrue(ret == ec_length);
        rbuff[ret] = '\0';
        fprintf(fdl, "leading string is: %s\n", rbuff);
        assertTrue(strcmp(FILE_EXIST_CONTENT, rbuff) == 0);

        /* test creat() and write() */
        fd = creat("newfile");
        fprintf(fdl, "create newfile: %d\n", fd);
        assertTrue(fd > -1);
        assertTrue(write(fd, wbuff, nonsense_length) == nonsense_length);
        fprintf(fdl, "writen to newfile through %d\n", fd);

        /* test fd management by opening another fd which points to the same file
         * resource */
        fd2 = open("newfile");
        fprintf(fdl, "open newfile: %d\n", fd2);

        /* close the original fd, but fd2 should still be using the file */
        ret = close(fd);
        assertTrue(ret == 0);
        fprintf(fdl, "close %d: %d\n", fd, ret);

        /* open a new fd, OS should recycle the last closed fd */
        fd = open("newfile");
        fprintf(fdl, "open newfile: %d\n", fd);
        assertTrue(fd > -1);

        /* close fd2, but new fd should still be using the file */
        ret = close(fd2);
        assertTrue(ret == 0);
        fprintf(fdl, "close %d: %d\n", fd2, ret);

        /* open again, now there are two fd opening the same file resource */
        fd2 = open("newfile");
        fprintf(fdl, "open newfile: %d\n", fd2);

        /* test unlink, note that the file should not be removed now, since there
         * are two fd using it */
        unlink("newfile");
        fprintf(fdl, "trying to delete newfile\n");

        /* test compliance to the spec that any further attempt of opening the file
         * is rejected until the last fd is closed */
        ret = open("newfile");
        assertTrue(ret == -1);
        fprintf(fdl, "attempt to open: %d\n", ret);

        /* to prove the file is actually not deleted, try to read from it */
        ret = read(fd2, rbuff, nonsense_length);
        assertTrue(ret == nonsense_length);
        rbuff[ret] = '\0';
        fprintf(fdl, "read from newfile through %d: %s\n", fd2, rbuff);
        assertTrue(strcmp(NONSENSE, rbuff) == 0);

        /* close the fd2, there still should be one fd using the file  */
        ret = close(fd2);
        assertTrue(ret == 0);
        fprintf(fdl, "close %d: %d\n", fd2, ret);
        ret = creat("newfile");
        assertTrue(ret == -1);
        fprintf(fdl, "open newfile: %d\n", ret);

        /* close the last fd, then the file should be removed */
        ret = close(fd);
        assertTrue(ret == 0);
        fprintf(fdl, "close the last fd %d: %d\n", fd, ret);

        /* now the file can be created */
        fd = creat("newfile");
        assertTrue(fd > -1);
        fprintf(fdl, "open newfile: %d\n", fd);
        /* test if the file is newly created after last removal */
        assertTrue(read(fd, rbuff, strlen(wbuff)) == 0);
        /* add content for the following test */
        assertTrue(write(fd, wbuff, nonsense_length) == nonsense_length);
        fprintf(fdl, "writen to newfile through %d\n", fd);
        ret = close(fd);
        assertTrue(ret == 0);
        fprintf(fdl, "close %d: %d\n", fd, ret);

        fd = creat("newfile");
        assertTrue(fd > -1);
        fprintf(fdl, "open newfile: %d\n", fd);
        /* test if the file is truncated */
        assertTrue(read(fd, rbuff, strlen(wbuff)) == 0);
        ret = close(fd);
        assertTrue(ret == 0);
        fprintf(fdl, "close %d: %d\n", fd, ret);

        /* fdc is deliberately left unclosed to test robustness */
    }
    done();
    return 0;
}
