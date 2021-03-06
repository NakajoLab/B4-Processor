package b4processor.utils

import chisel3._

import scala.io.Source

object InstructionUtil {
  def fromFile32bit(filename: String): Seq[UInt] = {
    val file = Source.fromFile(filename)
    val output = fromStringSeq32bit(file.getLines().filter(_.nonEmpty).toSeq)
    file.close()
    output
  }

  def fromStringSeq32bit(seq: Seq[String]): Seq[UInt] = {
    var bytes = Seq[String]()
    for (s <- seq) {
      var offset = 0
      if (s.startsWith("x"))
        offset = 1
      for (i <- (0 until 4).reverse)
        bytes = bytes :+ s.slice(i * 2 + offset, i * 2 + 2 + offset)
    }
    fromStringSeq8bit(bytes)
  }

  def fromFile8bit(filename: String): Seq[UInt] = {
    val file = Source.fromFile(filename)
    val output = fromStringSeq8bit(file.getLines().filter(_.nonEmpty).toSeq)
    file.close()
    output
  }

  def fromStringSeq8bit(seq: Seq[String]): Seq[UInt] = {
    var output = Seq[String]()
    for (s <- seq) {
      var offset = 0
      if (s.startsWith("x"))
        offset = 1
      output = output :+ s.slice(offset, offset + 2)
    }
    output.map(n => s"x$n".U(8.W))
  }
}
