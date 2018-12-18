/**
 * Implementation based on Andrew Hoffman's halfnes
 * https://github.com/andrew-hoffman/halfnes/blob/master/src/main/java/com/grapeshot/halfnes/PPU.java
 */

import gui.GUIMain
import mappers.Mapper
import constants.DataConstants
import java.util.Arrays

class PPU(private val mapper: Mapper) {

    private var isBackgroundOn: Boolean = false
    private var isSpriteOn: Boolean = false
    private var isSpriteClip: Boolean = false
    private var isDotCrawl: Boolean = true
    private var isGreyscale: Boolean = false
    private var isVBlank: Boolean = false
    private var isNMIControl: Boolean = false
    private var isSpriteZeroHit: Boolean = false
    private var isSpriteZero: Boolean = false
    private var isSpriteOverflow: Boolean = false
    private var isBackgroundPattern: Boolean = true
    private var isBackgroundClip: Boolean = false
    private var isSpriteSize: Boolean = false
    private var isSpritePattern: Boolean = false
    private var isEven: Boolean = true
    private var spriteBackgroundFlags = BooleanArray(8)

    // Internal PPU Registers
    private var registerV: Int = 0x00
    private var registerT: Int = 0x00
    private var registerX: Int = 0

    private var oamAddress: Int = 0
    private var oamStart: Int = 0
    private var readBuffer: Int = 0
    private var scanLineIndex: Int = 0
    private var scanLineCount: Int = 0
    private var vBlankLine: Int = 0
    private var frameCount: Int = 0
    private var cycleCount: Int = 0
    private var colorEmphasisValue: Int = 0
    private var locatedSpriteIndex: Int = 0
    private var backgroundAttributeShiftHigh: Int = 0
    private var backgroundAttributeShiftLow: Int = 0
    private var backgroundShiftHigh: Int = 0
    private var backgroundShiftLow: Int = 0
    private var nextAttribute: Int = 0
    private var backgroundLowBits: Int = 0
    private var backgroundHighBits: Int = 0
    private var divider: Int = 3
    private var dividerController: Int = 0
    private var tileAddress: Int = 0
    private var followingBackgroundAttribute: Int = 0
    private var openBus: Int = 0
    private var vramIncrement: Int = 1

    private var cpuDivider: IntArray = IntArray(5) { 3 }
    private var renderBitmap = IntArray(240 * 256)
    private var OAM: IntArray = IntArray(256) { 0xFF }
    private var secondaryOAM: IntArray = IntArray(32)
    private var spriteShiftRegisterHigh: IntArray = IntArray(8)
    private var spriteShiftRegisterLow: IntArray = IntArray(8)
    private var spriteXLatch: IntArray = IntArray(8)
    private var spritePalette: IntArray = IntArray(8)
    private var backgroundColors: IntArray = IntArray(256)
    var colorPalette: IntArray = intArrayOf(
            0x09, 0x01, 0x00, 0x01, 0x00, 0x02, 0x02, 0x0D,
            0x08, 0x10, 0x08, 0x24, 0x00, 0x00, 0x04, 0x2C,
            0x09, 0x01, 0x34, 0x03, 0x00, 0x04, 0x00, 0x14,
            0x08, 0x3A, 0x00, 0x02, 0x00, 0x20, 0x2C, 0x08)

    init {
        when (mapper.romLoader.televisionType) {
            Mapper.TelevisionType.NTSC -> {
                scanLineCount = 262
                vBlankLine = 241
                cpuDivider[0] = 3
            }
            Mapper.TelevisionType.PAL -> {
                scanLineCount = 312
                vBlankLine = 241
                cpuDivider[0] = 4
            }
        }
    }

    fun performFrameOperation() {
        for (scanLine in 0 until scanLineCount) {
            clockLine(scanLine)
        }
    }

    fun renderFrame(gui: GUIMain) {
        gui.renderFrame(renderBitmap)
    }

