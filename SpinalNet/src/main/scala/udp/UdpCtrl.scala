package udp

import spinal.core._
import spinal.lib._
// import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config, Apb3SlaveFactory}
import spinal.lib.bus.amba4.axilite.{AxiLite4, AxiLite4SlaveFactory}
import spinal.lib.com.eth._
import spinal.lib.fsm.{EntryPoint, StateParallelFsm, State, StateMachine}

object UdpCtrlGeneric {
  val BUS_ADDR_WIDTH = 8
  val PHY_DATA_WIDTH = 8 // 8, 4, or 2
  val MAC_DATA_WIDTH = 32
  val MAC_BUF_SIZE = 16
  // def getApb3Config = Apb3Config(
  //   addressWidth  = BUS_ADDR_WIDTH,
  //   dataWidth     = MAC_DATA_WIDTH,
  //   selWidth      = 1,
  //   useSlaveError = true
  // )
}

case class UdpCtrl(p: MacEthParameter, txCd: ClockDomain, rxCd: ClockDomain)
    extends Component {
  val io = new Bundle {
    // val bus =  slave(Apb3(UdpCtrlGeneric.getApb3Config))
    val bus = slave(
      AxiLite4(
        addressWidth = UdpCtrlGeneric.BUS_ADDR_WIDTH,
        dataWidth = UdpCtrlGeneric.MAC_DATA_WIDTH
      )
    )
    // val rgmii = master(Rmii(RmiiParameter(
    //   tx = RmiiTxParameter(dataWidth = p.phy.txDataWidth),
    //   rx = RmiiRxParameter(dataWidth = p.phy.rxDataWidth,
    //                        withEr = true)
    // )))
    // val phy = master(PhyIo(p.phy))
    val interrupt = out Bool ()
  }

  val mac = new MacEth(p, txCd, rxCd)
  // io.phy <> mac.io.phy

  // Loopback PHY
  val phy = PhyIo(p.phy) addTag (crossClockDomain)
  phy <> mac.io.phy
  phy.rx.valid := phy.tx.valid
  phy.rx.payload.fragment.data := mac.io.phy.tx.payload.fragment.data
  phy.rx.payload.fragment.error := False
  phy.rx.payload.last := phy.tx.payload.last
  phy.tx.ready := phy.rx.ready
  phy.busy := False
  phy.colision := False

  // val busCtrl = Apb3SlaveFactory(io.bus)
  val busCtrl = new AxiLite4SlaveFactory(io.bus)
  val bridge = mac.io.ctrl.driveFrom(busCtrl)
  io.interrupt := bridge.interruptCtrl.pending
}

object UdpCtrl extends App {
  val phyPram = PhyParameter(
    txDataWidth = UdpCtrlGeneric.PHY_DATA_WIDTH,
    rxDataWidth = UdpCtrlGeneric.PHY_DATA_WIDTH
  )
  val macParam = MacEthParameter(
    phy = phyPram,
    rxDataWidth = UdpCtrlGeneric.MAC_DATA_WIDTH,
    txDataWidth = UdpCtrlGeneric.MAC_DATA_WIDTH,
    rxBufferByteSize = UdpCtrlGeneric.MAC_BUF_SIZE,
    txBufferByteSize = UdpCtrlGeneric.MAC_BUF_SIZE
  )
  SpinalVerilog(
    UdpCtrl(
      macParam,
      txCd = ClockDomain.external("txClk"),
      rxCd = ClockDomain.external("rxClk")
    )
  ).printPruned()
}
