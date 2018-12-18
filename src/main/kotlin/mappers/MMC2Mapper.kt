package mappers

import ROMLoader
import constants.DataConstants

class MMC2Mapper(romLoader: ROMLoader) : Mapper(romLoader) {

    private var chrLatchL = true
    private var chrLatchR = false
    private var chrBankL1 = 0
    private var chrBankR1 = 0
    private var chrBankL2 = 0
    private var chrBankR2 = 0

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
            chrBankL1 = data and 0x1f
            setupPPUBanks()
        } else if (address in 0xc000..0xcfff) {
            chrBankL2 = data and 0x1f
            setupPPUBanks()
        } else if (address in 0xd000..0xdfff) {
            chrBankR1 = data and 0x1f
            setupPPUBanks()
        } else if (address in 0xe000..0xefff) {
            chrBankR2 = data and 0x1f
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
                    chrLatchL = false
                    setupPPUBanks()
                }
                0xfe -> {
                    if (address and 3 == 0) {
                        chrLatchL = true
                        setupPPUBanks()
                    }
                    chrLatchR = false
                    setupPPUBanks()
                }
                0x1fd -> {
                    chrLatchR = false
                    setupPPUBanks()
                }
                0x1fe -> {
                    chrLatchR = true
                    setupPPUBanks()
                }
                else -> {
                }
            }
        }
        return retval
    }

    private fun setupPPUBanks() {
        if (chrLatchL) {
            setPPUBank(4, 0, chrBankL2)
        } else {
            setPPUBank(4, 0, chrBankL1)
        }
        if (chrLatchR) {
            setPPUBank(4, 4, chrBankR2)
        } else {
            setPPUBank(4, 4, chrBankR1)
        }
    }

    private fun setPPUBank(banksize: Int, bankpos: Int, banknum: Int) {
        for (i in 0 until banksize) {
            chrMap[i + bankpos] = 1024 * (banksize * banknum + i) % chrSize
        }
    }
}