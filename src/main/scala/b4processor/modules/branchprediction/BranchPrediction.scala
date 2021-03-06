package b4processor.modules.branchprediction

import b4processor.Parameters
import b4processor.connections.Fetch2BranchPrediction
import chisel3._

/** 分岐予測モジュール */
class BranchPrediction(implicit params: Parameters) extends Module {
  val io = IO(new Bundle {
    val fetch = new Fetch2BranchPrediction
  })
  io.fetch.prediction := false.B
}
