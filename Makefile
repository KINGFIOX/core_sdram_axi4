###############################################################################
## Tool paths
###############################################################################
VERILATOR_SRC  ?= /usr/share/verilator/include
SYSTEMC_HOME   ?= /usr/local/systemc-2.3.1
SYSTEMC_LIBDIR ?= $(SYSTEMC_HOME)/lib-linux64

export VERILATOR_SRC
export SYSTEMC_HOME
export SYSTEMC_LIBDIR

GDB_DASHBOARD  ?= .gdb-dashboard
GDB_ARGS       ?= --iterations 10 --trace 0

###############################################################################
## Directories
###############################################################################
BUILD_DIR  = build
RTL_DIR    = $(BUILD_DIR)/rtl

TOP  = SDRAMSimTop

CHISEL_SRC = $(wildcard src/scala/*.scala)

###############################################################################
## Targets
###############################################################################
.PHONY: all elaborate build debug run clean init idea bsp gdb view

all: run

init:
	git submodule update --init --recursive

elaborate: $(RTL_DIR)/$(TOP).sv

$(RTL_DIR)/$(TOP).sv: $(CHISEL_SRC) build.sc common.sc
	mill -i scala.runMain sdram.Elaborate $(CURDIR)/$(RTL_DIR)

build: elaborate
	make -f scripts/generate_verilated.mk
	make -f scripts/build_verilated.mk
	make -f scripts/build_sysc_tb.mk

debug: elaborate
	make -f scripts/generate_verilated.mk
	make -f scripts/build_verilated.mk EXTRA_CFLAGS="-g -O0"
	make -f scripts/build_sysc_tb.mk EXTRA_CFLAGS="-g -O0" EXTRA_LDFLAGS="-g"

run: build
	./build/test.x --trace 1 --iterations 50000

gdb: debug
	gdb -q -x $(GDB_DASHBOARD) -ex "set args $(GDB_ARGS)" ./build/test.x

view:
	gtkwave verilator.vcd

clean:
	make -f scripts/generate_verilated.mk $@
	make -f scripts/build_verilated.mk $@
	make -f scripts/build_sysc_tb.mk $@
	-rm -rf $(BUILD_DIR) *.vcd

idea:
	mill -i mill.idea.GenIdea/idea

bsp:
	mill -i mill.bsp.BSP/install
