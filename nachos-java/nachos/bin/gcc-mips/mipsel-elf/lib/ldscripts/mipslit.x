/* Default linker script, for normal executables */
OUTPUT_FORMAT("ecoff-littlemips", "ecoff-bigmips",
	      "ecoff-littlemips")
SEARCH_DIR("/home/yli/work/java/nachos/nachos-java/nachos/bin/gcc-mips/mips-dec-ultrix42/lib");
ENTRY (__start)
SECTIONS
{
  . = 0x400000 + SIZEOF_HEADERS;
  .text : {
     _ftext = . ;
    *(.init)
     eprol  =  .;
    *(.text)
    *(.fini)
     etext  =  .;
     _etext  =  .;
  }
  . = 0x10000000;
  .rdata : {
    *(.rdata)
  }
   _fdata = ALIGN(16);
  .data : {
    *(.data)
    CONSTRUCTORS
  }
   HIDDEN (_gp = ALIGN (16) + 0x8000);
  .lit8 : {
    *(.lit8)
  }
  .lit4 : {
    *(.lit4)
  }
  .sdata : {
    *(.sdata)
  }
   edata  =  .;
   _edata  =  .;
   _fbss = .;
  .sbss : {
    *(.sbss)
    *(.scommon)
  }
  .bss : {
    *(.bss)
    *(COMMON)
  }
   end = .;
   _end = .;
}