    fun read(registerNumber: Int): Int {
        when (registerNumber) {
            2 -> {
                isEven = true
                if (scanLineIndex == 241) {
                    if (cycleCount == 1) {
                        isVBlank = false
                    }
                }
                openBus = ((if (isVBlank) 0x80 else 0)
                        or (if (isSpriteZeroHit) 0x40 else 0)
                        or (if (isSpriteOverflow) 0x20 else 0)
                        or (openBus and 0x1f))
                isVBlank = false
            }
            4 -> {
                openBus = OAM[oamAddress]
                if (renderingOn() && scanLineIndex <= 240) {
                    return when {
                        cycleCount < 64 -> 0xFF
                        cycleCount <= 256 -> 0x00
                        cycleCount < 320 -> 0xFF
                        else -> secondaryOAM[0]
                    }
                }
            }
            7 -> {
                val temp: Int
                if (registerV and 0x3fff < 0x3f00) {
                    temp = readBuffer
                    readBuffer = mapper.ppuRead(registerV and 0x3fff)
                } else {
                    readBuffer = mapper.ppuRead((registerV and 0x3fff) - 0x1000)
                    temp = mapper.ppuRead(registerV)
                }
                if (!renderingOn() || scanLineIndex > 240 && scanLineIndex < scanLineCount - 1) {
                    registerV += vramIncrement
                } else {
                    incrementLoopyVHorizontal()
                    incrementLoopyVVertical()
                }
                openBus = temp
            }
            else -> return openBus
        }
        return openBus
    }

    fun write(registerNumber: Int, data: Int) {
        openBus = data
        when (registerNumber) {
            0
            -> {
                registerT = registerT and 0xc00.inv()
                registerT = registerT or (data and 3 shl 10)
                vramIncrement = if (data and DataConstants.BIT2 != 0) 32 else 1
                isSpritePattern = data and DataConstants.BIT3 != 0
                isBackgroundPattern = data and DataConstants.BIT4 != 0
                isSpriteSize = data and DataConstants.BIT5 != 0
                isNMIControl = data and DataConstants.BIT7 != 0
            }
            1
            -> {
                isGreyscale = data and DataConstants.BIT0 != 0
                isBackgroundClip = data and DataConstants.BIT1 == 0
                isSpriteClip = data and DataConstants.BIT2 == 0
                isBackgroundOn = data and DataConstants.BIT3 != 0
                isSpriteOn = data and DataConstants.BIT4 != 0
                colorEmphasisValue = data and 0xe0 shl 1
                if (scanLineCount == 312) {
                    val red = colorEmphasisValue shr 6 and 1
                    val green = colorEmphasisValue shr 7 and 1
                    colorEmphasisValue = colorEmphasisValue and 0xf3f
                    colorEmphasisValue = colorEmphasisValue or (red shl 7 or (green shl 6))
                }
            }
            3 ->
                oamAddress = data and 0xff
            4 -> {
                if (oamAddress and 3 == 2) {
                    OAM[oamAddress++] = data and 0xE3
                } else {
                    OAM[oamAddress++] = data
                }
                oamAddress = oamAddress and 0xff
            }
            5 -> if (isEven) {
                registerT = registerT and 0x1f.inv()
                registerX = data and 7
                registerT = registerT or (data shr 3)

                isEven = false
            } else {
                registerT = registerT and 0x7000.inv()
                registerT = registerT or (data and 7 shl 12)
                registerT = registerT and 0x3e0.inv()
                registerT = registerT or (data and 0xf8 shl 2)
                isEven = true
            }
            6 ->
                if (isEven) {
                    registerT = registerT and 0xc0ff
                    registerT = registerT or (data and 0x3f shl 8)
                    registerT = registerT and 0x3fff
                    isEven = false
                } else {
                    registerT = registerT and 0xfff00
                    registerT = registerT or data
                    registerV = registerT
                    isEven = true
                }
            7 -> {
                mapper.ppuWrite(registerV and 0x3fff, data)
                if (!renderingOn() || scanLineIndex > 240 && scanLineIndex < scanLineCount - 1) {
                    registerV += vramIncrement
                } else if (registerV and 0x7000 == 0x7000) {
                    val YScroll = registerV and 0x3E0
                    registerV = registerV and 0xFFF
                    when (YScroll) {
                        0x3A0 -> registerV = registerV xor 0xBA0
                        0x3E0 -> registerV = registerV xor 0x3E0
                        else -> registerV += 0x20
                    }
                } else {
                    registerV += 0x1000
                }
            }
            else -> {
            }
        }
    }

