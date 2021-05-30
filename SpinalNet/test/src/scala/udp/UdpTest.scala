package udp

import org.scalatest.funsuite.AnyFunSuite

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.com.eth._
import spinal.sim._

import testutils.CocotbRunner

//Run this scala test to generate and check that your RTL work correctly
class UdpTest extends AnyFunSuite {
  test("loopback test") {
    // SpinalConfig(targetDirectory = "rtl").dumpWave(0,"../../../../../../waves/UdpAppSelfTester.vcd").generateVerilog(
    //   UdpApp(udpPort = 37984)
    // )

    val compiled = SimConfig.withWave.compile(
      rtl = new UdpApp(udpPort = 37984)
    )
    assert(CocotbRunner("./SpinalNet/test/src/python/udp/selftested"), "Simulation faild")
    println("SUCCESS")
  }

  test("control test") {
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

    val compiled = SimConfig.withWave.compile(
      rtl = UdpCtrl(
        macParam,
        txCd = ClockDomain.external("txClk"),
        rxCd = ClockDomain.external("rxClk")
      )
    )
    assert(CocotbRunner("./SpinalNet/test/src/python/udp/udpctrl"), "Simulation faild")
    println("SUCCESS")
  }
}
