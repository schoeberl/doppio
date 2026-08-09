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
  private var previousValues = Map.empty[String, Long]

  def this(clock: Clock, ports: java.util.Map[String, ChiselSimPort]) =
    this(clock, ports, 10)

  override def time(): Long = currentTime

  override def read(portName: String): Long =
    port(portName).read()

  override def write(portName: String, value: Long): Unit =
    port(portName).write(value)

  override def previous(portName: String): Long =
    previousValues.getOrElse(portName, 0L)

  override def step(): Unit = {
    previousValues = ports.asScala.view.mapValues(_.read()).toMap
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
}

object ChiselSimPort {
  def bool(port: Bool): ChiselSimPort =
    new BoolPort(port)

  def uint(port: UInt): ChiselSimPort =
    new UIntPort(port)

  private final class BoolPort(port: Bool) extends ChiselSimPort {
    private val handle = new PeekPokeAPI.TestableBool(port)

    override def read(): Long =
      handle.peekValue().asBigInt.longValue

    override def write(value: Long): Unit =
      handle.poke(value != 0)
  }

  private final class UIntPort(port: UInt) extends ChiselSimPort {
    private val handle = new PeekPokeAPI.TestableUInt(port)

    override def read(): Long =
      handle.peekValue().asBigInt.longValue

    override def write(value: Long): Unit =
      handle.poke(BigInt(value))
  }
}
