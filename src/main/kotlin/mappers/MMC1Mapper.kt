package mappers

import ROMLoader
import constants.DataConstants

class MMC1Mapper(romLoader: ROMLoader) : Mapper(romLoader) {

    private var mmc1Shift = 0
    private var mmc1Latch = 0
    private var mmc1Control = 0xc
    private var mmc1Chr0 = 0
    private var mmc1Chr1 = 0
    private var mmc1Prg = 0
    private var soROMLatch = false

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
            mmc1Shift = 0
            mmc1Latch = 0
            mmc1Control = mmc1Control or 0xc
            setbanks()
            return
        }

        mmc1Shift = (mmc1Shift shr 1) + (data and 1) * 16
        ++mmc1Latch
        if (mmc1Latch < 5) {
            return
        } else {
            if (address in 0x8000..0x9fff) {
                mmc1Control = mmc1Shift and 0x1f
                val mirtype: ScreenMirrorType = when (mmc1Control and 3) {
                    2 -> ScreenMirrorType.VERTICAL_MIRROR
                    else -> ScreenMirrorType.HORIZONTAL_MIRROR
                }
                setMapperMirrorMode(mirtype)
            } else if (address in 0xa000..0xbfff) {
                mmc1Chr0 = mmc1Shift and 0x1f
                if (prgSize > 262144) {
                    mmc1Chr0 = mmc1Chr0 and 0xf
                    soROMLatch = mmc1Shift and DataConstants.BIT4 != 0
                }
            } else if (address in 0xc000..0xdfff) {
                mmc1Chr1 = mmc1Shift and 0x1f
                if (prgSize > 262144) {
                    mmc1Chr1 = mmc1Chr1 and 0xf
                }
            } else if (address in 0xe000..0xffff) {
                mmc1Prg = mmc1Shift and 0xf
            }
            setbanks()
            mmc1Latch = 0
            mmc1Shift = 0
        }
    }

    private fun setbanks() {
        if (mmc1Control and DataConstants.BIT4 != 0) {
            for (i in 0..3) {
                chrMap[i] = 1024 * (i + 4 * mmc1Chr0) % chrSize
            }
            for (i in 0..3) {
                chrMap[i + 4] = 1024 * (i + 4 * mmc1Chr1) % chrSize
            }
        } else {
            for (i in 0..7) {
                chrMap[i] = 1024 * (i + 8 * (mmc1Chr0 shr 1)) % chrSize
            }
        }
        when {
            mmc1Control and DataConstants.BIT3 == 0 -> for (i in 0..31) {
                prgMap[i] = (1024 * i + 32768 * (mmc1Prg shr 1)) % prgSize
            }
            mmc1Control and DataConstants.BIT2 == 0 -> {
                for (i in 0..15) {
                    prgMap[i] = 1024 * i
                }
                for (i in 0..15) {
                    prgMap[i + 16] = (1024 * i + 16384 * mmc1Prg) % prgSize
                }
            }
            else -> {
                for (i in 0..15) {
                    prgMap[i] = (1024 * i + 16384 * mmc1Prg) % prgSize
                }
                for (i in 1..16) {
                    prgMap[32 - i] = prgSize - 1024 * i
                    if (prgMap[32 - i] > 262144) {
                        prgMap[32 - i] -= 262144
                    }
                }
            }
        }
        if (soROMLatch && prgSize > 262144) {
            for (i in 0 until prgMap.size) {
                prgMap[i] += 262144
            }
        }
    }
}