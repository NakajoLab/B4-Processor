package b4processor.modules.registerfile

import b4processor.Parameters
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RegisterFileTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "register file"

  implicit val detfaultParams =
    Parameters(runParallel = 1, maxRegisterFileCommitCount = 1)

  it should "save a value" in {
    test(new RegisterFile) { c =>
      c.io.reorderBuffer(0).valid.poke(true)
      c.io.reorderBuffer(0).bits.value.poke(123)
      c.io.reorderBuffer(0).bits.destinationRegister.poke(5)

      c.clock.step()

      c.io.decoders(0).sourceRegister1.poke(5)
      c.io.decoders(0).value1.expect(123)
    }
  }

  it should "have no op on 0" in {
    test(new RegisterFile) { c =>
      c.io.reorderBuffer(0).valid.poke(true)
      c.io.reorderBuffer(0).bits.value.poke(123)
      c.io.reorderBuffer(0).bits.destinationRegister.poke(0)

      c.clock.step()

      c.io.decoders(0).sourceRegister1.poke(0)
      c.io.decoders(0).value1.expect(0)
    }
  }

  it should "resolve multiple inputs and outputs" in {
    test(
      new RegisterFile()(detfaultParams.copy(maxRegisterFileCommitCount = 2))
    ) { c =>
      c.io.reorderBuffer(0).valid.poke(true)
      c.io.reorderBuffer(0).bits.value.poke(123)
      c.io.reorderBuffer(0).bits.destinationRegister.poke(1)
      c.io.reorderBuffer(1).valid.poke(true)
      c.io.reorderBuffer(1).bits.value.poke(456)
      c.io.reorderBuffer(1).bits.destinationRegister.poke(2)

      c.clock.step()

      c.io.decoders(0).sourceRegister1.poke(1)
      c.io.decoders(0).value1.expect(123)
      c.io.decoders(0).sourceRegister2.poke(2)
      c.io.decoders(0).value2.expect(456)
    }
  }

  it should "resolve multiple inputs overlapping" in {
    test(
      new RegisterFile()(detfaultParams.copy(maxRegisterFileCommitCount = 2))
    ) { c =>
      c.io.reorderBuffer(0).valid.poke(true)
      c.io.reorderBuffer(0).bits.value.poke(123)
      c.io.reorderBuffer(0).bits.destinationRegister.poke(1)
      c.io.reorderBuffer(1).valid.poke(true)
      c.io.reorderBuffer(1).bits.value.poke(456)
      c.io.reorderBuffer(1).bits.destinationRegister.poke(1)

      c.clock.step()

      c.io.decoders(0).sourceRegister1.poke(1)
      c.io.decoders(0).value1.expect(456)
    }
  }
}
