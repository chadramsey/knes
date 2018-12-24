package mappers

import ROMLoader
import java.util.Arrays

open class Mapper(val romLoader: ROMLoader) {

    lateinit var prg: IntArray
    lateinit var chr: IntArray
    lateinit var prgMap: IntArray
    lateinit var chrMap: IntArray
    var prgRam: IntArray = IntArray(8192)
    lateinit var nameTablePointer0: IntArray
    lateinit var nameTablePointer1: IntArray
    lateinit var nameTablePointer2: IntArray
    lateinit var nameTablePointer3: IntArray
    private var ppuNameTable0 = IntArray(0x400)
    private var ppuNameTable1 = IntArray(0x400)
    private var ppuNameTable2 = IntArray(0x400)
    private var ppuNameTable3 = IntArray(0x400)
    var chrSize: Int = 0
    var prgSize: Int = 0
    private var hasChrRam: Boolean = false
    var hasPrgRam: Boolean = true

    companion object {
        fun locateMapperForROMAndSetLoader(romLoader: ROMLoader): Mapper {
            println("Loading ROM using Mapper ${romLoader.mapperType}")
            return when (romLoader.mapperType) {
                0 -> NROMMapper(romLoader)
                1 -> MMC1Mapper(romLoader)
                2 -> UNROMMapper(romLoader)
                9 -> MMC2Mapper(romLoader)
                else -> throw Exception("Mapper not supported")
            }
        }
    }

    enum class ScreenMirrorType(val mirrorTypeName: String) {
        HORIZONTAL_MIRROR("Horizontal Mirroring"),
        VERTICAL_MIRROR("Vertical Mirroring"),
        FOUR_SCREEN_MIRROR("Four Screen Mirroring")
    }

    enum class TelevisionType {
        NTSC,
        PAL
    }

    open fun loadROMValues() {
        prgSize = romLoader.prgSize
        chrSize = romLoader.chrSize
        prg = romLoader.loadData(romLoader.prgSize, romLoader.prgOffset)
        chr = romLoader.loadData(romLoader.chrSize, romLoader.chrOffset)
        if (chrSize == 0) {
            hasChrRam = true
            chrSize = 8192
            chr = IntArray(8192)
        }
        prgMap = IntArray(32)
        for (i in prgMap.indices) {
            prgMap[i] = (1024 * i) and (romLoader.prgSize - 1)
        }
        chrMap = IntArray(8)
        for (i in chrMap.indices) {
            chrMap[i] = (1024 * i) and (chrSize - 1)
        }
        Arrays.fill(ppuNameTable0, 0xa0)
        Arrays.fill(ppuNameTable1, 0xb0)
        Arrays.fill(ppuNameTable2, 0xc0)
        Arrays.fill(ppuNameTable3, 0xd0)
        setMapperMirrorMode(romLoader.screenMirrorType)
    }

    fun setMapperMirrorMode(mirrorType: ScreenMirrorType) {
        when (mirrorType) {
            ScreenMirrorType.HORIZONTAL_MIRROR -> {
                nameTablePointer0 = ppuNameTable0
                nameTablePointer1 = ppuNameTable0
                nameTablePointer2 = ppuNameTable1
                nameTablePointer3 = ppuNameTable1
            }
            ScreenMirrorType.VERTICAL_MIRROR -> {
                nameTablePointer0 = ppuNameTable0
                nameTablePointer1 = ppuNameTable1
                nameTablePointer2 = ppuNameTable0
                nameTablePointer3 = ppuNameTable1
            }
            ScreenMirrorType.FOUR_SCREEN_MIRROR -> {
                nameTablePointer0 = ppuNameTable0
                nameTablePointer1 = ppuNameTable1
                nameTablePointer2 = ppuNameTable2
                nameTablePointer3 = ppuNameTable3
            }
        }
    }

    open fun cartridgeWrite(address: Int, data: Int) {
        if (address in 0x6000..0x8000) {
            prgRam[address and 0x1FFF] = data
        }
    }

    open fun cartridgeRead(address: Int): Int {
        if (address >= 0x8000) {
            return prg[prgMap[address and 0x7FFF shr 10] + (address and 0x3FF)]
        } else if (address >= 0x6000 && hasPrgRam) {
            return prgRam[address and 0x1FFF]
        }
        return address shr 8
    }

    open fun ppuRead(address: Int): Int {
        var address = address
        return if (address < 0x2000) {
            chr[chrMap[address shr 10] + (address and 0x3FF)]
        } else {
            when (address and 0xc00) {
                0 -> nameTablePointer0[address and 0x3FF]
                0x400 -> nameTablePointer1[address and 0x3FF]
                0x800 -> nameTablePointer2[address and 0x3FF]
                0xc00 -> if (address >= 0x3F00) {
                    address = address and 0x1F
                    if (address >= 0x10 && address and 3 == 0) {
                        address -= 0x10
                    }
                    NESMain.ppu!!.colorPalette[address]
                } else {
                    nameTablePointer3[address and 0x3FF]
                }
                else -> if (address >= 0x3F00) {
                    address = address and 0x1F
                    if (address >= 0x10 && address and 3 == 0) {
                        address -= 0x10
                    }
                    NESMain.ppu!!.colorPalette[address]
                } else {
                    nameTablePointer3[address and 0x3FF]
                }
            }
        }
    }

    open fun ppuWrite(address: Int, data: Int) {
        var address = address
        address = address and 0x3FFF
        if (address < 0x2000) {
            if (hasChrRam) {
                chr[chrMap[address shr 10] + (address and 0x3FF)] = data
            }
        } else {
            when (address and 0xc00) {
                0x0 -> nameTablePointer0[address and 0x3FF] = data
                0x400 -> nameTablePointer1[address and 0x3FF] = data
                0x800 -> nameTablePointer2[address and 0x3FF] = data
                0xc00 -> if (address in 0x3F00..0x3FFF) {
                    address = address and 0x1F
                    if (address >= 0x10 && address and 3 == 0) {
                        address -= 0x10
                    }
                    NESMain.ppu!!.colorPalette[address] = data and 0x3F
                } else {
                    nameTablePointer3[address and 0x3FF] = data
                }
            }
        }
    }

    fun getROMMetadata(): String {
        return ("Filepath: ${romLoader.filename} \n" +
                "Mapper Type: ${romLoader.mapperType} \n" +
                "PRG Size: ${romLoader.prgSize / 1024} kB \n" +
                "CHR Size: ${romLoader.chrSize / 1024} kB \n" +
                "Screen Mirroring Type: ${romLoader.screenMirrorType.mirrorTypeName} \n" +
                "Battery Save: ${if (romLoader.doesContainSaveRAM) "Yes" else "No"}")
    }
}