import mappers.Mapper

class RAM(private val mapper: Mapper) {

    private var saveRAM: IntArray = IntArray(2048) { 0xFF }

    fun read(address: Int): Int {
        return when {
            address > 0x4018 -> mapper.cartridgeRead(address)
            address <= 0x1FFF -> saveRAM[address and 0x7FF]
            address <= 0x3FFF -> NESMain.ppu!!.read(address and 7)
            address in 0x4000..0x4018 -> { // Handles both APU and controller input (only controller for now)
                val registerValue: Int = address - 0x4000
                if (registerValue == 0x16) { // Controller address space
                    NESMain.controller!!.strobe()
                    return NESMain.controller!!.outByte or 0x40
                }
                return 0x40
            }
            else -> address shr 8
        }
    }

    fun write(address: Int, data: Int) {
        when {
            address > 0x4018 -> mapper.cartridgeWrite(address, data)
            address <= 0x1FFF -> saveRAM[address and 0x7FF] = data
            address <= 0x3FFF -> NESMain.ppu!!.write(address and 7, data)
            address in 0x4000..0x4018 -> { // Handles both APU and controller input (only controller for now)
                val registerValue: Int = address - 0x4000
                if (registerValue == 0x14) {
                    for (i in 0..255) {
                        this.write(0x2004, this.read((data shl 8) + i))
                    }
                }
                if (registerValue == 0x16) { // Controller
                    NESMain.controller!!.output()
                }
            }
        }
    }
}