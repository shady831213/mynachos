#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
  int create_file, open_file;

  if (argc!=2) {
    printf("Usage: open <filename>\n");
    return 1;
  }
  create_file = creat(argv[1]);
  open_file = open(argv[1]);
  if (open_file==-1) {
    printf("Unable to open %s\n", argv[1]);
    return 1;
  }
  if (open_file!=create_file) {
    printf("file desp should be %0d, but it is %0d\n", create_file, open_file);
    return 1;
  }
  //close(file);

  return 0;
}
