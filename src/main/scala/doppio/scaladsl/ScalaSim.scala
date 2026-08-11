package doppio.scaladsl

import doppio.{InPort, OutPort, Sim}

final class ScalaSim private[scaladsl] (val javaSim: Sim) {
  def in(name: String): ScalaInPort = new ScalaInPort(javaSim.inPort(name))

  def out(name: String): ScalaOutPort = new ScalaOutPort(javaSim.outPort(name))

  def drive(): Unit = javaSim.drive()

  def drive(body: => Unit): Unit = {
    javaSim.drive()
    body
  }

  def step(): Unit = javaSim.step()

  def fork(body: => Unit): Unit = {
    javaSim.fork(new Runnable {
      override def run(): Unit = body
    })
  }

  def expect(condition: Boolean, message: String): Unit =
    javaSim.expect(condition, message)

  def time: Long = javaSim.time()
}

final class ScalaInPort private[scaladsl] (val javaPort: InPort) {
  def path: String = javaPort.path()

  def value: Long =
    throw new UnsupportedOperationException("input port values cannot be read")

  def value_=(value: Long): Unit = javaPort.set(value)

  def :=(value: Long): Unit = javaPort.set(value)

  override def toString: String = path
}

final class ScalaOutPort private[scaladsl] (val javaPort: OutPort) {
  def path: String = javaPort.path()

  def value: Long = javaPort.asLong()

  def high: Boolean = javaPort.isHigh()

  override def toString: String = s"$path=$value"
}
