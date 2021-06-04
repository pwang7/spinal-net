package sdram

import spinal.core._
import spinal.lib._
import spinal.lib.memory.sdram._
import spinal.lib.memory.sdram.sdr.{MT48LC16M16A2, SdramCtrl}


class SdramCtrlTester extends CocotbBase {
  override def getName: String = "SdramCtrlTester"
  override def pythonTestLocation: String = "SpinalNet/test/src/python/sdram"
  override def createToplevel: Component = {
    val device = MT48LC16M16A2
    SdramCtrl(device.layout,device.timingGrade7.copy(tPOW = 5 us),CAS = 2,UInt(8 bits)).setDefinitionName(getName)
  }
  override def backendConfig(config: SpinalConfig): SpinalConfig = config.copy(defaultClockDomainFrequency = FixedFrequency(133 MHz))
  override def noVhdl = true

  test("sdram test") {
    doTest("./SpinalNet/test/src/python/sdram", Language.VERILOG)
    println("SUCCESS")
  }
}