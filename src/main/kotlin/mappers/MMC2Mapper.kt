package mappers

import ROMLoader
import constants.DataConstants

class MMC2Mapper(romLoader: ROMLoader) : Mapper(romLoader) {

    private var chrlatchL = true
    private var chrlatchR = false
    private var chrbankL1 = 0
    private var chrbankR1 = 0
    private var chrbankL2 = 0
    private var chrbankR2 = 0

    override fun loadROMValues() {
        super.loadROMValues()
        for (i in 1..32) {
            prgMap[32 - i] = prgSize - 1024 * i
        }

        for (i in 0..7) {
            chrMap[i] = 0
        }
    }

    override fun cartridgeWrite(address: Int, data: Int) {
        if (address < 0x8000 || address > 0xffff) {
            super.cartridgeWrite(address, data)
            return
        } else if (address in 0xa000..0xafff) {
            for (i in 0..7) {
                prgMap[i] = 1024 * (i + 8 * (data and 0xf)) and chrSize - 1
            }
        } else if (address in 0xb000..0xbfff) {
            chrbankL1 = data and 0x1f
            setupPPUBanks()
        } else if (address in 0xc000..0xcfff) {
            chrbankL2 = data and 0x1f
            setupPPUBanks()
        } else if (address in 0xd000..0xdfff) {
            chrbankR1 = data and 0x1f
            setupPPUBanks()
        } else if (address in 0xe000..0xefff) {
            chrbankR2 = data and 0x1f
            setupPPUBanks()
        } else if (address in 0xf000..0xffff) {
            setMapperMirrorMode(if (data and DataConstants.BIT0 != 0) ScreenMirrorType.HORIZONTAL_MIRROR else ScreenMirrorType.VERTICAL_MIRROR)
        }
    }

    override fun ppuRead(address: Int): Int {
        val retval = super.ppuRead(address)
        if (address and DataConstants.BIT3 != 0) {
            when (address shr 4) {
                0xfd -> if (address and 3 == 0) {
                    chrlatchL = false
                    setupPPUBanks()
                }
                0xfe -> {
                    if (address and 3 == 0) {
                        chrlatchL = true
                        setupPPUBanks()
                    }
                    chrlatchR = false
                    setupPPUBanks()
                }
                0x1fd -> {
                    chrlatchR = false
                    setupPPUBanks()
                }
                0x1fe -> {
                    chrlatchR = true
                    setupPPUBanks()
                }
                else -> {
                }
            }
        }
        return retval
    }

    private fun setupPPUBanks() {
        if (chrlatchL) {
            setPPUBank(4, 0, chrbankL2)
        } else {
            setPPUBank(4, 0, chrbankL1)
        }
        if (chrlatchR) {
            setPPUBank(4, 4, chrbankR2)
        } else {
            setPPUBank(4, 4, chrbankR1)
        }
    }

    private fun setPPUBank(banksize: Int, bankpos: Int, banknum: Int) {
        for (i in 0 until banksize) {
            chrMap[i + bankpos] = 1024 * (banksize * banknum + i) % chrSize
        }
    }
}