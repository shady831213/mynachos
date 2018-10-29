#!/bin/sh

export ARCHDIR=${PWD}/bin/gcc-mips/bin
export PATH=${PWD}/bin:$ARCHDIR:$PATH
export LD_LIBRARY_PATH=${PWD}/bin/gcc-mips/lib:${PWD}/bin/gcc-mips-dep/lib:$LD_LIBRARY_PATH
