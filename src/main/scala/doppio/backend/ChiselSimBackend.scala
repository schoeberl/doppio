package doppio.backend

import chisel3.{Bool, Clock, UInt}
import chisel3.simulator.PeekPokeAPI
import scala.jdk.CollectionConverters._

final class ChiselSimBackend(
    clock: Clock,
    signals: java.util.Map[String, ChiselSimSignal],
    period: Int
) extends SimulatorBackend {
  private val clockHandle = new PeekPokeAPI.TestableClock(clock)
  private var currentTime = 0L
  private var previousValues = Map.empty[String, Long]

  def this(clock: Clock, signals: java.util.Map[String, ChiselSimSignal]) =
    this(clock, signals, 10)

  override def time(): Long = currentTime

  override def read(signalPath: String): Long =
    signal(signalPath).read()

  override def write(signalPath: String, value: Long): Unit =
    signal(signalPath).write(value)

  override def previous(signalPath: String): Long =
    previousValues.getOrElse(signalPath, 0L)

  override def step(): Unit = {
    previousValues = signals.asScala.view.mapValues(_.read()).toMap
    clockHandle.step(1, period)
    currentTime += 1
  }

  private def signal(signalPath: String): ChiselSimSignal = {
    val handle = signals.get(signalPath)
    if (handle == null) {
      throw new IllegalArgumentException(s"unknown ChiselSim signal: $signalPath")
    }
    handle
  }
}

sealed trait ChiselSimSignal {
  def read(): Long

  def write(value: Long): Unit
}

object ChiselSimSignal {
  def bool(signal: Bool): ChiselSimSignal =
    new BoolSignal(signal)

  def uint(signal: UInt): ChiselSimSignal =
    new UIntSignal(signal)

  private final class BoolSignal(signal: Bool) extends ChiselSimSignal {
    private val handle = new PeekPokeAPI.TestableBool(signal)

    override def read(): Long =
      handle.peekValue().asBigInt.longValue

    override def write(value: Long): Unit =
      handle.poke(value != 0)
  }

  private final class UIntSignal(signal: UInt) extends ChiselSimSignal {
    private val handle = new PeekPokeAPI.TestableUInt(signal)

    override def read(): Long =
      handle.peekValue().asBigInt.longValue

    override def write(value: Long): Unit =
      handle.poke(BigInt(value))
  }
}
