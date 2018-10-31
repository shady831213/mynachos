#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
  int file, reopen_file;

  if (argc!=2) {
    printf("Usage: close <filename>\n");
    return 1;
  }
  file = creat(argv[1]);
  if (file==-1) {
    printf("Unable to open %s\n", argv[1]);
    return 1;
  }
  if (file!=2) {
    printf("file desp should be 2, but it is %0d\n", file);
    return 1;
  }
  if (close(file)!=0) {
    printf("close file failed\n");
    return 1;
  }
  //reopen
  reopen_file = open(argv[1]);
  if (reopen_file!=file) {
    printf("reopen file desp should be %0d, but it is %0d\n", file, reopen_file);
    return 1;
  }

  close(reopen_file);
  return 0;
}