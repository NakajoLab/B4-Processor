package b4processor.modules.lsq

import b4processor.Parameters
import b4processor.structures.memoryAccess.MemoryAccessInfo
import b4processor.structures.memoryAccess.MemoryAccessType._
import b4processor.structures.memoryAccess.MemoryAccessWidth._
import b4processor.utils.{DecodeEnqueue, LSQ2Memory, LSQfromALU}
import b4processor.utils.ExecutorValue
import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.util.{BitPat, DecoupledIO}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LoadStoreQueueWrapper(implicit params: Parameters)
    extends LoadStoreQueue {

  def initialize(): Unit = {
    this.setDecoder()
    this.setOutputs()
    this.setReorderBuffer()
  }

  def setOutputs(
    values: Seq[Option[LSQfromALU]] = Seq.fill(params.runParallel + 1)(None)
  ): Unit = {
    for (i <- 0 until params.runParallel + 1) {
      val output = this.io.outputCollector.outputs(i)
      val value = values(i)
      output.validAsResult.poke(value.exists(_.valid))
      output.validAsLoadStoreAddress.poke(value.exists(_.valid))
      output.value.poke(value.map(_.value).getOrElse(0))
      output.tag.poke(value.map(_.destinationtag).getOrElse(0))
      //      output.programCounter.poke(value.map(_.ProgramCounter).getOrElse(0))
    }
  }

  def setDecoder(
    values: Seq[Option[DecodeEnqueue]] = Seq.fill(params.runParallel)(None)
  ): Unit = {
    for (i <- 0 until params.runParallel) {
      val decoder = this.io.decoders(i)
      val value = values(i)
      decoder.valid.poke(value.isDefined)
      if (value.isDefined) {
        val v = value.get
        decoder.bits.addressAndLoadResultTag.poke(v.addressTag)
        decoder.bits.storeDataTag.poke(v.storeDataTag)
        decoder.bits.storeData.poke(v.storeData.getOrElse(0L))
        decoder.bits.storeDataValid.poke(v.storeData.isDefined)

        decoder.bits.accessInfo.poke(v.accessInfo)
      }

    }
  }

  def setReorderBuffer(
    DestinationTags: Seq[Int] = Seq.fill(params.maxRegisterFileCommitCount)(0),
    valids: Seq[Boolean] = Seq.fill(params.maxRegisterFileCommitCount)(false)
  ): Unit = {
    for (i <- 0 until params.maxRegisterFileCommitCount) {
      val tag = DestinationTags(i)
      val v = valids(i)
      io.reorderBuffer.destinationTag(i).poke(tag)
      io.reorderBuffer.valid(i).poke(v)
    }
  }

  def expectMemory(values: Seq[Option[LSQ2Memory]]): Unit = {
    for (i <- 0 until params.maxRegisterFileCommitCount) {
      this.io.memory(i).valid.expect(values(i).isDefined)
      if (values(i).isDefined) {
        this.io.memory(i).bits.address.expect(values(i).get.address)
        this.io.memory(i).bits.tag.expect(values(i).get.tag)
        this.io
          .memory(i)
          .bits
          .accessInfo
          .accessType
          .expect(values(i).get.accessInfo.accessType)
        this.io
          .memory(i)
          .bits
          .accessInfo
          .accessWidth
          .expect(values(i).get.accessInfo.accessWidth)
        this.io
          .memory(i)
          .bits
          .accessInfo
          .signed
          .expect(values(i).get.accessInfo.signed)
        this.io.memory(i).bits.data.expect(values(i).get.data)
      }
    }
  }
}

class LoadStoreQueueTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Load Store Queue"
  implicit val defaultParams = Parameters(
    tagWidth = 4,
    runParallel = 2,
    maxRegisterFileCommitCount = 2,
    debug = true
  )

  it should "Both Of Instructions Enqueue LSQ" in {
    test(new LoadStoreQueueWrapper) { c =>
      c.initialize()
      c.io.head.get.expect(0)
      c.io.tail.get.expect(0)
      c.io.decoders(0).ready.expect(true)
      c.io.decoders(1).ready.expect(true)
      c.setDecoder(values =
        Seq(
          Some(
            DecodeEnqueue(
              addressTag = 10,
              storeDataTag = 5,
              storeData = None,
              accessInfo = (new MemoryAccessInfo).Lit(
                _.accessType -> Load,
                _.accessWidth -> Byte,
                _.signed -> false.B
              )
            )
          ),
          Some(
            DecodeEnqueue(
              addressTag = 11,
              storeDataTag = 6,
              storeData = None,
              accessInfo = (new MemoryAccessInfo).Lit(
                _.accessType -> Load,
                _.accessWidth -> Byte,
                _.signed -> false.B
              )
            )
          )
        )
      )
      c.clock.step(1)

      c.setDecoder()
      c.io.head.get.expect(2)
      c.io.tail.get.expect(0)
      c.clock.step(2)
    }
  }

  it should "load check" in {
    // runParallel = 1, maxRegisterFileCommitCount = 1
    test(new LoadStoreQueueWrapper).withAnnotations(Seq(WriteVcdAnnotation)) {
      c =>
        c.initialize()
        // ?????????
        c.io.head.get.expect(0)
        c.io.tail.get.expect(0)
        c.io.decoders(0).ready.expect(true)
        c.io.memory(0).ready.poke(true)
        // c.io.memory(1).ready.poke(true) (if runParallel = 2)
        c.expectMemory(Seq(None, None))
        // c.expectMemory(Seq(None, None)) (if runParallel = 2)

        // ???????????????
        c.setDecoder(values =
          Seq(
            Some(
              DecodeEnqueue(
                addressTag = 10,
                storeDataTag = 5,
                storeData = Some(0),
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.clock.step(1)

        c.setDecoder()
        c.setOutputs(values =
          Seq(
            Some(LSQfromALU(valid = true, destinationtag = 10, value = 150)),
            None,
            None
          )
        )
        c.io.head.get.expect(1)
        c.io.tail.get.expect(0)
        c.expectMemory(Seq(None, None))
        c.clock.step(1)

        // ????????????
        c.expectMemory(values =
          Seq(
            Some(
              LSQ2Memory(
                address = 150,
                tag = 10,
                data = 0,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.io.tail.get.expect(0)
        c.io.head.get.expect(1)
        c.clock.step(1)

        c.io.head.get.expect(1)
        c.io.tail.get.expect(1)
        c.clock.step(3)

    }
  }
  // (if runParallel = 2)
  // c.setDecoder(values =
  //   Seq(Some(DecodeEnqueue(addressTag = ???, storeDataTag = ???, storeData = ???, opcode = ???, function3 = ???)),
  //     Some(DecodeEnqueue(addressTag = ???, storeDataTag = ???, storeData = ???, opcode = ???, function3 = ???))))
  // c.setExecutor(values =
  //   Seq(Some(LSQfromALU(valid = ???, destinationtag = ???, value = ???)),
  //     Some(LSQfromALU(valid = ???, destinationtag = ???, value = ???))))
  //  c.expectMemory(values =
  //    Seq(Some(LSQ2Memory(address = ???, tag = ???, data = ???, opcode = ???, function3 = ???)),
  //      Some(LSQ2Memory(address = ???, tag = ???, data = ???, opcode = ???, function3 = ???))))

  it should "store check" in {
    // runParallel = 1, maxRegisterFileCommitCount = 1
    test(new LoadStoreQueueWrapper).withAnnotations(Seq(WriteVcdAnnotation)) {
      c =>
        c.initialize()
        // ?????????
        c.io.head.get.expect(0)
        c.io.tail.get.expect(0)
        c.io.decoders(0).ready.expect(true)
        c.io.memory(0).ready.poke(true)
        c.io.memory(1).ready.poke(true)
        // c.io.memory(1).ready.poke(true) (if runParallel = 2)
        c.expectMemory(Seq(None, None))
        // c.expectMemory(Seq(None, None)) (if runParallel = 2)

        // ???????????????
        c.setDecoder(values =
          Seq(
            Some(
              DecodeEnqueue(
                addressTag = 10,
                storeDataTag = 5,
                storeData = None,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.clock.step(1)

        c.setDecoder()
        c.setOutputs(values =
          Seq(
            Some(LSQfromALU(valid = true, destinationtag = 5, value = 123)),
            None,
            None
          )
        )
        c.io.head.get.expect(1)
        c.io.tail.get.expect(0)
        c.clock.step(1)

        c.setOutputs(values =
          Seq(
            Some(LSQfromALU(valid = true, destinationtag = 10, value = 150)),
            None,
            None
          )
        )
        c.setReorderBuffer(
          valids = Seq(true, false),
          DestinationTags = Seq(10, 1)
        )
        c.io.head.get.expect(1)
        c.io.tail.get.expect(0)
        c.expectMemory(Seq(None, None))
        c.clock.step(1)

        // ????????????
        c.expectMemory(values =
          Seq(
            Some(
              LSQ2Memory(
                address = 150,
                tag = 10,
                data = 123,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.io.head.get.expect(1)
        c.io.tail.get.expect(0)
        c.clock.step(1)

        c.io.head.get.expect(1)
        c.io.tail.get.expect(1)
        c.clock.step(1)
    }
  }
  // (if runParallel = 2)
  // c.setDecoder(values =
  //   Seq(Some(DecodeEnqueue(addressTag = ???, storeDataTag = ???, storeData = ???, opcode = ???, function3 = ???)),
  //     Some(DecodeEnqueue(addressTag = ???, storeDataTag = ???, storeData = ???, opcode = ???, function3 = ???))))
  // c.setExecutor(values =
  //   Seq(Some(LSQfromALU(valid = ???, destinationtag = ???, value = ???)),
  //     Some(LSQfromALU(valid = ???, destinationtag = ???, value = ???))))
  //  c.expectMemory(values =
  //    Seq(Some(LSQ2Memory(address = ???, tag = ???, data = ???, opcode = ???, function3 = ???)),
  //      Some(LSQ2Memory(address = ???, tag = ???, data = ???, opcode = ???, function3 = ???))))

  it should "2 Parallel load check" in {
    test(new LoadStoreQueueWrapper).withAnnotations(Seq(WriteVcdAnnotation)) {
      c =>
        c.initialize()
        // ?????????
        c.io.head.get.expect(0)
        c.io.tail.get.expect(0)
        c.io.decoders(0).ready.expect(true)
        c.io.decoders(1).ready.expect(true)
        c.io.memory(0).ready.poke(true)
        c.io.memory(1).ready.poke(true)
        c.expectMemory(Seq(None, None))

        // ???????????????
        c.setDecoder(values =
          Seq(
            Some(
              DecodeEnqueue(
                addressTag = 10,
                storeDataTag = 5,
                storeData = None,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            Some(
              DecodeEnqueue(
                addressTag = 11,
                storeDataTag = 6,
                storeData = None,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            )
          )
        )
        c.clock.step(1)

        c.setDecoder()
        c.io.head.get.expect(2)
        c.io.tail.get.expect(0)
        c.setOutputs(values =
          Seq(
            Some(LSQfromALU(valid = true, destinationtag = 10, value = 150)),
            Some(LSQfromALU(valid = true, destinationtag = 11, value = 100)),
            None
          )
        )
        c.clock.step(1)

        // ????????????
        c.setOutputs()
        c.expectMemory(values =
          Seq(
            Some(
              LSQ2Memory(
                address = 150,
                tag = 10,
                data = 0,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            Some(
              LSQ2Memory(
                address = 100,
                tag = 11,
                data = 0,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            )
          )
        )
        c.clock.step(2)

    }
  }

  it should "2 Parallel store check" in {
    test(new LoadStoreQueueWrapper).withAnnotations(Seq(WriteVcdAnnotation)) {
      c =>
        c.initialize()
        // ?????????
        c.io.head.get.expect(0)
        c.io.tail.get.expect(0)
        c.io.decoders(0).ready.expect(true)
        c.io.decoders(1).ready.expect(true)
        c.io.memory(0).ready.poke(true)
        c.io.memory(1).ready.poke(true)
        c.expectMemory(Seq(None, None))

        // ???????????????
        c.setDecoder(values =
          Seq(
            Some(
              DecodeEnqueue(
                addressTag = 10,
                storeDataTag = 5,
                storeData = None,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            Some(
              DecodeEnqueue(
                addressTag = 11,
                storeDataTag = 6,
                storeData = Some(123),
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            )
          )
        )
        c.clock.step(1)

        c.setDecoder()
        c.io.head.get.expect(2)
        c.io.tail.get.expect(0)
        c.setOutputs(values =
          Seq(
            Some(LSQfromALU(valid = true, destinationtag = 10, value = 150)),
            Some(LSQfromALU(valid = true, destinationtag = 5, value = 100)),
            None
          )
        )
        c.clock.step(1)

        c.setOutputs(values =
          Seq(
            Some(LSQfromALU(valid = true, destinationtag = 8, value = 456)),
            Some(LSQfromALU(valid = true, destinationtag = 11, value = 789)),
            None
          )
        )
        c.setReorderBuffer(
          valids = Seq(false, true),
          DestinationTags = Seq(1, 10)
        )
        c.clock.step(1)

        // ????????????
        c.setOutputs()
        c.setReorderBuffer(
          valids = Seq(true, false),
          DestinationTags = Seq(11, 15)
        )
        c.expectMemory(values =
          Seq(
            Some(
              LSQ2Memory(
                address = 150,
                tag = 10,
                data = 100,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.clock.step(1)

        c.setReorderBuffer()
        c.expectMemory(values =
          Seq(
            Some(
              LSQ2Memory(
                address = 789,
                tag = 11,
                data = 123,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.clock.step(2)

    }
  }

  it should "2 Parallel check (1 clock wait)" in {
    test(new LoadStoreQueueWrapper).withAnnotations(Seq(WriteVcdAnnotation)) {
      c =>
        c.initialize()
        // ?????????
        c.io.head.get.expect(0)
        c.io.tail.get.expect(0)
        c.io.decoders(0).ready.expect(true)
        c.io.decoders(1).ready.expect(true)
        c.io.memory(0).ready.poke(true)
        c.io.memory(1).ready.poke(true)
        c.expectMemory(Seq(None, None))

        // ???????????????
        c.setDecoder(values =
          Seq(
            Some(
              DecodeEnqueue(
                addressTag = 10,
                storeDataTag = 5,
                storeData = None,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            Some(
              DecodeEnqueue(
                addressTag = 11,
                storeDataTag = 6,
                storeData = None,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            )
          )
        )
        // load instruction + store instruction
        c.clock.step(1)

        c.setDecoder(values =
          Seq(
            None,
            Some(
              DecodeEnqueue(
                addressTag = 13,
                storeDataTag = 0,
                storeData = None,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            )
          )
        )
        // invalid instruction + load instruction
        c.io.head.get.expect(2)
        c.io.tail.get.expect(0)
        c.setOutputs(values =
          Seq(
            Some(
              LSQfromALU(valid = true, destinationtag = 10, value = 150)
            ), // 1st load address
            Some(LSQfromALU(valid = true, destinationtag = 11, value = 100)),
            None
          )
        ) // store address
        c.clock.step(1)

        // ????????????
        c.setDecoder()
        c.io.head.get.expect(3)
        c.io.tail.get.expect(0)
        c.setOutputs(values =
          Seq(
            Some(
              LSQfromALU(valid = true, destinationtag = 12, value = 123)
            ), // invalid
            Some(LSQfromALU(valid = true, destinationtag = 13, value = 500)),
            None
          )
        ) // 2nd load address
        c.expectMemory(values =
          Seq(
            Some(
              LSQ2Memory(
                address = 150,
                tag = 10,
                data = 0,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.clock.step(1)

        c.setOutputs(values =
          Seq(
            Some(
              LSQfromALU(valid = true, destinationtag = 6, value = 456)
            ), // store data
            Some(
              LSQfromALU(valid = true, destinationtag = 14, value = 200)
            ), // invalid
            None
          )
        )
        // store????????????????????????load???????????????
        c.expectMemory(values =
          Seq(
            None,
            Some(
              LSQ2Memory(
                address = 500,
                tag = 13,
                data = 0,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Load,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            )
          )
        )
        c.clock.step(1)

        c.setReorderBuffer(
          valids = Seq(false, true),
          DestinationTags = Seq(1, 11)
        )
        c.setOutputs()
        c.expectMemory(Seq(None, None))
        c.clock.step(1)

        c.expectMemory(values =
          Seq(
            Some(
              LSQ2Memory(
                address = 100,
                tag = 11,
                data = 456,
                accessInfo = (new MemoryAccessInfo).Lit(
                  _.accessType -> Store,
                  _.accessWidth -> Byte,
                  _.signed -> false.B
                )
              )
            ),
            None
          )
        )
        c.clock.step(2)
    }
  }

  // ???????????????????????????????????????????????????????????????
  // ?????????????????????????????????????????????????????????Overlap=false????????????????????????????????????????????????
  it should "wait load for the overlapping store" in {
    // runParallel = 1, maxRegisterFileCommitCount = 1
    test(
      new LoadStoreQueueWrapper()(
        defaultParams.copy(runParallel = 1, maxRegisterFileCommitCount = 1)
      )
    ).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.initialize()
      // ?????????
      c.io.head.get.expect(0)
      c.io.tail.get.expect(0)
      c.io.decoders(0).ready.expect(true)
      c.io.memory(0).ready.poke(true)
      // c.io.memory(1).ready.poke(true) (if runParallel = 2)
      c.expectMemory(Seq(None))
      // c.expectMemory(Seq(None, None)) (if runParallel = 2)

      // ????????????????????????
      c.setDecoder(values =
        Seq(
          Some(
            DecodeEnqueue(
              addressTag = 10,
              storeDataTag = 5,
              storeData = None,
              accessInfo = (new MemoryAccessInfo).Lit(
                _.accessType -> Store,
                _.accessWidth -> Byte,
                _.signed -> false.B
              )
            )
          )
        )
      )

      c.clock.step()
      // ????????????????????????
      c.setDecoder(values =
        Seq(
          Some(
            DecodeEnqueue(
              addressTag = 11,
              storeDataTag = 0,
              storeData = None,
              accessInfo = (new MemoryAccessInfo).Lit(
                _.accessType -> Load,
                _.accessWidth -> Byte,
                _.signed -> false.B
              )
            )
          )
        )
      )
      c.io.head.get.expect(1)
      c.io.tail.get.expect(0)

      c.clock.step()
      c.initialize()
      c.io.head.get.expect(2)
      c.io.tail.get.expect(0)

      c.clock.step(2)
      // ????????????????????????????????????????????????
      c.setOutputs(values =
        Seq(
          Some(LSQfromALU(valid = true, destinationtag = 11, value = 150)),
          None
        )
      )
      c.expectMemory(Seq(None))

      c.clock.step()
      c.initialize()
      // ?????????????????????
      c.expectMemory(Seq(None))

      c.clock.step()
      // ???????????????????????????
      c.setOutputs(values =
        Seq(
          Some(LSQfromALU(valid = true, destinationtag = 10, value = 150)),
          Some(LSQfromALU(valid = true, destinationtag = 5, value = 300))
        )
      )
      c.setReorderBuffer(Seq(10), Seq(true))

      c.clock.step()
      c.initialize()
      // ?????????????????????
      c.expectMemory(values =
        Seq(
          Some(
            LSQ2Memory(
              address = 150,
              tag = 10,
              data = 300,
              accessInfo = (new MemoryAccessInfo).Lit(
                _.accessType -> Store,
                _.accessWidth -> Byte,
                _.signed -> false.B
              )
            )
          )
        )
      )

      c.clock.step()
      // ?????????????????????
      c.expectMemory(values =
        Seq(
          Some(
            LSQ2Memory(
              address = 150,
              tag = 11,
              data = 0,
              accessInfo = (new MemoryAccessInfo).Lit(
                _.accessType -> Load,
                _.accessWidth -> Byte,
                _.signed -> false.B
              )
            )
          )
        )
      )

      c.clock.step()

      c.io.tail.get.expect(2)
      c.io.head.get.expect(2)

      c.clock.step(3)
    }
  }

}
