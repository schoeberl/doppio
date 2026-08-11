package doppio

import java.util.function.Consumer

package object scaladsl {
  implicit final class SimScalaOps(private val sim: Sim) extends AnyVal {
    def scala: ScalaSim = new ScalaSim(sim)

    def runScala(body: ScalaSim => Unit): Unit = {
      sim.run(new Consumer[Sim] {
        override def accept(current: Sim): Unit = body(new ScalaSim(current))
      })
    }
  }
}
