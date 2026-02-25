###############################################################################
# Variables
###############################################################################
CXX            ?= ccache g++
VERILATOR_SRC  ?= /usr/share/verilator/include
SYSTEMC_HOME   ?= /usr/local/systemc-2.3.1
SYSTEMC_LIBDIR ?= $(SYSTEMC_HOME)/lib-linux64

SRC_DIR       ?= build/verilated/
OBJ_DIR       ?= build/obj_verilated/
LIB_DIR       ?= build/lib/

LIBNAME       ?= libsyscverilated.a

# Additional include directories
INCLUDE_PATH ?=
INCLUDE_PATH += $(SRC_DIR)
INCLUDE_PATH += $(SYSTEMC_HOME)/include
INCLUDE_PATH += $(VERILATOR_SRC)
INCLUDE_PATH += $(VERILATOR_SRC)/vltstd

# Flags
CFLAGS       ?=
CFLAGS       += -DVM_TRACE=1
CFLAGS       += -fpic
CFLAGS       += $(patsubst %,-I%,$(INCLUDE_PATH))
CFLAGS       += $(EXTRA_CFLAGS)

LIB_OPT      ?= -L$(SYSTEMC_LIBDIR) -lsystemc

# SRC / Object list
src2obj       = $(OBJ_DIR)$(patsubst %$(suffix $(1)),%.o,$(notdir $(1)))
SRC_LIST      = $(foreach src,$(SRC_DIR),$(wildcard $(src)/*.cpp))
SRC_LIST     += $(VERILATOR_SRC)/verilated.cpp
SRC_LIST     += $(VERILATOR_SRC)/verilated_vcd_c.cpp
SRC_LIST     += $(VERILATOR_SRC)/verilated_vcd_sc.cpp
SRC_LIST     += $(VERILATOR_SRC)/verilated_threads.cpp

OBJ          ?= $(foreach src,$(SRC_LIST),$(call src2obj,$(src)))

###############################################################################
# Rules
###############################################################################
define template_c
$(call src2obj,$(1)): $(1) | $(OBJ_DIR)
	$(CXX) $(CFLAGS) -c $$< -o $$@
endef

all: $(LIB_DIR)$(LIBNAME)

$(OBJ_DIR) $(LIB_DIR):
	mkdir -p $@

$(foreach src,$(SRC_LIST),$(eval $(call template_c,$(src))))

$(LIB_DIR)$(LIBNAME): $(OBJ) | $(LIB_DIR) 
	$(CXX) -shared -o $(LIB_DIR)$(LIBNAME) $(LIB_OPT) $(OBJ)

clean:
	rm -rf $(LIB_DIR) $(OBJ_DIR)
