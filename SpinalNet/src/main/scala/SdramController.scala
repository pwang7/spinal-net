package sdram

import scala.math.BigDecimal.RoundingMode
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.fsm._
import spinal.lib.io.TriStateArray
import spinal.lib.memory.sdram._
import spinal.lib.memory.sdram.sdr.{MT48LC16M16A2, SdramTimings, SdramInterface}

case class SdramConfig(
    CAS: Int = 3,
    burstLen: Int = 8,
    idWidth: Int = 4
) {
  val burstLenWidth = log2Up(burstLen)
}

case class SdramCmd(l: SdramLayout, c: SdramConfig) extends Bundle {
  val address = Bits(l.wordAddressWidth bits)
  val write = Bool
  val data = Bits(l.dataWidth bits)
  val burstLen = UInt(c.burstLenWidth bits)
  val mask = Bits(l.bytePerWord bits)
  val opId = UInt(c.idWidth bits)
  val last = Bool
}

case class SdramRsp(l: SdramLayout, c: SdramConfig) extends Bundle {
  val data = Bits(l.dataWidth bits)
  val opId = UInt(c.idWidth bits)
  val last = Bool
}

case class SdramBus(l: SdramLayout, c: SdramConfig)
    extends Bundle
    with IMasterSlave {
  val cmd = Stream(SdramCmd(l, c))
  val rsp = Stream(SdramRsp(l, c))

  override def asMaster(): Unit = {
    master(cmd)
    slave(rsp)
  }
}

