BUILD_DIR  = build
RTL_DIR    = $(BUILD_DIR)/rtl

TOP  = SDRAMSimTop

CHISEL_SRC = $(wildcard scala/src/*.scala)

.PHONY: all elaborate clean init idea

all: elaborate

init:
	git submodule update --init --recursive

elaborate: $(RTL_DIR)/$(TOP).sv

$(RTL_DIR)/$(TOP).sv: $(CHISEL_SRC) build.sc common.sc
	mill -i scala.runMain sdram.Elaborate $(CURDIR)/$(RTL_DIR)

clean:
	rm -rf $(BUILD_DIR)

idea:
	mill -i mill.idea.GenIdea/idea

bsp:
	mill -i mill.bsp.BSP/install