    private fun renderingOn(): Boolean {
        return isBackgroundOn || isSpriteOn
    }

    private fun clockLine(scanLineIndex: Int) {
        val skip = if (scanLineCount == 262 &&
                scanLineIndex == 0 &&
                renderingOn() &&
                frameCount and DataConstants.BIT1 == 0)
            1
        else
            0
        cycleCount = skip
        while (cycleCount < 341) {
            clock()
            ++cycleCount
        }
    }

    private fun clock() {
        if (cycleCount == 1) {
            if (scanLineIndex == 0) {
                isDotCrawl = renderingOn()
            }
            if (scanLineIndex < 240) {
                backgroundColors[scanLineIndex] = colorPalette[0]
            }
        }
        if (scanLineIndex < 240 || scanLineIndex == scanLineCount - 1) {
            if (renderingOn() && (cycleCount in 1..256 || cycleCount in 321..336)) {
                bgFetch()
            } else if (cycleCount == 257 && renderingOn()) {
                registerV = registerV and 0x41f.inv()
                registerV = registerV or (registerT and 0x41f)
            } else if (cycleCount in 258..341) {
                oamAddress = 0
            }
            if (cycleCount == 340 && renderingOn()) {
                fetchNTByte()
                fetchNTByte()
            }
            if (cycleCount == 65 && renderingOn()) {
                oamStart = oamAddress
            }
            if (cycleCount == 260 && renderingOn()) {
                evalSprites()
            }
            if (scanLineIndex == scanLineCount - 1) {
                if (cycleCount == 0) {
                    isVBlank = false
                    isSpriteZeroHit = false
                    isSpriteOverflow = false
                } else if (cycleCount in 280..304 && renderingOn()) {
                    registerV = registerT
                }
            }
        } else if (scanLineIndex == vBlankLine && cycleCount == 1) {
            isVBlank = true
        }
        if (scanLineIndex < 240 && cycleCount >= 1 && cycleCount <= 256) {
            val bufferOffset = (scanLineIndex shl 8) + (cycleCount - 1)
            when {
                isBackgroundOn -> {
                    val isBG = drawBGPixel(bufferOffset)
                    drawSprites(scanLineIndex, cycleCount - 1, isBG)
                }
                isSpriteOn -> {
                    val backgroundColor = if (registerV in 16129..16382) mapper.ppuRead(registerV) else colorPalette[0]
                    renderBitmap[bufferOffset] = backgroundColor
                    drawSprites(scanLineIndex, cycleCount - 1, true)
                }
                else -> {
                    val backgroundColor = if (registerV in 16129..16382) mapper.ppuRead(registerV) else colorPalette[0]
                    renderBitmap[bufferOffset] = backgroundColor
                }
            }
            if (isGreyscale) {
                renderBitmap[bufferOffset] = renderBitmap[bufferOffset] and 0x30
            }
            renderBitmap[bufferOffset] = renderBitmap[bufferOffset] and 0x3f or colorEmphasisValue
        }

        NESMain.cpu!!.isNmiEngaged = isVBlank && isNMIControl

        divider = (divider + 1) % cpuDivider[dividerController]
        if (divider == 0) {
            NESMain.cpu!!.performCpuCycle()
            dividerController = (dividerController + 1) % cpuDivider.size
        }
        if (cycleCount == 340) {
            scanLineIndex = (scanLineIndex + 1) % scanLineCount
            if (scanLineIndex == 0) {
                ++frameCount
            }
        }
    }

