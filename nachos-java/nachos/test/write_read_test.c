#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define BUFSIZE 1024

char buf[BUFSIZE];
char src_buf[3*BUFSIZE];
char dst_buf[3*BUFSIZE];

int main(int argc, char** argv)
{
  int i, src, dst, amount;

  for(i = 0; i < 3*BUFSIZE; i++) {
    src_buf[i] = i % 97;
  }

  if (argc!=3) {
    printf("Usage: write_read <src> <dst>\n");
    return 1;
  }

  src = creat(argv[1]);
  if (src==-1) {
    printf("Unable to open %s\n", argv[1]);
    return 1;
  }

  if (write(src, src_buf, 3*BUFSIZE) == -1) {
      printf("write failed to %s\n", argv[1]);
      return 1;
  }
  close(src);
  src = open(argv[1]);

  creat(argv[2]);
  dst = open(argv[2]);
  if (dst==-1) {
    printf("Unable to create %s\n", argv[2]);
    return 1;
  }


  while ((amount = read(src, buf, BUFSIZE))>0) {
    write(dst, buf, amount);
  }

  close(src);
  close(dst);

  dst = open(argv[2]);

  if ((amount = read(dst, dst_buf, 3*BUFSIZE)) == -1) {
      printf("read failed from %s\n", argv[2]);
      return 1;
  }
  close(dst);

  for(i = 0; i < 3*BUFSIZE; i++) {
    if (dst_buf[i] != i % 97) {
        printf("%d data expected to %d, but get %d\n", i, i %97, dst_buf[i]);
        return 1;
    }
  }

  return 0;
}
