package b4processor.modules.decoder

import b4processor.Parameters
import b4processor.connections.FetchBuffer2Decoder
import chisel3._

/** 複数のデコーダをつなげるモジュール
  *
  * @param params
  *   パラメータ
  */
class MultipleDecoder(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val instructions = Vec(params.runParallel, new FetchBuffer2Decoder)
  })

  val decoders = (0 until params.runParallel).map(i => Module(new Decoder(i)))

  for (i <- 1 until params.runParallel) {
    decoders(i).io.decodersBefore <> decoders(i - 1).io.decodersAfter
  }

  for (i <- 0 until params.runParallel) {
    decoders(i).io.instructionFetch <> io.instructions(i)
  }
}
