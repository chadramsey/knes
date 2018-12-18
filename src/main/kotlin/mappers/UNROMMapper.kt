package mappers

import ROMLoader

class UNROMMapper(romLoader: ROMLoader) : Mapper(romLoader) {

    private var bank = 0x0

    override fun loadROMValues() {
        super.loadROMValues()
        for (i in 0..15) {
            prgMap[i] = 1024 * i and prgSize - 1
        }
        for (i in 1..16) {
            prgMap[32 - i] = prgSize - 1024 * i
        }
        for (i in 0..7) {
            chrMap[i] = 1024 * i and chrSize - 1
        }
    }

    override fun cartridgeWrite(address: Int, data: Int) {
        if (address < 0x8000 || address > 0xffff) {
            super.cartridgeWrite(address, data)
            return
        }
        bank = data and 0xf
        for (i in 0..15) {
            prgMap[i] = 1024 * (i + 16 * bank) and prgSize - 1
        }
    }
}