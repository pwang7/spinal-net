import random
from queue import Queue

import cocotb
from cocotb.scoreboard import Scoreboard
from cocotb.triggers import RisingEdge

from cocotblib.Phase import PhaseManager, Infrastructure, PHASE_SIM
from cocotblib.Scorboard import ScorboardInOrder
from cocotblib.Stream import StreamDriverMaster, Stream, Transaction, StreamDriverSlave, StreamMonitor
from cocotblib.misc import ClockDomainAsyncReset, simulationSpeedPrinter, randBits, SimulationTimeout

TEST_DATA = ['Hello World!', 'Hello DatenLord!', 'Hello Spinal!', 'Hello Cocotb!']

class DriverAgent(Infrastructure):
    def __init__(self, name, parent, dut):
        Infrastructure.__init__(self, name, parent)

        StreamDriverMaster(Stream(dut,"io_rx_cmd"), self.genRxCmd(), dut.clk, dut.reset)
        StreamDriverMaster(Stream(dut, "io_rx_data"), self.genRxData(), dut.clk, dut.reset)
        StreamDriverSlave(Stream(dut, "io_tx_cmd"), dut.clk, dut.reset)
        StreamDriverSlave(Stream(dut, "io_tx_data"), dut.clk, dut.reset)

    def genRxCmd(self):
        while self.getPhase() != PHASE_SIM:
            yield None

        for i in range(len(TEST_DATA)):
            cmd = Transaction()
            cmd.ip = 0x11223344
            cmd.srcPort = 16
            cmd.dstPort = 37984
            cmd.length  = len(TEST_DATA[i])
            cmd.nextDelay = i
            yield cmd

        while True:
            yield None

    def genRxData(self):
        while self.getPhase() != PHASE_SIM:
            yield None

        for i in range(len(TEST_DATA)):
            for j in range(len(TEST_DATA[i])):
                data = Transaction()
                data.last = int(j == len(TEST_DATA[i]) - 1)
                data.fragment = (TEST_DATA[i].encode())[j]
                data.nextDelay = i + j
                yield data

        while True:
            yield None



class MonitorAgent(Infrastructure):
    def __init__(self, name, parent, dut):
        Infrastructure.__init__(self, name ,parent)

        self.txCmdScordboard = ScorboardInOrder("txCmdScordboard", self)
        self.txDataScordboard = ScorboardInOrder("txDataScordboard", self)
        StreamMonitor(Stream(dut,"io_tx_cmd"), self.txCmdScordboard.uutPush, dut.clk, dut.reset)
        StreamMonitor(Stream(dut, "io_tx_data"), self.txDataScordboard.uutPush, dut.clk, dut.reset)


        for i in range(len(TEST_DATA)):
            cmd = Transaction()
            cmd.ip = 0x11223344
            cmd.dstPort = 16
            cmd.srcPort = 37984
            cmd.length  = len(TEST_DATA[i])
            self.txCmdScordboard.refPush(cmd)

        for i in range(len(TEST_DATA)):
            for j in range(len(TEST_DATA[i])):
                data = Transaction()
                data.last = int(j == len(TEST_DATA[i]) - 1)
                data.fragment = (TEST_DATA[i].encode())[j]
                self.txDataScordboard.refPush(data)

    def hasEnoughSim(self):
        return (self.txCmdScordboard.refsCounter == self.txCmdScordboard.uutsCounter
                and self.txDataScordboard.refsCounter == self.txDataScordboard.uutsCounter)

@cocotb.test()
def testUdpLoopback(dut):
    random.seed(0)

    cocotb.fork(ClockDomainAsyncReset(dut.clk, dut.reset))
    cocotb.fork(simulationSpeedPrinter(dut.clk))
    cocotb.fork(SimulationTimeout(1000 * 2000))

    phaseManager = PhaseManager()
    phaseManager.setWaitTasksEndTime(1000 * 200)


    DriverAgent("driver", phaseManager, dut)
    MonitorAgent("monitor", phaseManager, dut)


    yield phaseManager.run()
