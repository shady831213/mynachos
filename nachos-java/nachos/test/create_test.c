#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
  int file;

  if (argc!=2) {
    printf("Usage: create <filename>\n");
    return 1;
  }

  file = creat(argv[1]);
  if (file==-1) {
    printf("Unable to create %s\n", argv[1]);
    return 1;
  }
  if (file!=2) {
    printf("file desp should be 2, but it is %0d\n", file);
    return 1;
  }
  //close(file);

  return 0;
}
