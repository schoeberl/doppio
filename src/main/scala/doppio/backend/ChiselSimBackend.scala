package doppio.backend

import chisel3.{Bool, Clock, UInt}
import chisel3.simulator.PeekPokeAPI
import scala.jdk.CollectionConverters._

final class ChiselSimBackend(
    clock: Clock,
    ports: java.util.Map[String, ChiselSimPort],
    period: Int
) extends SimulatorBackend {
  private val clockHandle = new PeekPokeAPI.TestableClock(clock)
  private var currentTime = 0L
  private var previousValues = Map.empty[String, java.math.BigInteger]

  def this(clock: Clock, ports: java.util.Map[String, ChiselSimPort]) =
    this(clock, ports, 10)

  override def time(): Long = currentTime

  override def read(portName: String): Long =
    port(portName).read()

  override def write(portName: String, value: Long): Unit =
    port(portName).write(value)

  override def previous(portName: String): Long =
    ChiselSimPort.asLong(previousBigInteger(portName))

  override def readBigInteger(portName: String): java.math.BigInteger =
    port(portName).readBigInteger()

  override def write(portName: String, value: java.math.BigInteger): Unit = {
    require(value.signum() >= 0, "port values must be unsigned")
    port(portName).write(value)
  }

  override def previousBigInteger(portName: String): java.math.BigInteger = {
    port(portName)
    previousValues.getOrElse(portName, java.math.BigInteger.ZERO)
  }

  override def step(): Unit = {
    previousValues = ports.asScala.view.mapValues(_.readBigInteger()).toMap
    clockHandle.step(1, period)
    currentTime += 1
  }

  private def port(portName: String): ChiselSimPort = {
    val handle = ports.get(portName)
    if (handle == null) {
      throw new IllegalArgumentException(s"unknown ChiselSim port: $portName")
    }
    handle
  }
}

sealed trait ChiselSimPort {
  def read(): Long

  def write(value: Long): Unit

  def readBigInteger(): java.math.BigInteger

  def write(value: java.math.BigInteger): Unit
}

object ChiselSimPort {
  private[backend] def asLong(value: java.math.BigInteger): Long = {
    if (value.bitLength() > 64) {
      throw new ArithmeticException("port value exceeds 64 bits; use readBigInteger")
    }
    value.longValue()
  }

  def bool(port: Bool): ChiselSimPort =
    new BoolPort(port)

  def uint(port: UInt): ChiselSimPort =
    new UIntPort(port)

  private final class BoolPort(port: Bool) extends ChiselSimPort {
    private val handle = new PeekPokeAPI.TestableBool(port)

    override def read(): Long =
      asLong(readBigInteger())

    override def write(value: Long): Unit =
      handle.poke(value != 0)

    override def readBigInteger(): java.math.BigInteger =
      handle.peekValue().asBigInt.bigInteger

    override def write(value: java.math.BigInteger): Unit = {
      require(value.signum() >= 0 && value.bitLength() <= 1, "value does not fit Bool port")
      handle.poke(value.signum() != 0)
    }
  }

  private final class UIntPort(port: UInt) extends ChiselSimPort {
    private val handle = new PeekPokeAPI.TestableUInt(port)

    override def read(): Long =
      asLong(readBigInteger())

    override def write(value: Long): Unit =
      write(new java.math.BigInteger(java.lang.Long.toUnsignedString(value)))

    override def readBigInteger(): java.math.BigInteger =
      handle.peekValue().asBigInt.bigInteger

    override def write(value: java.math.BigInteger): Unit = {
      require(value.signum() >= 0 && value.bitLength() <= port.getWidth, "value does not fit UInt port")
      handle.poke(BigInt(value))
    }
  }
}
