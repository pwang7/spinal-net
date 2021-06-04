import cocotb
from cocotb.triggers import Timer, RisingEdge
from cocotblib.misc import simulationSpeedPrinter, cocotbXHack

async def ClockDomainAsyncResetCustom(clk, reset):
    if reset:
        reset <= 1
    clk <= 0
    await Timer(100000)
    if reset:
        reset <= 0
    while True:
        clk <= 0
        await Timer(3750)
        clk <= 1
        await Timer(3750)

async def waitUntil(clk, cond):
    while True:
        if cond():
            break
        await RisingEdge(clk)

@cocotb.test()
async def testFunc(dut):
    # Handle value X
    #cocotbXHack()

    cocotb.fork(ClockDomainAsyncResetCustom(dut.clk, dut.reset))
    cocotb.fork(simulationSpeedPrinter(dut.clk))

    await waitUntil(dut.clk, lambda: int(dut.io_initDone) == 1)
    dut.io_bus_rsp_ready <= False

    burstLen = 4
    rangeMax = 20
    for i in range(rangeMax):
        dut.io_bus_cmd_valid            <= True
        dut.io_bus_cmd_payload_address  <= i
        dut.io_bus_cmd_payload_write    <= True
        dut.io_bus_cmd_payload_data     <= i
        dut.io_bus_cmd_payload_burstLen <= burstLen
        dut.io_bus_cmd_payload_mask     <= 3
        dut.io_bus_cmd_payload_opId     <= 7
        dut.io_bus_cmd_payload_last     <= ((i + 1) % burstLen == 0)
        await RisingEdge(dut.clk)

        await waitUntil(dut.clk, lambda: (int(dut.io_bus_cmd_valid) == 1 and int(dut.io_bus_cmd_ready) == 1))
        print("write: addr={}, data={}, id={}, last={}".format(i, i, int(dut.io_bus_cmd_payload_opId), int(dut.io_bus_cmd_payload_last)))

    dut.io_bus_rsp_ready <= True
    for i in range(rangeMax):
        dut.io_bus_cmd_valid            <= True
        dut.io_bus_cmd_payload_address  <= i
        dut.io_bus_cmd_payload_write    <= False
        dut.io_bus_cmd_payload_data     <= i
        dut.io_bus_cmd_payload_burstLen <= burstLen
        dut.io_bus_cmd_payload_mask     <= 3
        dut.io_bus_cmd_payload_opId     <= 7
        dut.io_bus_cmd_payload_last     <= ((i + 1) % burstLen == 0)
        await RisingEdge(dut.clk)

        await waitUntil(dut.clk, lambda: (int(dut.io_bus_rsp_valid) == 1 and int(dut.io_bus_rsp_ready) == 1))
        read_data = int(dut.io_bus_rsp_payload_data)
        op_id = int(dut.io_bus_rsp_payload_opId)
        last = int(dut.io_bus_rsp_payload_last)
        print("read: addr={}, data={}, id={}, last={}".format(i, read_data, op_id, last))
        assert read_data == i, "read data not match"

    dut.io_bus_rsp_ready <= False
