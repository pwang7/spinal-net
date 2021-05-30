import random
from queue import Queue
import cocotb
# from cocotb.result import TestFailure
# from cocotb.result import TestSuccess
# from cocotb.clock import Clock
# from cocotb.triggers import Timer
# from cocotb.drivers.amba import AXI4LiteMaster
# from cocotb.drivers.amba import AXIProtocolError

# CLK_PERIOD_NS = 10

# def setup_dut(dut):
#     cocotb.fork(Clock(dut.clk, CLK_PERIOD_NS, units='ns').start())

# @cocotb.test()
# async def write_and_read(dut):
#     """Write to the register at address 0.
#     Read back from that register and verify the value is the same.
#     Test ID: 2
#     Expected Results:
#         The contents of the register is the same as the value written.
#     """

#     # Reset
#     dut.rst <= 1
#     dut.test_id <= 2
#     axim = AXI4LiteMaster(dut, "bus", dut.clk)
#     setup_dut(dut)
#     await Timer(CLK_PERIOD_NS * 10, units='ns')
#     dut.reset <= 0

#     ADDRESS = 0x00
#     DATA = 0xAB

#     # Write to the register
#     await axim.write(ADDRESS, DATA)
#     await Timer(CLK_PERIOD_NS * 10, units='ns')

#     # Read back the value
#     value = await axim.read(ADDRESS)
#     await Timer(CLK_PERIOD_NS * 10, units='ns')

#     value = dut.dut.r_temp_0
#     if value != DATA:
#         # Fail
#         raise TestFailure("Register at address 0x%08X should have been: \
#                            0x%08X but was 0x%08X" % (ADDRESS, DATA, int(value)))

#     dut._log.info("Write 0x%08X to address 0x%08X" % (int(value), ADDRESS))


from cocotb.triggers import RisingEdge, Timer

from cocotblib.Stream import StreamDriverMaster, Stream, Transaction, StreamDriverSlave, StreamMonitor
from cocotblib.misc import ClockDomainAsyncReset, simulationSpeedPrinter, randBits, SimulationTimeout



class AxiLite4:
    def __init__(self,dut,name):
        self.ar = Stream(dut,name + "_ar")
        self.r  = Stream(dut, name + "_r")
        self.aw = Stream(dut, name + "_aw")
        self.w  = Stream(dut, name + "_w")
        self.b  = Stream(dut, name + "_b")


class AxiLite4Master:
    def __init__(self, axiLite, clk, reset):
        self.axiLite = axiLite
        self.clk = clk
        self.awQueue = Queue()
        self.arQueue = Queue()
        self.wQueue = Queue()
        self.awDriver = StreamDriverMaster(axiLite.aw, lambda : self.awQueue.get_nowait() if not self.awQueue.empty() else None, clk, reset)
        self.arDriver = StreamDriverMaster(axiLite.ar, lambda : self.arQueue.get_nowait() if not self.arQueue.empty() else None, clk, reset)
        self.wDriver = StreamDriverMaster(axiLite.w, lambda : self.wQueue.get_nowait() if not self.wQueue.empty() else None, clk, reset)
        StreamDriverSlave(axiLite.r, clk, reset)
        StreamDriverSlave(axiLite.b, clk, reset)

    @cocotb.coroutine
    def write(self, address, data):
        aw = Transaction()
        aw.addr = address
        aw.prot = 0
        self.awQueue.put(aw)
        w = Transaction()
        w.data = data
        w.strb = 0xF
        self.wQueue.put(w)
        while True:
            yield RisingEdge(self.clk)
            if self.axiLite.b.valid == True and self.axiLite.b.ready == True:
                break


    @cocotb.coroutine
    def read(self, address, rsp):
        ar = Transaction()
        ar.addr = address
        ar.prot = 0
        self.arQueue.put(ar)
        while True:
            yield RisingEdge(self.clk)
            if self.axiLite.r.valid == True and self.axiLite.r.ready == True:
                rsp[0] = int(self.axiLite.r.payload.data)
                break

    @cocotb.coroutine
    def readAssert(self,address, value, mask ,message):
        rsp = [0]
        yield self.read(address, rsp)
        print("rsp=", rsp)
        assert rsp[0] & mask == value, message

@cocotb.test()
def test1(dut):
    random.seed(0)
    cocotb.fork(ClockDomainAsyncReset(dut.clk, dut.reset))
    cocotb.fork(simulationSpeedPrinter(dut.clk))
    cocotb.fork(SimulationTimeout(1000 * 8000))

    axiLite = AxiLite4(dut, "io_bus")
    master = AxiLite4Master(axiLite, dut.clk, dut.reset)

    CONFIG_DATA = 0x13
    STATUS_DATA = 0x00
    AVAIL_DATA = 0x04
    WDATA = 0xAB
    yield RisingEdge(dut.clk)
    yield master.readAssert(0x00, CONFIG_DATA, 0xFF, "config not match")
    yield master.readAssert(0x2C, STATUS_DATA, 0xFF, "status not match")
    yield master.readAssert(0x14, AVAIL_DATA, 0xFF, "buffer size not match")
    yield master.write(0x10, WDATA)
    #yield master.readAssert(0x20, WDATA, 0xFF, "TX != RX")

    # yield RisingEdge(dut.clk)
    # yield master.readAssert(0x00, 0, 1, "wavePlayer.phase.run was read as True, but should be False")
    # yield master.readAssert(0x10, 1, 1, "wavePlayer.filter.bypass was read as False, but should be True")
    # yield master.write(4, 0x80)
    # yield master.write(0, 1)
    # yield master.readAssert(0x00, 1, 1, "wavePlayer.phase.run was read as False, but should be True")
    # phaseValue = 0
    # for i in range(4):
    #     newValue = [0]
    #     yield master.read(0x8, newValue)
    #     assert newValue[0] > phaseValue, "wavePlayer.phase.value doesn't seem to increment"
    #     phaseValue = newValue[0]
    #     yield RisingEdge(dut.clk)


    # yield analyseFreq(dut,0x200)
    # yield Timer(1000*2000)

    # yield master.write(0x14, 0x10)
    # yield master.write(0x10, 0)
    # yield master.readAssert(0x10, 0, 1, "wavePlayer.filter.bypass was read as True, but should be False")

    # yield Timer(1000*2000)
    # yield analyseFreq(dut,0x200)
    yield Timer(1000*2000)