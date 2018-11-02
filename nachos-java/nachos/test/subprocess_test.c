#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
  int pid, pexit_status;
  if (argc!=3) {
    printf("Usage: subprocess <src> <dst>\n");
    return 1;
  }

  pid = exec("write_read_test.coff", argc, argv);
  if (join(pid, &pexit_status) != 1) {
      printf("pid %0d exit abnormal!\n", pid);
      return 1;
  }

  if (pexit_status != 0) {
    printf("pid %0d, exit with %0d\n", pid, pexit_status);
    return 1;

  }

  if (open(argv[1]) == -1) {
    printf("Unable to open %s\n", argv[1]);
    return 1;
  }

  if (unlink(argv[1]) == -1) {
    printf("Unable to remove %s\n", argv[1]);
    return 1;
  }

  if (open(argv[1]) != -1) {
    printf("remove %s failed!\n", argv[1]);
    return 1;
  }

  if (open(argv[2]) == -1) {
    printf("Unable to open %s\n", argv[2]);
    return 1;
  }

  if (unlink(argv[2]) == -1) {
    printf("Unable to remove %s\n", argv[2]);
    return 1;
  }

  if (open(argv[2]) != -1) {
    printf("remove %s failed!\n", argv[2]);
    return 1;
  }
  return 0;
}
