###############################################################################
# Variables
###############################################################################
CC             ?= ccache gcc
CXX            ?= ccache g++
VERILATOR_SRC  ?= /usr/share/verilator/include
SYSTEMC_HOME   ?= /usr/local/systemc-2.3.1
SYSTEMC_LIBDIR ?= $(SYSTEMC_HOME)/lib-linux64

OBJ_DIR      ?= build/obj/
EXE_DIR      ?= build/
SRC_DIR      ?= src/cxx/

TARGET       ?= test.x

# Additional include directories
INCLUDE_PATH ?=
INCLUDE_PATH += $(SRC_DIR)
INCLUDE_PATH += build/verilated
INCLUDE_PATH += $(VERILATOR_SRC)
INCLUDE_PATH += $(VERILATOR_SRC)/vltstd
INCLUDE_PATH += $(SYSTEMC_HOME)/include

# Dependancies
LIB_PATH     ?=
LIB_PATH     += build/lib 
LIBS          = -lsyscverilated

# Flags
CFLAGS       ?= -fpic -O2
CFLAGS       += $(patsubst %,-I%,$(INCLUDE_PATH))
CFLAGS       += -DVM_TRACE=1
CFLAGS       += $(BUS_CFLAGS)
LDFLAGS      ?= -O2
LDFLAGS      += -L$(SYSTEMC_LIBDIR) 
LDFLAGS      += $(patsubst %,-L%,$(LIB_PATH))

EXTRA_CLEAN_FILES ?=

# SRC / Object list
src2obj       = $(OBJ_DIR)$(patsubst %$(suffix $(1)),%.o,$(notdir $(1)))
SRC_CXX      ?= $(foreach src,$(SRC_DIR),$(wildcard $(src)/*.cpp))
SRC_C        ?= $(foreach src,$(SRC_DIR),$(wildcard $(src)/*.c))
SRC_CXX      := $(filter-out $(addprefix %/,$(notdir $(SRC_EXCLUDE))),$(SRC_CXX))
SRC_C        := $(filter-out $(addprefix %/,$(notdir $(SRC_EXCLUDE))),$(SRC_C))
SRC          ?= $(SRC_CXX) $(SRC_C)
OBJ          ?= $(foreach src,$(SRC),$(call src2obj,$(src)))

###############################################################################
# Rules
###############################################################################
define template_cxx
$(call src2obj,$(1)): $(1) | $(OBJ_DIR)
	$(CXX) $(CFLAGS) -c $$< -o $$@
endef

define template_c
$(call src2obj,$(1)): $(1) | $(OBJ_DIR)
	$(CC) $(CFLAGS) -c $$< -o $$@
endef

all: $(EXE_DIR)$(TARGET)

$(OBJ_DIR) $(EXE_DIR):
	mkdir -p $@

$(foreach src,$(SRC_CXX),$(eval $(call template_cxx,$(src))))
$(foreach src,$(SRC_C),$(eval $(call template_c,$(src))))

$(EXE_DIR)$(TARGET): $(OBJ) | $(EXE_DIR) 
	$(CXX) $(LDFLAGS) $(OBJ) -o $@ -lsystemc $(LIBS)

clean:
	rm -rf $(EXE_DIR) $(OBJ_DIR) $(EXTRA_CLEAN_FILES)
