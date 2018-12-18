package mappers

import ROMLoader
import constants.DataConstants

class MMC1Mapper(romLoader: ROMLoader) : Mapper(romLoader) {

    private var mmc1shift = 0
    private var mmc1latch = 0
    private var mmc1ctrl = 0xc
    private var mmc1chr0 = 0
    private var mmc1chr1 = 0
    private var mmc1prg = 0
    private var soromlatch = false

    override fun loadROMValues() {
        super.loadROMValues()
        for (i in 0..31) {
            prgMap[i] = 1024 * i and prgSize - 1
        }
        for (i in 0..7) {
            chrMap[i] = 1024 * i and chrSize - 1
        }
        setbanks()
    }

    override fun cartridgeWrite(address: Int, data: Int) {
        if (address < 0x8000 || address > 0xffff) {
            super.cartridgeWrite(address, data)
            return
        }
        if (data and DataConstants.BIT7 != 0) {
            mmc1shift = 0
            mmc1latch = 0
            mmc1ctrl = mmc1ctrl or 0xc
            setbanks()
            return
        }

        mmc1shift = (mmc1shift shr 1) + (data and 1) * 16
        ++mmc1latch
        if (mmc1latch < 5) {
            return
        } else {
            if (address in 0x8000..0x9fff) {
                mmc1ctrl = mmc1shift and 0x1f
                val mirtype: ScreenMirrorType = when (mmc1ctrl and 3) {
                    2 -> ScreenMirrorType.VERTICAL_MIRROR
                    else -> ScreenMirrorType.HORIZONTAL_MIRROR
                }
                setMapperMirrorMode(mirtype)
            } else if (address in 0xa000..0xbfff) {
                mmc1chr0 = mmc1shift and 0x1f
                if (prgSize > 262144) {
                    mmc1chr0 = mmc1chr0 and 0xf
                    soromlatch = mmc1shift and DataConstants.BIT4 != 0
                }
            } else if (address in 0xc000..0xdfff) {
                mmc1chr1 = mmc1shift and 0x1f
                if (prgSize > 262144) {
                    mmc1chr1 = mmc1chr1 and 0xf
                }
            } else if (address in 0xe000..0xffff) {
                mmc1prg = mmc1shift and 0xf
            }
            setbanks()
            mmc1latch = 0
            mmc1shift = 0
        }
    }

    private fun setbanks() {
        if (mmc1ctrl and DataConstants.BIT4 != 0) {
            for (i in 0..3) {
                chrMap[i] = 1024 * (i + 4 * mmc1chr0) % chrSize
            }
            for (i in 0..3) {
                chrMap[i + 4] = 1024 * (i + 4 * mmc1chr1) % chrSize
            }
        } else {
            for (i in 0..7) {
                chrMap[i] = 1024 * (i + 8 * (mmc1chr0 shr 1)) % chrSize
            }
        }
        when {
            mmc1ctrl and DataConstants.BIT3 == 0 -> for (i in 0..31) {
                prgMap[i] = (1024 * i + 32768 * (mmc1prg shr 1)) % prgSize
            }
            mmc1ctrl and DataConstants.BIT2 == 0 -> {
                for (i in 0..15) {
                    prgMap[i] = 1024 * i
                }
                for (i in 0..15) {
                    prgMap[i + 16] = (1024 * i + 16384 * mmc1prg) % prgSize
                }
            }
            else -> {
                for (i in 0..15) {
                    prgMap[i] = (1024 * i + 16384 * mmc1prg) % prgSize
                }
                for (i in 1..16) {
                    prgMap[32 - i] = prgSize - 1024 * i
                    if (prgMap[32 - i] > 262144) {
                        prgMap[32 - i] -= 262144
                    }
                }
            }
        }
        if (soromlatch && prgSize > 262144) {
            for (i in 0 until prgMap.size) {
                prgMap[i] += 262144
            }
        }
    }
}