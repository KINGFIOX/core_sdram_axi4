###############################################################################
# Variables
###############################################################################
OUTPUT_DIR       ?= build/verilated
RTL_DIR          ?= build/rtl
SRC_V_DIR        ?= src/verilog
NAME             ?= $(TOP)

RTL_SV_SRC       ?= $(shell find $(RTL_DIR) -name '*.sv')
RTL_V_DIRS       ?= $(shell find $(RTL_DIR) -type d)
EXTRA_V_SRC      ?= $(wildcard $(SRC_V_DIR)/*.v)

# Verilator options
VERILATE_PARAMS  ?= --trace
VERILATOR_OPTS   ?= --pins-sc-uint

TARGETS          ?= $(OUTPUT_DIR)/V$(NAME)

###############################################################################
# Rules
###############################################################################
all: $(TARGETS)

$(OUTPUT_DIR):
	mkdir -p $@

$(OUTPUT_DIR)/V$(NAME): $(RTL_SV_SRC) $(EXTRA_V_SRC) | $(OUTPUT_DIR)
	verilator --sc \
		$(RTL_SV_SRC) \
		$(EXTRA_V_SRC) \
		$(addprefix -I,$(RTL_V_DIRS)) \
		--top $(NAME) \
		--Mdir $(OUTPUT_DIR) \
		$(VERILATOR_OPTS) $(VERILATE_PARAMS)

clean:
	rm -rf $(TARGETS) $(OUTPUT_DIR)
