package b4processor.connections

import chisel3._

/** デコーダとレジスタファイルをつなぐ
  */
class Decoder2RegisterFile extends Bundle {
  val sourceRegister1 = Output(UInt(5.W))
  val sourceRegister2 = Output(UInt(5.W))
  val value1 = Input(UInt(64.W))
  val value2 = Input(UInt(64.W))
}