    private fun bgFetch() {
        backgroundAttributeShiftHigh = backgroundAttributeShiftHigh or (nextAttribute shr 1 and 1)
        backgroundAttributeShiftLow = backgroundAttributeShiftLow or (nextAttribute and 1)
        when (cycleCount - 1 and 7) {
            1 -> fetchNTByte()
            3 ->
                followingBackgroundAttribute = getAttribute((registerV and 0xc00) + 0x23c0,
                        registerV and 0x1f,
                        registerV and 0x3e0 shr 5)
            5 ->
                backgroundLowBits = mapper.ppuRead(tileAddress + (registerV and 0x7000 shr 12))
            7 -> {
                backgroundHighBits = mapper.ppuRead(tileAddress + 8 +
                        (registerV and 0x7000 shr 12))
                backgroundShiftLow = backgroundShiftLow or backgroundLowBits
                backgroundShiftHigh = backgroundShiftHigh or backgroundHighBits
                nextAttribute = followingBackgroundAttribute
                if (cycleCount != 256) {
                    incrementLoopyVHorizontal()
                } else {
                    incrementLoopyVVertical()
                }
            }
        }
        if (cycleCount in 321..336) {
            bgShiftClock()
        }
    }

    private fun incrementLoopyVVertical() {
        if (registerV and 0x7000 == 0x7000) {
            registerV = registerV and 0x7000.inv()
            var y = registerV and 0x03E0 shr 5
            if (y == 29) {
                y = 0
                registerV = registerV xor 0x0800
            } else {
                y = y + 1 and 31
            }
            registerV = registerV and 0x03E0.inv() or (y shl 5)
        } else {
            registerV += 0x1000
        }
    }

    private fun incrementLoopyVHorizontal() {
        if (registerV and 0x001F == 31) {
            registerV = registerV and 0x001F.inv()
            registerV = registerV xor 0x0400
        } else {
            registerV += 1
        }
    }

    private fun fetchNTByte() {
        tileAddress = mapper.ppuRead(
                (registerV and 0xc00 or 0x2000) + (registerV and 0x3ff)) * 16 + if (isBackgroundPattern) 0x1000 else 0
    }

    private fun drawBGPixel(bufferOffset: Int): Boolean {
        val isBG: Boolean
        if (isBackgroundClip && bufferOffset and 0xff < 8) {
            renderBitmap[bufferOffset] = colorPalette[0]
            isBG = true
        } else {
            val bgPix = (backgroundShiftHigh shr -registerX + 16 and 1 shl 1) + (backgroundShiftLow shr -registerX + 16 and 1)
            val bgPal = (backgroundAttributeShiftHigh shr -registerX + 8 and 1 shl 1) + (backgroundAttributeShiftLow shr -registerX + 8 and 1)
            isBG = bgPix == 0
            renderBitmap[bufferOffset] = if (isBG) colorPalette[0] else colorPalette[(bgPal shl 2) + bgPix]
        }
        bgShiftClock()
        return isBG
    }

    private fun bgShiftClock() {
        backgroundShiftHigh = backgroundShiftHigh shl 1
        backgroundShiftLow = backgroundShiftLow shl 1
        backgroundAttributeShiftHigh = backgroundAttributeShiftHigh shl 1
        backgroundAttributeShiftLow = backgroundAttributeShiftLow shl 1
    }

