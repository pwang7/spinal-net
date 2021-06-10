package dma

import spinal.core._
import spinal.lib._
import spinal.lib.bus.wishbone._
import spinal.lib.bus.amba4.axi._
import spinal.lib.bus.regif.AccessType._
import spinal.lib.memory.sdram.sdr.{MT48LC16M16A2, SdramInterface}

import sdram._

class DmaMem(
    addressWidth: Int = 32,
    bufSize: Int = 16,
    burstLen: Int = 8,
    dataWidth: Int = 32,
    idWidth: Int = 4,
    selWidth: Int = 4
) extends Component {
  val axiConfig = Axi4Config(
    addressWidth = addressWidth, //log2Up(memByteSize)
    dataWidth = dataWidth,
    idWidth = idWidth,
    useLock = false,
    useRegion = false,
    useCache = false,
    useProt = false,
    useQos = false
  )

  val wbConfig = WishboneConfig(
    addressWidth = addressWidth, //log2Up(memByteSize),
    dataWidth = dataWidth,
    selWidth = selWidth
  )

  val dmaConfig = DmaConfig(
    addressWidth = addressWidth,
    burstLen = burstLen,
    bufDepth = bufSize,
    dataWidth = dataWidth,
    xySizeMax = 256
  )

  val sdramConfig = SdramConfig(
    CAS = 2,
    addressWidth = addressWidth,
    burstLen = burstLen,
    busDataWidth = dataWidth,
    idWidth = idWidth
  )

  val sdramDevice = MT48LC16M16A2

  val io = new Bundle {
    val ctrl = slave(Ctrl())
    val sdram = master(SdramInterface(sdramDevice.layout))
    val wb = slave(
      Wishbone(wbConfig)
    )
  }

  val busif = BusInterface(io.wb, (0, 100 Byte))
  val SAR_REG = busif.newReg(doc = "DMA src address")
  val DAR_REG = busif.newReg(doc = "DMA dst address")

  val srcAddr = SAR_REG.field(addressWidth bits, RW)
  val dstAddr = DAR_REG.field(addressWidth bits, RW)

  val dmaArea = new Area {
    val dma = new Dma(dmaConfig)
    dma.io.ctrl <> io.ctrl

    dma.io.param.sar := srcAddr.asUInt
    dma.io.param.dar := dstAddr.asUInt
    dma.io.param.xsize := 2 * dmaConfig.busByteSize // At least twice bus byte size
    dma.io.param.ysize := 1
    dma.io.param.srcystep := 0
    dma.io.param.dstystep := 0
    dma.io.param.llr := 0
    dma.io.param.bf := True
    dma.io.param.cf := True
  }

  val sdramArea = new Area {
    val sdramController = new SdramController(
      sdramDevice.layout,
      sdramDevice.timingGrade7,
      SdramConfig()
    )
    io.sdram <> sdramController.io.sdram
  }

  sdramArea.sdramController.io.axi << dmaArea.dma.io.axi
}

object DmaMem {
  def main(args: Array[String]): Unit = {
    SpinalConfig(defaultClockDomainFrequency = FixedFrequency(100 MHz))
      .generateVerilog(
        new DmaMem()
      ) printPruned ()
  }
}
