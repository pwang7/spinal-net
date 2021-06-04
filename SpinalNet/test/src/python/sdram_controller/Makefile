#SIM ?= icarus
#COCOTB_LOG_LEVEL ?= DEBUG
#COCOTB_SCHEDULER_DEBUG ?= 1
#COCOTB_RESOLVE_X ?= ZEROS

# ../../../../../simWorkspace/SdramControllerTb/rtl/SdramControllerTb.v
VERILOG_SOURCES += ../../../../../simWorkspace/SdramController/rtl/SdramController.v ../../verilog/tb_sdram_controller.v ../../verilog/mt48lc16m16a2.v ../common/timescale.v
TOPLEVEL=tb_sdram_controller
MODULE=SdramControllerTest

# Python path to cocotblib
export PYTHONPATH:=..
export COCOTB_RESOLVE_X:=ZEROS
include $(shell cocotb-config --makefiles)/Makefile.sim
#include ../common/Makefile.sim