    private fun evalSprites() {
        isSpriteZero = false
        var ypos: Int
        var offset: Int
        locatedSpriteIndex = 0
        Arrays.fill(secondaryOAM, 0xff)
        var spritestart = oamStart
        while (spritestart < 255) {
            ypos = OAM[spritestart]
            offset = scanLineIndex - ypos
            if (ypos > scanLineIndex || offset > (if (isSpriteSize) 15 else 7)) {
                spritestart += 4
                continue
            }
            if (spritestart == 0) {
                isSpriteZero = true
            }
            if (locatedSpriteIndex >= 8) {
                isSpriteOverflow = true
                break
            } else {
                secondaryOAM[locatedSpriteIndex * 4] = OAM[spritestart]
                val oamextra = OAM[spritestart + 2]

                spriteBackgroundFlags[locatedSpriteIndex] = oamextra and DataConstants.BIT5 != 0
                spriteXLatch[locatedSpriteIndex] = OAM[spritestart + 3]
                spritePalette[locatedSpriteIndex] = ((oamextra and 3) + 4) * 4
                if (oamextra and DataConstants.BIT7 != 0) {
                    offset = (if (isSpriteSize) 15 else 7) - offset
                }
                if (offset > 7) {
                    offset += 8
                }
                val tilenum = OAM[spritestart + 1]
                spriteFetch(isSpriteSize, tilenum, offset, oamextra)
                ++locatedSpriteIndex
            }
            spritestart += 4
        }
        for (i in locatedSpriteIndex..7) {
            spriteShiftRegisterLow[locatedSpriteIndex] = 0
            spriteShiftRegisterHigh[locatedSpriteIndex] = 0
            spriteFetch(isSpriteSize, 0xff, 0, 0)
        }
    }

    private fun spriteFetch(isSpriteSize: Boolean, tileNumber: Int, offset: Int, oamExtra: Int) {
        var fetchedTile: Int = if (isSpriteSize) {
            (tileNumber and 1) * 0x1000 + (tileNumber and 0xfe) * 16
        } else {
            tileNumber * 16 + if (isSpritePattern) 0x1000 else 0
        }
        fetchedTile += offset
        val hflip = oamExtra and DataConstants.BIT6 != 0
        if (!hflip) {
            spriteShiftRegisterLow[locatedSpriteIndex] = reverseByte(mapper.ppuRead(fetchedTile))
            spriteShiftRegisterHigh[locatedSpriteIndex] = reverseByte(mapper.ppuRead(fetchedTile + 8))
        } else {
            spriteShiftRegisterLow[locatedSpriteIndex] = mapper.ppuRead(fetchedTile)
            spriteShiftRegisterHigh[locatedSpriteIndex] = mapper.ppuRead(fetchedTile + 8)
        }
    }

    private fun reverseByte(byte: Int): Int {
        return (Integer.reverse(byte) shr 24) and 0xFF
    }

    private fun drawSprites(line: Int, x: Int, backgroundFlag: Boolean) {
        val startDraw = if (!isSpriteClip) 0 else 8
        var spritePixel = 0
        var index = 7
        for (y in locatedSpriteIndex - 1 downTo 0) {
            val off = x - spriteXLatch[y]
            if (off in 0..8) {
                if ((spriteShiftRegisterHigh[y] and 1) + (spriteShiftRegisterLow[y] and 1) != 0) {
                    index = y
                    spritePixel = 2 * (spriteShiftRegisterHigh[y] and 1) + (spriteShiftRegisterLow[y] and 1)
                }
                spriteShiftRegisterHigh[y] = spriteShiftRegisterHigh[y] shr 1
                spriteShiftRegisterLow[y] = spriteShiftRegisterLow[y] shr 1
            }
        }
        if (spritePixel == 0 || x < startDraw || !isSpriteOn) {
            return
        }

        if (isSpriteZero && index == 0 && !backgroundFlag &&
                x < 255) {
            isSpriteZeroHit = true
        }
        if (!spriteBackgroundFlags[index] || backgroundFlag) {
            renderBitmap[(line shl 8) + x] = colorPalette[spritePalette[index] + spritePixel]
        }
    }

    private fun getAttribute(nameTableStart: Int, tileX: Int, tileY: Int): Int {
        val base = mapper.ppuRead(nameTableStart + (tileX shr 2) + 8 * (tileY shr 2))
        return if (tileY and DataConstants.BIT1 != 0) {
            if (tileX and DataConstants.BIT1 != 0) {
                base shr 6 and 3
            } else {
                base shr 4 and 3
            }
        } else if (tileX and DataConstants.BIT1 != 0) {
            base shr 2 and 3
        } else {
            base and 3
        }
    }
}