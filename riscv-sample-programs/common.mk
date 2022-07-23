TOOL_PREFIX := riscv64-unknown-elf-
export CC := $(TOOL_PREFIX)gcc
export OBJCOPY := $(TOOL_PREFIX)objcopy
export OBJDUMP := $(TOOL_PREFIX)objdump
export CFLAGS := -nodefaultlibs -nostdlib -march=rv64i -mabi=lp64 -no-pie -static

CFLAGS += -T ../linker.ld

.PHONY:clean
all: $(PROGRAMNAME).text.hex $(PROGRAMNAME).data.hex $(PROGRAMNAME).o $(PROGRAMNAME).dump

$(PROGRAMNAME).o: $(SOURCES)
	@echo $(CC) $(CFLAGS)
	$(CC) $(CFLAGS) $^ -o $@

$(PROGRAMNAME).text.binary: $(PROGRAMNAME).o
	$(OBJCOPY) -O binary -j .text $< $@

$(PROGRAMNAME).data.binary: $(PROGRAMNAME).o
	$(OBJCOPY) -O binary -j .data $< $@

$(PROGRAMNAME).text.hex: $(PROGRAMNAME).text.binary
	od -An -t x1 $< -w1 -v | tr -d " " > $@


$(PROGRAMNAME).data.hex: $(PROGRAMNAME).data.binary
	od -An -t x1 $< -w1 -v | tr -d " " > $@


$(PROGRAMNAME).dump: $(PROGRAMNAME).o
	$(OBJDUMP) -d $< -M no-aliases -M numeric > $@

clean:
	$(RM) *.hex *.binary *.o *.dump