class SdramController(l: SdramLayout, t: SdramTimings, c: SdramConfig)
    extends Component {
  require(c.burstLen >= 4, "burst length at least 4")
  require(
    c.burstLen <= l.columnSize,
    "burst length should be less than column size"
  )

  val CMD_UNSELECTED = B"4'b1000"
  val CMD_NOP = B"4'b0111"
  val CMD_ACTIVE = B"4'b0011"
  val CMD_READ = B"4'b0101"
  val CMD_WRITE = B"4'b0100"
  val CMD_BURST_TERMINATE = B"4'b0110"
  val CMD_PRECHARGE = B"4'b0010"
  val CMD_REFRESH = B"4'b0001"
  val CMD_LOAD_MODE_REG = B"4'b0000"

  val DQM_ALL_VALID = B(0, l.bytePerWord bits) // 2'b00
  val DQM_ALL_INVALID = ~DQM_ALL_VALID // High means invalid
  val DQM_READ_DELAY_CYCLES = 2

  val MODE_VALUE =
    B"6'b000_0_00" ## B(c.CAS, 3 bits) ## B"4'b0_111" // sequential full page
  val ALL_BANK_ADDR = 1 << 10

  val io = new Bundle {
    val bus = slave(SdramBus(l, c)) // setAsReg()
    val sdram = master(SdramInterface(l)) // setAsReg
    val initDone = out Bool

    bus.addAttribute(name = "IOB", value = "TRUE")
    sdram.addAttribute(name = "IOB", value = "TRUE")
    initDone.addAttribute(name = "IOB", value = "TRUE")
  }

  val commandReg = Reg(Bits(4 bits)) init (0)
  val addressReg = Reg(Bits(l.chipAddressWidth bits)) init (0)
  val bankAddrReg = Reg(Bits(l.bankWidth bits)) init (0)
  val rowAddrReg = Reg(Bits(l.rowWidth bits)) init (0)
  val columnAddrReg = Reg(Bits(l.columnWidth bits)) init (0)
  val readDataReg = Reg(Bits(l.dataWidth bits)) init (0)
  val opIdReg = Reg(UInt(c.idWidth bits)) init (0)

  io.sdram.BA := bankAddrReg
  io.sdram.ADDR := addressReg
  io.sdram.DQM := io.bus.cmd.valid ? (DQM_ALL_VALID | ~io.bus.cmd.mask) | DQM_ALL_INVALID
  io.sdram.CKE := True
  io.sdram.CSn := commandReg(3)
  io.sdram.RASn := commandReg(2)
  io.sdram.CASn := commandReg(1)
  io.sdram.WEn := commandReg(0)

  io.bus.rsp.data := readDataReg
  io.bus.rsp.opId := opIdReg
  io.bus.rsp.last := False
  io.sdram.DQ.write := io.bus.cmd.data

  io.initDone := False

  val cmdReady = Bool
  val rspValid = Bool
  io.bus.cmd.ready := cmdReady
  io.bus.rsp.valid := rspValid

  assert(
    assertion = (columnAddrReg.asUInt < l.columnSize - c.burstLen),
    message = "invalid column address and burst length",
    severity = ERROR
  )

  val clkFrequancy = ClockDomain.current.frequency.getValue
  def timeToCycles(time: TimeNumber): BigInt =
    (clkFrequancy * time).setScale(0, RoundingMode.UP).toBigInt

  def cycleCounter(cycleMax: BigInt) = new Area {
    val counter = Reg(UInt(log2Up(cycleMax) bits)) init (0)
    val busy = counter =/= 0
    if (cycleMax > 1) {
      when(busy) {
        counter := counter - 1
      }
    }
    def setCycles(cycles: BigInt) = {
      assert(
        cycles <= cycleMax && cycles > 0,
        s"invalid counter cycle: ${cycles}"
      )
      counter := cycles - 1
    }
    def setCycles(cycles: UInt) = {
      assert(
        cycles <= cycleMax && cycles > 0,
        s"invalid counter cycle: ${cycles}"
      )
      counter := (cycles - 1).resized
    }
    def setTime(time: TimeNumber) = setCycles(
      timeToCycles(time).max(1)
    ) // Minimum 1 cycles
  }
  def timeCounter(timeMax: TimeNumber) = cycleCounter(timeToCycles(timeMax))

  val stateCounter = timeCounter(t.tPOW)
  val refreshCounter = CounterFreeRun(timeToCycles(t.tREF / (1 << l.rowWidth)))

  val initPeriod = Bool
  val refreshReqReg = Reg(Bool) init (False)
  val writeReq = Bool
  val readReq = Bool

  commandReg := CMD_NOP

  val initFsm = new StateMachine {
    val INIT_WAIT: State = new State with EntryPoint {
      onEntry {
        stateCounter.setTime(t.tPOW)
      } whenIsActive {
        when(!stateCounter.busy) {
          goto(INIT_PRECHARGE)
        }
      }
    }

    val INIT_PRECHARGE: State = new State {
      onEntry {
        addressReg := ALL_BANK_ADDR
        commandReg := CMD_PRECHARGE
        stateCounter.setTime(t.tRP)
      } whenIsActive {
        when(!stateCounter.busy) {
          goto(INIT_REFRESH_1)
        }
      }
    }

    val INIT_REFRESH_1: State = new State {
      onEntry {
        commandReg := CMD_REFRESH
        stateCounter.setTime(t.tRFC)
      } whenIsActive {
        when(!stateCounter.busy) {
          goto(INIT_REFRESH_2)
        }
      }
    }

    val INIT_REFRESH_2: State = new State {
      onEntry {
        commandReg := CMD_REFRESH
        stateCounter.setTime(t.tRFC)
      } whenIsActive {
        when(!stateCounter.busy) {
          goto(INIT_LOAD_MODE_REG)
        }
      }
    }

    val INIT_LOAD_MODE_REG: State = new State {
      onEntry {
        addressReg := MODE_VALUE
        commandReg := CMD_LOAD_MODE_REG
        stateCounter.setCycles(t.cMRD)
      } whenIsActive {
        when(!stateCounter.busy) {
          exit()
        }
      }
    }
  }

  val refreshFsm = new StateMachine {
    val REFRESH_PRECHARGE: State = new State with EntryPoint {
      onEntry {
        addressReg := ALL_BANK_ADDR
        commandReg := CMD_REFRESH
        stateCounter.setTime(t.tRP)
      } whenIsActive {
        when(!stateCounter.busy) {
          goto(REFRESH)
        }
      }
    }

    val REFRESH: State = new State {
      onEntry {
        commandReg := CMD_PRECHARGE
        stateCounter.setTime(t.tRFC)
      } whenIsActive {
        when(!stateCounter.busy) {
          exit()
        }
      } onExit {
        refreshReqReg := False
      }
    }
  }

  val writeFsm = new StateMachine {
    val BURST_WRITE: State = new State with EntryPoint {
      onEntry {
        addressReg := columnAddrReg.resized
        commandReg := CMD_WRITE
        stateCounter.setCycles(io.bus.cmd.burstLen)
      } whenIsActive {
        when(!stateCounter.busy || io.bus.cmd.last) {
          goto(TERM_WRITE)
        }
      }
    }

    val TERM_WRITE: State = new State {
      onEntry {
        commandReg := CMD_BURST_TERMINATE
      } whenIsActive {
        exit()
      }
    }
  }

  val readFsm = new StateMachine {
    val SEND_READ_CMD: State = new State with EntryPoint {
      onEntry {
        addressReg := columnAddrReg.resized
        commandReg := CMD_READ
        stateCounter.setCycles(c.CAS)
      } whenIsActive {
        when(!stateCounter.busy) {
          goto(BURST_READ)
        }
      }
    }

    val BURST_READ: State = new State {
      onEntry {
        stateCounter.setCycles(io.bus.cmd.burstLen)
      } whenIsActive {
        when(stateCounter.counter === c.CAS) {
          commandReg := CMD_BURST_TERMINATE
        } elsewhen (!stateCounter.busy) {
          exit()
        }
      } onExit {
        io.bus.rsp.last := True
      }
    }
  }

  val fsm = new StateMachine {
    val INIT: State = new StateFsm(initFsm) with EntryPoint {
      whenCompleted {
        goto(IDLE)
      } onExit {
        io.initDone := True
      }
    }

    val IDLE: State = new State {
      whenIsActive {
        when(refreshReqReg) {
          goto(REFRESH)
        } elsewhen (writeReq || readReq) {
          goto(ACTIVE)
        }
      }
    }

    val REFRESH: State = new StateFsm(refreshFsm) {
      whenCompleted {
        goto(IDLE)
      }
    }

    val ACTIVE: State = new State {
      onEntry {
        addressReg := rowAddrReg
        commandReg := CMD_ACTIVE
        bankAddrReg := io.bus.cmd
          .address(l.wordAddressWidth - l.bankWidth - 1, l.bankWidth bits)
        rowAddrReg := io.bus.cmd.address(
          (l.rowWidth + l.columnWidth - 1) downto l.columnWidth
        ) // Row address
        columnAddrReg := io.bus.cmd
          .address((l.columnWidth - 1) downto 0)
          .resized // Colume address
        opIdReg := io.bus.cmd.opId
        stateCounter.setTime(t.tRCD)
      } whenIsActive {
        when(!stateCounter.busy) {
          when(writeReq) {
            goto(WRITE)
          } otherwise {
            goto(READ)
          }
        }
      }
    }

    val WRITE: State = new StateFsm(writeFsm) {
      whenCompleted {
        goto(PRECHARGE)
      }
    }

    val READ: State = new StateFsm(readFsm) {
      whenCompleted {
        goto(PRECHARGE)
      }
    }

    val PRECHARGE: State = new State {
      onEntry {
        commandReg := CMD_PRECHARGE
      } whenIsActive {
        goto(IDLE)
      }
    }
  }

  val readArea = new ClockingArea(
    clockDomain = ClockDomain(
      clock = ClockDomain.current.clock,
      reset = ClockDomain.current.reset,
      config = ClockDomainConfig(clockEdge = FALLING)
    )
  ) {
    val readReg = RegNextWhen(io.sdram.DQ.read, io.sdram.DQ.writeEnable === 0)
  }
  readDataReg := readArea.readReg

  val readDataValidReg =
    RegNext(readFsm.isActive(readFsm.BURST_READ)) init (False)
  initPeriod := fsm.isActive(fsm.INIT)
  writeReq :=
    io.bus.cmd.write && io.bus.cmd.valid
  readReq :=
    !io.bus.cmd.write && io.bus.cmd.valid
  cmdReady := writeFsm.isActive(writeFsm.BURST_WRITE) || readFsm.isEntering(
    readFsm.SEND_READ_CMD
  )
  rspValid := writeFsm.isActive(writeFsm.TERM_WRITE) || readDataValidReg

  when(!initPeriod && refreshCounter.willOverflow) {
    refreshReqReg := True
  }

  when(writeFsm.isActive(writeFsm.BURST_WRITE)) {
    io.sdram.DQ.writeEnable.setAll()
  } otherwise {
    io.sdram.DQ.writeEnable := 0
  }
}

object SdramController {
  def main(args: Array[String]): Unit = {
    val device = MT48LC16M16A2
    SpinalConfig(defaultClockDomainFrequency = FixedFrequency(100 MHz))
      .generateVerilog(
        new SdramController(device.layout, device.timingGrade7, SdramConfig())
      )
  }
}
