package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.common.OpcodeFormat
import b4processor.common.OpcodeFormat._
import chisel3._
import chisel3.util._

/**
 * ソースタグ2の値を選択する回路
 * 基本的にValueSelector1と同じだが、即値の入力を持っている。
 *
 * @param params パラメータ
 */
class ValueSelector2(params: Parameters) extends Module {
  val io = IO(new Bundle {
    val reorderBufferValue = Flipped(DecoupledIO(UInt(64.W)))
    val registerFileValue = Input(UInt(64.W))
    val aluBypassValue = Vec(params.numberOfALUs, Flipped(DecoupledIO(new Bundle {
      val destinationTag = UInt(params.tagWidth.W)
      val value = UInt(64.W)
    })))
    val immediateValue = Input(UInt(64.W))
    val opcodeFormat = Input(OpcodeFormat())
    val sourceTag = Flipped(DecoupledIO(UInt(params.tagWidth.W)))
    val value = DecoupledIO(UInt(64.W))
  })

  io.reorderBufferValue.ready := true.B
  for (i <- 0 until params.numberOfALUs) {
    io.aluBypassValue(i).ready := true.B
  }
  io.sourceTag.ready := true.B

  val aluMatchingTagExists = (0 until params.numberOfALUs)
    .map { i => io.aluBypassValue(i).valid && io.aluBypassValue(i).bits.destinationTag === io.sourceTag.bits }
    .fold(false.B) { (a, b) => a || b }

  io.value.valid := MuxCase(false.B,
    Seq(
      // I形式である(即値優先)
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> true.B,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> true.B,
      (io.sourceTag.valid && aluMatchingTagExists) -> true.B,
      (!io.sourceTag.valid) -> true.B,
    ))
  io.value.bits := MuxCase(0.U,
    Seq(
      // I形式である(即値優先)
      (io.opcodeFormat === I || io.opcodeFormat === U || io.opcodeFormat === J) -> io.immediateValue,
      (io.sourceTag.valid && io.reorderBufferValue.valid) -> io.reorderBufferValue.bits,
      (io.sourceTag.valid && aluMatchingTagExists) -> MuxCase(0.U,
        (0 until params.numberOfALUs).map(i => (io.aluBypassValue(i).valid && io.aluBypassValue(i).bits.destinationTag === io.sourceTag.bits) -> io.aluBypassValue(i).bits.value)
      ),
      (!io.sourceTag.valid) -> io.registerFileValue,
    ))
}