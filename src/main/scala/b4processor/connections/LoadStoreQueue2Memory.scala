package b4processor.connections

import b4processor.Parameters
import chisel3._
import chisel3.util._

/**
 * LSQとメモリをつなぐ
 */
class LoadStoreQueue2Memory(implicit params: Parameters) extends ReadyValidIO(new Bundle {
  val address = SInt(64.W)
  val tag = UInt(params.tagWidth.W)
  val data = UInt(64.W)
  val opcode = Bool() // 1: LOAD  0: STORE
  val function3 = UInt(3.W)
})
