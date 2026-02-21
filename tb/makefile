###############################################################################
## Tool paths
###############################################################################
VERILATOR_SRC  ?= /usr/share/verilator/include
SYSTEMC_HOME   ?= /usr/local/systemc-2.3.1
SYSTEMC_LIBDIR ?= $(SYSTEMC_HOME)/lib-linux64

export VERILATOR_SRC
export SYSTEMC_HOME
export SYSTEMC_LIBDIR

GDB_DASHBOARD  ?= ../.gdb-dashboard
GDB_ARGS       ?= --iterations 10 --trace 0

###############################################################################
## Makefile
###############################################################################
all: run

elaborate:
	cd .. && make elaborate

build: elaborate
	make -f makefile.generate_verilated
	make -f makefile.build_verilated
	make -f makefile.build_sysc_tb

debug: elaborate
	make -f makefile.generate_verilated
	make -f makefile.build_verilated EXTRA_CFLAGS="-g -O0"
	make -f makefile.build_sysc_tb EXTRA_CFLAGS="-g -O0" EXTRA_LDFLAGS="-g"

clean:
	make -f makefile.generate_verilated $@
	make -f makefile.build_verilated $@
	make -f makefile.build_sysc_tb $@
	-rm *.vcd

run: build
	./build/test.x --trace 1 --iterations 100

gdb: debug
	gdb -q -x $(GDB_DASHBOARD) -ex "set args $(GDB_ARGS)" ./build/test.x

view:
	gtkwave verilator.vcd
