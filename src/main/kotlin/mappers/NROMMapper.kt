package mappers

import ROMLoader

class NROMMapper(romLoader: ROMLoader) : Mapper(romLoader) {

    override fun loadROMValues() {
        super.loadROMValues()

        val shiftedPrg = IntArray(65536)
        System.arraycopy(prg, 0, shiftedPrg, 0x8000, prg.size)
        if (prgSize <= 16384) {
            System.arraycopy(prg, 0, shiftedPrg, 0xc000, prg.size)
        }
        prg = shiftedPrg
    }

    override fun cartridgeRead(address: Int): Int {
        if (address >= 0x8000) {
            return prg[address]
        } else if (address >= 0x6000 && hasPrgRam) {
            return prgRam[address and 0x1fff]
        }
        return address shr 8
    }

    override fun ppuRead(address: Int): Int {
        var address = address
        return if (address < 0x2000) {
            chr[address]
        } else {
            when (address and 0xc00) {
                0 -> nameTablePointer0[address and 0x3ff]
                0x400 -> nameTablePointer1[address and 0x3ff]
                0x800 -> nameTablePointer2[address and 0x3ff]
                0xc00 -> if (address >= 0x3f00) {
                    address = address and 0x1f
                    if (address >= 0x10 && address and 3 == 0) {
                        address -= 0x10
                    }
                    NESMain.ppu!!.colorPalette[address]
                } else {
                    nameTablePointer3[address and 0x3ff]
                }
                else -> if (address >= 0x3f00) {
                    address = address and 0x1f
                    if (address >= 0x10 && address and 3 == 0) {
                        address -= 0x10
                    }
                    NESMain.ppu!!.colorPalette[address]
                } else {
                    nameTablePointer3[address and 0x3ff]
                }
            }
        }
    }
}