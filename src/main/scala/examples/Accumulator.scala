package examples

import chisel3._

class Accumulator(width: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(width.W))
    val out = Output(UInt(width.W))
    val en  = Input(Bool())
    val rst = Input(Bool())
  })

  val sum = RegInit(0.U(width.W))

  when (io.rst) {
    sum := 0.U
  } .elsewhen (io.en) {
    sum := sum + io.in
  }

  io.out := sum
}
