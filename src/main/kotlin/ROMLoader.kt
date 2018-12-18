import mappers.Mapper
import constants.DataConstants
import util.FileUtil

class ROMLoader(val filename: String) {

    private val romBytes = FileUtil.readFromFile(filename)

    private lateinit var headerBytes: IntArray
    lateinit var televisionType: Mapper.TelevisionType
    lateinit var screenMirrorType: Mapper.ScreenMirrorType
    var doesContainSaveRAM: Boolean = false
    var prgSize: Int = 0
    var chrSize: Int = 0
    var mapperType: Int = 0
    var prgOffset: Int = 0
    var chrOffset: Int = 0

    init {
        parseROMHeader()
    }

    private fun parseROMHeader() {
        val headerLength = 16
        headerBytes = IntArray(headerLength)
        try {
            System.arraycopy(romBytes, 0, headerBytes, 0, headerLength)
        } catch (e: Exception) {
            println("Failed to copy ROM header: ${e.printStackTrace()}")
        }
        if (headerBytes[0] == 'N'.toInt() &&
        headerBytes[1] == 'E'.toInt() &&
        headerBytes[2] == 'S'.toInt() &&
        headerBytes[3] == 0x1A) {
            println("Valid NES ROM")
        }
        prgSize = Math.min(romBytes.size - 16, 16384 * headerBytes[4])
        chrSize = Math.min(romBytes.size - 16 - prgSize, 8192 * headerBytes[5])
        screenMirrorType = when {
                    headerBytes[6] and DataConstants.BIT3 != 0 -> Mapper.ScreenMirrorType.FOUR_SCREEN_MIRROR
                    headerBytes[6] and DataConstants.BIT0 != 0 -> Mapper.ScreenMirrorType.VERTICAL_MIRROR
                    else -> Mapper.ScreenMirrorType.HORIZONTAL_MIRROR
        }
        doesContainSaveRAM = headerBytes[6] and DataConstants.BIT1 != 0
        mapperType = headerBytes[6] shr 4
        televisionType = Mapper.TelevisionType.NTSC
        prgOffset = 0
        chrOffset = prgSize
    }

    fun loadData(size: Int, dataOffset: Int): IntArray {
        val binaryDataArray = IntArray(size)
        System.arraycopy(romBytes, dataOffset + headerBytes.size, binaryDataArray, 0, size)
        return binaryDataArray
    }
}