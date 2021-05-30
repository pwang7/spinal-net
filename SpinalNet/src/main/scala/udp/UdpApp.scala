package udp

import spinal.core._
import spinal.lib._
import spinal.lib.fsm.{EntryPoint, StateParallelFsm, State, StateMachine}

case class UdpAppCmd() extends Bundle {
  val IP_WIDTH = 32
  val PORT_WIDTH = 16
  val LEN_WIDTH = 16

  val ip = Bits(IP_WIDTH bits)
  val srcPort = Bits(PORT_WIDTH bits)
  val dstPort = Bits(PORT_WIDTH bits)
  val length = UInt(LEN_WIDTH bits)
}

case class UdpAppBus() extends Bundle with IMasterSlave {
  val cmd = Stream(UdpAppCmd())
  val data = Stream(Fragment(Bits(8 bits)))

  override def asMaster(): Unit = master(cmd, data)
}

case class UdpApp(udpPort: Int = 37984) extends Component {
  val io = new Bundle {
    val rx = slave(UdpAppBus())
    val tx = master(UdpAppBus())
  }
  val MAX_LEN = scala.math.pow(2, io.rx.cmd.LEN_WIDTH).toInt

  val rxCnt = Counter(MAX_LEN, inc = io.rx.data.fire)
  val txCnt = Counter(MAX_LEN, inc = io.tx.data.fire)
  val fifo = new StreamFifo(Fragment(Bits(8 bits)), MAX_LEN)
  //fifo.io.push << io.rx.data
  //fifo.io.pop >> io.tx.data
  fifo.io.push.valid := False
  fifo.io.push.last := False
  fifo.io.push.fragment := 0
  fifo.io.pop.ready := False

  // Set default value to rx/tx output pins
  io.rx.cmd.ready := False
  io.rx.data.ready := False

  io.tx.cmd.valid := False
  io.tx.cmd.ip := io.rx.cmd.ip
  io.tx.cmd.srcPort := io.rx.cmd.dstPort
  io.tx.cmd.dstPort := io.rx.cmd.srcPort
  io.tx.cmd.length := rxCnt

  io.tx.data.valid := False
  io.tx.data.last := False
  io.tx.data.fragment := 0

  val flushRx = new Area {
    def apply(): Unit = {
      active := True
    }

    val active = RegInit(False)
    when(active) {
      io.rx.data.ready := True
      when(io.rx.data.valid && io.rx.data.last) {
        io.rx.cmd.ready := True
        active := False
      }
    }
  }

  val fsm = new StateMachine {
    val idle: State = new State with EntryPoint {
      whenIsActive {
        // Check io.rx.cmd dst port
        when(io.rx.cmd.valid && !flushRx.active) {
          switch(io.rx.cmd.dstPort) {
            is(udpPort) {
              goto(receiveData)
            }
            default {
              flushRx()
            }
          }
        }
      }
    }

    val receiveData = new State {
      onEntry {
        rxCnt.clear()
      }
      whenIsActive {
        // Put UDP receive data into FIFO
        fifo.io.push.fragment := io.rx.data.fragment
        fifo.io.push.last := io.rx.data.last
        fifo.io.push.valid := io.rx.data.valid
        io.rx.data.ready := True
        when(io.rx.data.valid && io.rx.data.last) {
          io.rx.cmd.ready := True
          goto(transmitData)
        }
      }
    }

    // Send cmd and data
    val transmitData = new StateParallelFsm(
      cmdFsm,
      dataFsm
    ) {
      whenCompleted {
        goto(idle)
      }
    }
  }

  //Inner FSM to send cmd
  lazy val cmdFsm = new StateMachine {
    val sendCmd = new State with EntryPoint {
      whenIsActive {
        // Send one io.tx.cmd transaction
        io.tx.cmd.valid := True
        when(io.tx.cmd.ready) {
          exit()
        }
      }
    }
  }

  // Inner FSM to send data
  lazy val dataFsm = new StateMachine {
    val sendData = new State with EntryPoint {
      val end = RegInit(False)
      onEntry {
        txCnt.clear()
      }
      whenIsActive {
        // Send data
        io.tx.data.fragment := fifo.io.pop.fragment
        io.tx.data.last := fifo.io.pop.last
        io.tx.data.valid := fifo.io.pop.valid
        fifo.io.pop.ready := io.tx.data.ready
        when(txCnt === rxCnt) {
          exit()
        }
      }
    }
  }
}
