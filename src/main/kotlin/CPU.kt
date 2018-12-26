/**
 * Implementation based on Andrew Hoffman's halfnes
 * https://github.com/andrew-hoffman/halfnes/blob/master/src/main/java/com/grapeshot/halfnes/CPU.java
 */

import constants.DataConstants

class CPU(private val cpuRAM: RAM) {

    private var instruction: Int = 0
    private var cycles: Int = 0
    var clocks: Int = 0

    private var registerY: Int = 0
    private var registerX: Int = 0
    private var registerA: Int = 0

    private var stackPointer: Int = 0xFD

    private var programCounter: Int = 0
    private var interruptIndex: Int = 0
    private var pageBoundryIndex: Int = 0

    var isNmiEngaged: Boolean = false
    private var isNmiNext: Boolean = false
    private var isPreviousNmi: Boolean = false
    private var isInterruptDelay: Boolean = false
    private var isPreviousInterrupt: Boolean = false
    private var isIdle: Boolean = false
    private var isIdleLoopSkip: Boolean = true
    private var isNegative: Boolean = false
    private var isOverflow: Boolean = false
    private var isDecimalMode: Boolean = false
    private var isInterruptDisabled: Boolean = true
    private var isZero: Boolean = false
    private var isCarry: Boolean = false

    companion object {
        enum class Dummy {
            ONCARRY,
            ALWAYS
        }
    }

    init {
        for (i in 0..0x800) {
            cpuRAM.write(i, 0xFF)
        }

        cpuRAM.write(0x0008, 0xF7)
        cpuRAM.write(0x0009, 0xEF)
        cpuRAM.write(0x000A, 0xDF)
        cpuRAM.write(0x000F, 0xBF)

        for (i in 0x4000..0x400F) {
            cpuRAM.write(i, 0x00)
        }

        cpuRAM.write(0x4015, 0x00)
        cpuRAM.write(0x4017, 0x00)

        programCounter = cpuRAM.read(0xFFFD) * 256 +
                cpuRAM.read(0xFFFC) // Notates the reset vector
    }

    fun performCpuCycle() {
        cpuRAM.read(0x4000)
        ++clocks

        if (cycles-- > 0) {
            return
        }

        if (isNmiNext) {
            nmi()
            isNmiNext = false
        }

        if (isNmiEngaged && !isPreviousNmi) {
            isNmiNext = true
        }

        isPreviousNmi = isNmiEngaged

        if (interruptIndex > 0) {
            if (!isInterruptDisabled && !isInterruptDelay) {
                interrupt()
                cycles += 7
                return
            } else if (isInterruptDelay) {
                isInterruptDelay = false
                if (!isPreviousInterrupt) {
                    interrupt()
                    cycles += 7
                    return
                }
            }
        } else {
            isInterruptDelay = false
        }

        if (isIdle && isIdleLoopSkip) {
            cycles += 3
            return
        }

        pageBoundryIndex = 0
        instruction = cpuRAM.read(programCounter++)

        when (instruction) {
            // ADC
            0x69 -> {
                adc(imm())
                cycles += 2
            }
            0x65 -> {
                adc(zpg())
                cycles += 3
            }
            0x75 -> {
                adc(zpg(registerX))
                cycles += 4
            }
            0x6d -> {
                adc(abs())
                cycles += 4
            }
            0x7d -> {
                adc(abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x79 -> {
                adc(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x61 -> {
                adc(indX())
                cycles += 6
            }
            0x71 -> {
                adc(indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            // AND
            0x29 -> {
                and(imm())
                cycles += 2
            }
            0x25 -> {
                and(zpg())
                cycles += 3
            }
            0x35 -> {
                and(zpg(registerX))
                cycles += 4
            }
            0x2D -> {
                and(abs())
                cycles += 4
            }
            0x3D -> {
                and(abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x39 -> {
                and(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x21 -> {
                and(indX())
                cycles += 6
            }
            0x31 -> {
                and(indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            // ASL
            0x0A -> {
                aslA()
                cycles += 2
            }
            0x06 -> {
                asl(zpg())
                cycles += 5
            }
            0x16 -> {
                asl(zpg(registerX))
                cycles += 6
            }
            0x0e -> {
                asl(abs())
                cycles += 6
            }
            0x1e -> {
                asl(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // BIT
            0x24 -> {
                bit(zpg())
                cycles += 3
            }
            0x2c -> {
                bit(abs())
                cycles += 4
            }
            // Branches
            0x10 -> {
                branch(!isNegative)
                cycles += 2 + pageBoundryIndex
            }
            0x30 -> {
                branch(isNegative)
                cycles += 2 + pageBoundryIndex
            }
            0x50 -> {
                branch(!isOverflow)
                cycles += 2 + pageBoundryIndex
            }
            0x70 -> {
                branch(isOverflow)
                cycles += 2 + pageBoundryIndex
            }
            0x90 -> {
                branch(!isCarry)
                cycles += 2 + pageBoundryIndex
            }
            0xB0 -> {
                branch(isCarry)
                cycles += 2 + pageBoundryIndex
            }
            0xD0 -> {
                branch(!isZero)
                cycles += 2 + pageBoundryIndex
            }
            0xF0 -> {
                branch(isZero)
                cycles += 2 + pageBoundryIndex
            }
            // BRK
            0x00 -> {
                breakinterrupt()
                cycles += 7
            }
            // CMP
            0xc9 -> {
                cmp(registerA, imm())
                cycles += 2
            }
            0xc5 -> {
                cmp(registerA, zpg())
                cycles += 3
            }
            0xd5 -> {
                cmp(registerA, zpg(registerX))
                cycles += 4
            }
            0xcd -> {
                cmp(registerA, abs())
                cycles += 4
            }
            0xdd -> {
                cmp(registerA, abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0xd9 -> {
                cmp(registerA, abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0xc1 -> {
                cmp(registerA, indX())
                cycles += 6
            }
            0xd1 -> {
                cmp(registerA, indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            // CPX
            0xe0 -> {
                cmp(registerX, imm())
                cycles += 2
            }
            0xe4 -> {
                cmp(registerX, zpg())
                cycles += 3
            }
            0xec -> {
                cmp(registerX, abs())
                cycles += 4
            }
            // CPY
            0xc0 -> {
                cmp(registerY, imm())
                cycles += 2
            }
            0xc4 -> {
                cmp(registerY, zpg())
                cycles += 3
            }
            0xcc -> {
                cmp(registerY, abs())
                cycles += 4
            }
            // DEC
            0xc6 -> {
                dec(zpg())
                cycles += 5
            }
            0xd6 -> {
                dec(zpg(registerX))
                cycles += 6
            }
            0xce -> {
                dec(abs())
                cycles += 6
            }
            0xde -> {
                dec(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            0xd3 -> {
                dcp(registerA, indY(Dummy.ALWAYS))
                cycles += 8
            }
            0xc7 -> {
                dcp(registerA, zpg())
                cycles += 5
            }
            0xd7 -> {
                dcp(registerA, zpg(registerX))
                cycles += 6
            }
            0xdb -> {
                dcp(registerA, abs(registerY, Dummy.ALWAYS))
                cycles += 7
            }
            0xcf -> {
                dcp(registerA, abs())
                cycles += 6
            }
            0xdf -> {
                dcp(registerA, abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // EOR
            0x49 -> {
                eor(imm())
                cycles += 2
            }
            0x45 -> {
                eor(zpg())
                cycles += 3
            }
            0x55 -> {
                eor(zpg(registerX))
                cycles += 4
            }
            0x4d -> {
                eor(abs())
                cycles += 4
            }
            0x5d -> {
                eor(abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x59 -> {
                eor(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x41 -> {
                eor(indX())
                cycles += 6
            }
            0x51 -> {
                eor(indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            // Set carry flag
            0x18 -> {
                isCarry = false
                cycles += 2
            }
            0x38 -> {
                isCarry = true
                cycles += 2
            }
            0x58 -> {
                // CLI
                delayInterrupt()
                isInterruptDisabled = false
                cycles += 2
            }
            0x78 -> {
                // SEI
                delayInterrupt()
                isInterruptDisabled = true
                cycles += 2
            }
            0xb8 -> {
                isOverflow = false
                cycles += 2
            }
            0xd8 -> {
                isDecimalMode = false
                cycles += 2
            }
            0xf8 -> {
                isDecimalMode = true
                cycles += 2
            }
            // INC
            0xe6 -> {
                inc(zpg())
                cycles += 5
            }
            0xf6 -> {
                inc(zpg(registerX))
                cycles += 6
            }
            0xee -> {
                inc(abs())
                cycles += 6
            }
            0xfe -> {
                inc(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            0xf3 -> {
                isc(indY(Dummy.ALWAYS))
                cycles += 8
            }
            0xe7 -> {
                isc(zpg())
                cycles += 5
            }
            0xf7 -> {
                isc(zpg(registerX))
                cycles += 6
            }
            0xfb -> {
                isc(abs(registerY, Dummy.ALWAYS))
                cycles += 7
            }
            0xef -> {
                isc(abs())
                cycles += 6
            }
            0xff -> {
                isc(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // JMP
            0x4c -> {
                val tempe = programCounter
                programCounter = abs()
                if (programCounter == tempe - 1) {
                    isIdle = true
                }
                cycles += 3
            }
            0x6c -> {
                val tempf = programCounter
                programCounter = ind()
                if (programCounter == tempf - 1) {
                    isIdle = true
                }
                cycles += 5
            }
            // JSR
            0x20 -> {
                jsr(abs())
                cycles += 6
            }
            0xb3 -> {
                lax(indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            0xa7 -> {
                lax(zpg())
                cycles += 3
            }
            0xb7 -> {
                lax(zpg(registerY))
                cycles += 4
            }
            0xab -> {
                lax(imm())
                cycles += 2
            }
            0xaf -> {
                lax(abs())
                cycles += 4
            }
            0xbf -> {
                lax(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            // LDA
            0xa9 -> {
                lda(imm())
                cycles += 2
            }
            0xa5 -> {
                lda(zpg())
                cycles += 3
            }
            0xb5 -> {
                lda(zpg(registerX))
                cycles += 4
            }
            0xad -> {
                lda(abs())
                cycles += 4
            }
            0xbd -> {
                lda(abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0xb9 -> {
                lda(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0xa1 -> {
                lda(indX())
                cycles += 6
            }
            0xb1 -> {
                lda(indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            // LDX
            0xa2 -> {
                ldx(imm())
                cycles += 2
            }
            0xa6 -> {
                ldx(zpg())
                cycles += 3
            }
            0xb6 -> {
                ldx(zpg(registerY))
                cycles += 4
            }
            0xae -> {
                ldx(abs())
                cycles += 4
            }
            0xbe -> {
                ldx(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            // LDY
            0xa0 -> {
                ldy(imm())
                cycles += 2
            }
            0xa4 -> {
                ldy(zpg())
                cycles += 3
            }
            0xb4 -> {
                ldy(zpg(registerX))
                cycles += 4
            }
            0xac -> {
                ldy(abs())
                cycles += 4
            }
            0xbc -> {
                ldy(abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            // LSR
            0x4a -> {
                lsrA()
                cycles += 2
            }
            0x46 -> {
                lsr(zpg())
                cycles += 5
            }
            0x56 -> {
                lsr(zpg(registerX))
                cycles += 6
            }
            0x4e -> {
                lsr(abs())
                cycles += 6
            }
            0x5e -> {
                lsr(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // NOP
            0x1a, 0x3a, 0x5a, 0x7a, 0xda, 0xEA, 0xfa -> cycles += 2
            0x80, 0x82, 0xc2, 0xe2, 0x89 -> {
                imm()
                cycles += 2
            }
            0x04, 0x44, 0x64 -> {
                zpg()
                cycles += 3
            }
            0x14, 0x34, 0x54, 0x74, 0xd4, 0xf4 -> {
                zpg(registerX)
                cycles += 4
            }
            0x0C -> {
                abs()
                cycles += 4
            }
            0x1c, 0x3c, 0x5c, 0x7c, 0xdc, 0xfc -> {
                abs(registerX, Dummy.ONCARRY)
                cycles += 4 + pageBoundryIndex
            }
            // ORA
            0x09 -> {
                ora(imm())
                cycles += 2
            }
            0x05 -> {
                ora(zpg())
                cycles += 3
            }
            0x15 -> {
                ora(zpg(registerX))
                cycles += 4
            }
            0x0d -> {
                ora(abs())
                cycles += 4
            }
            0x1d -> {
                ora(abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x19 -> {
                ora(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x01 -> {
                ora(indX())
                cycles += 6
            }
            0x11 -> {
                ora(indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            // Registers
            0xAA -> {
                registerX = registerA
                cycles += 2
                setflags(registerA)
            }
            0x8a -> {
                registerA = registerX
                cycles += 2
                setflags(registerA)
            }
            0xca -> {
                registerX--
                registerX = registerX and 0xFF
                setflags(registerX)
                cycles += 2
            }
            0xe8 -> {
                registerX++
                registerX = registerX and 0xFF
                setflags(registerX)
                cycles += 2
            }
            0xa8 -> {
                registerY = registerA
                cycles += 2
                setflags(registerA)
            }
            0x98 -> {
                registerA = registerY
                cycles += 2
                setflags(registerA)
            }
            0x88 -> {
                registerY--
                registerY = registerY and 0xFF
                setflags(registerY)
                cycles += 2
            }
            0xc8 -> {
                registerY++
                registerY = registerY and 0xFF
                setflags(registerY)
                cycles += 2
            }
            0x33 -> {
                rla(indY(Dummy.ALWAYS))
                cycles += 8
            }
            0x27 -> {
                rla(zpg())
                cycles += 5
            }
            0x37 -> {
                rla(zpg(registerX))
                cycles += 6
            }
            0x3b -> {
                rla(abs(registerY, Dummy.ALWAYS))
                cycles += 7
            }
            0x2f -> {
                rla(abs())
                cycles += 6
            }
            0x3f -> {
                rla(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // ROL
            0x2a -> {
                rolA()
                cycles += 2
            }
            0x26 -> {
                rol(zpg())
                cycles += 5
            }
            0x36 -> {
                rol(zpg(registerX))
                cycles += 6
            }
            0x2e -> {
                rol(abs())
                cycles += 6
            }
            0x3e -> {
                rol(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // ROR
            0x6a -> {
                rorA()
                cycles += 2
            }
            0x66 -> {
                ror(zpg())
                cycles += 5
            }
            0x76 -> {
                ror(zpg(registerX))
                cycles += 6
            }
            0x6e -> {
                ror(abs())
                cycles += 6
            }
            0x7e -> {
                ror(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            0x73 -> {
                rra(indY(Dummy.ALWAYS))
                cycles += 8
            }
            0x67 -> {
                rra(zpg())
                cycles += 5
            }
            0x77 -> {
                rra(zpg(registerX))
                cycles += 6
            }
            0x7b -> {
                rra(abs(registerY, Dummy.ALWAYS))
                cycles += 7
            }
            0x6f -> {
                rra(abs())
                cycles += 6
            }
            0x7f -> {
                rra(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // RTI
            0x40 -> {
                rti()
                cycles += 6
            }
            // RTS
            0x60 -> {
                rts()
                cycles += 6
            }
            0x87 -> {
                sax(zpg())
                cycles += 3
            }
            0x97 -> {
                sax(zpg(registerY))
                cycles += 4
            }
            0x8f -> {
                sax(abs())
                cycles += 4
            }
            // SBC
            0xE1 -> {
                sbc(indX())
                cycles += 6
            }
            0xF1 -> {
                sbc(indY(Dummy.ONCARRY))
                cycles += 5 + pageBoundryIndex
            }
            0xE5 -> {
                sbc(zpg())
                cycles += 3
            }
            0xF5 -> {
                sbc(zpg(registerX))
                cycles += 4
            }
            0xE9 -> {
                sbc(imm())
                cycles += 2
            }
            0xF9 -> {
                sbc(abs(registerY, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0xeb -> {
                sbc(imm())
                cycles += 2
            }
            0xEd -> {
                sbc(abs())
                cycles += 4
            }
            0xFd -> {
                sbc(abs(registerX, Dummy.ONCARRY))
                cycles += 4 + pageBoundryIndex
            }
            0x07 -> {
                slo(zpg())
                cycles += 5
            }
            0x0f -> {
                slo(abs())
                cycles += 6
            }
            0x13 -> {
                slo(indY(Dummy.ALWAYS))
                cycles += 8
            }
            0x17 -> {
                slo(zpg(registerX))
                cycles += 6
            }
            0x1b -> {
                slo(abs(registerY, Dummy.ALWAYS))
                cycles += 7
            }
            0x1f -> {
                slo(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            0x53 -> {
                sre(indY(Dummy.ALWAYS))
                cycles += 8
            }
            0x47 -> {
                sre(zpg())
                cycles += 5
            }
            0x57 -> {
                sre(zpg(registerX))
                cycles += 6
            }
            0x5b -> {
                sre(abs(registerY, Dummy.ALWAYS))
                cycles += 7
            }
            0x4f -> {
                sre(abs())
                cycles += 6
            }
            0x5f -> {
                sre(abs(registerX, Dummy.ALWAYS))
                cycles += 7
            }
            // STA
            0x85 -> {
                sta(zpg())
                cycles += 3
            }
            0x95 -> {
                sta(zpg(registerX))
                cycles += 4
            }
            0x8d -> {
                sta(abs())
                cycles += 4
            }
            0x9d -> {
                sta(abs(registerX, Dummy.ALWAYS))
                cycles += 5
            }
            0x99 -> {
                sta(abs(registerY, Dummy.ALWAYS))
                cycles += 5
            }
            0x81 -> {
                sta(indX())
                cycles += 6
            }
            0x91 -> {
                sta(indY(Dummy.ALWAYS))
                cycles += 6
            }
            // Stack
            0x9A -> {
                stackPointer = registerX
                cycles += 2
            }
            0xBA -> {
                registerX = stackPointer
                cycles += 2
                setflags(registerX)
            }
            0x48 -> {
                cpuRAM.read(programCounter + 1)
                push(registerA)
                cycles += 3
            }
            0x68 -> {
                cpuRAM.read(programCounter + 1)
                registerA = pop()
                setflags(registerA)
                cycles += 4
            }
            0x08 -> {
                cpuRAM.read(programCounter + 1)
                push(fetchBytesFromFlags() or DataConstants.BIT4)
                cycles += 3
            }
            0x28 -> {
                // PLP
                delayInterrupt()
                cpuRAM.read(programCounter + 1)
                setFlagsFromByte(pop())
                cycles += 4
            }
            // STX
            0x86 -> {
                stx(zpg())
                cycles += 3
            }
            0x96 -> {
                stx(zpg(registerY))
                cycles += 4
            }
            0x8E -> {
                stx(abs())
                cycles += 4
            }
            // STY
            0x84 -> {
                sty(zpg())
                cycles += 3
            }
            0x94 -> {
                sty(zpg(registerX))
                cycles += 4
            }
            0x8c -> {
                sty(abs())
                cycles += 4
            }
            else -> {
                cycles += 2
            }
        }

        pageBoundryIndex = 0
        programCounter = programCounter and 0xFFFF
    }

    private fun nmi() {
        isIdle = false
        pushByteValueToRAM(programCounter shr 8)
        pushByteValueToRAM((programCounter) and 0xFF)
        pushByteValueToRAM(fetchBytesFromFlags() and DataConstants.BIT4.inv())
        programCounter = cpuRAM.read(0xFFFA) + (cpuRAM.read(0xFFFB) shl 8)
        cycles += 7
        isInterruptDisabled = false
    }

    private fun interrupt() {
        isIdle = false
        pushByteValueToRAM(programCounter shr 8)
        pushByteValueToRAM(programCounter and 0xFF)
        pushByteValueToRAM(fetchBytesFromFlags() and DataConstants.BIT4.inv())
        programCounter = cpuRAM.read(0xFFFE) + (cpuRAM.read(0xFFFF) shl 8)
        isInterruptDisabled = false
    }

    private fun pushByteValueToRAM(byte: Int) {
        cpuRAM.write((0x100 + (stackPointer and 0xFF)), byte)
        --stackPointer
        stackPointer = stackPointer and 0xFF
    }

    private fun fetchBytesFromFlags(): Int {
        return ((if (isNegative) DataConstants.BIT7 else 0) or
                (if (isOverflow) DataConstants.BIT6 else 0) or
                DataConstants.BIT5 or
                (if (isDecimalMode) DataConstants.BIT3 else 0) or
                (if (isInterruptDisabled) DataConstants.BIT2 else 0) or
                (if (isZero) DataConstants.BIT1 else 0) or
                (if (isCarry) DataConstants.BIT0 else 0))
    }

    private fun setFlagsFromByte(byte: Int) {
        isNegative = byte and DataConstants.BIT7 != 0
        isOverflow = byte and DataConstants.BIT6 != 0
        isDecimalMode = byte and DataConstants.BIT3 != 0
        isInterruptDisabled = byte and DataConstants.BIT2 != 0
        isZero = byte and DataConstants.BIT1 != 0
        isCarry = byte and DataConstants.BIT0 != 0
    }

    private fun rol(address: Int) {
        var data = cpuRAM.read(address)
        cpuRAM.write(address, data)
        data = data shl 1 or if (isCarry) 1 else 0
        isCarry = data and DataConstants.BIT8 != 0
        data = data and 0xFF
        setflags(data)
        cpuRAM.write(address, data)
    }

    private fun rolA() {
        registerA = registerA shl 1 or if (isCarry) 1 else 0
        isCarry = registerA and DataConstants.BIT8 != 0
        registerA = registerA and 0xFF
        setflags(registerA)
    }

    private fun ror(address: Int) {
        var data = cpuRAM.read(address)
        cpuRAM.write(address, data)
        val tmp = isCarry
        isCarry = data and DataConstants.BIT0 != 0
        data = data shr 1
        data = data and 0x7F
        data = data or if (tmp) 0x80 else 0
        setflags(data)
        cpuRAM.write(address, data)
    }

    private fun rorA() {
        val tmp = isCarry
        isCarry = registerA and DataConstants.BIT0 != 0
        registerA = registerA shr 1
        registerA = registerA and 0x7F
        registerA = registerA or if (tmp) 128 else 0
        setflags(registerA)
    }

    private fun breakinterrupt() {
        cpuRAM.read(programCounter++)
        push(programCounter shr 8)
        push(programCounter and 0xFF)
        push(fetchBytesFromFlags() or DataConstants.BIT4 or DataConstants.BIT5)
        programCounter = cpuRAM.read(0xFFFE) + (cpuRAM.read(0xFFFF) shl 8)
        isInterruptDisabled = true
    }

    private fun lsr(address: Int) {
        var data = cpuRAM.read(address)
        cpuRAM.write(address, data)
        isCarry = data and DataConstants.BIT0 != 0
        data = data shr 1
        data = data and 0x7F
        cpuRAM.write(address, data)
        setflags(data)
    }

    private fun lsrA() {
        isCarry = registerA and DataConstants.BIT0 != 0
        registerA = registerA shr 1
        registerA = registerA and 0x7F
        setflags(registerA)
    }

    private fun eor(address: Int) {
        registerA = registerA xor cpuRAM.read(address)
        registerA = registerA and 0xff
        setflags(registerA)
    }

    private fun ora(address: Int) {
        registerA = registerA or cpuRAM.read(address)
        registerA = registerA and 0xff
        setflags(registerA)
    }

    private fun bit(address: Int) {
        val data = cpuRAM.read(address)
        isZero = data and registerA == 0
        isNegative = data and DataConstants.BIT7 != 0
        isOverflow = data and DataConstants.BIT6 != 0
    }

    private fun jsr(address: Int) {
        programCounter--
        cpuRAM.read(programCounter)
        push(programCounter shr 8)
        push(programCounter and 0xFF)
        programCounter = address
    }

    private fun rts() {
        cpuRAM.read(programCounter++)
        programCounter = pop() and 0xff or (pop() shl 8)
        programCounter++
    }

    private fun rti() {
        cpuRAM.read(programCounter++)
        setFlagsFromByte(pop())
        programCounter = pop() and 0xff or (pop() shl 8)
    }

    private fun pop(): Int {
        ++stackPointer
        stackPointer = stackPointer and 0xff
        return cpuRAM.read(0x100 + stackPointer)
    }

    private fun push(byteToPush: Int) {
        cpuRAM.write(0x100 + (stackPointer and 0xff), byteToPush)
        --stackPointer
        stackPointer = stackPointer and 0xff
    }

    private fun branch(isTaken: Boolean) {
        if (isTaken) {
            val programCounterprev = programCounter + 1
            programCounter = rel()
            if (programCounterprev and 0xff00 != programCounter and 0xff00) {
                pageBoundryIndex = 2
            } else {
                cycles++
            }

            if (programCounterprev - 2 == programCounter) {
                isIdle = true
            }
        } else {
            rel()
        }
    }

    private fun inc(address: Int) {
        var tmp = cpuRAM.read(address)
        cpuRAM.write(address, tmp)
        ++tmp
        tmp = tmp and 0xff
        cpuRAM.write(address, tmp)
        setflags(tmp)
    }

    private fun dec(address: Int) {
        var tmp = cpuRAM.read(address)
        cpuRAM.write(address, tmp)
        --tmp
        tmp = tmp and 0xff
        cpuRAM.write(address, tmp)
        setflags(tmp)
    }

    private fun adc(address: Int) {
        val value = cpuRAM.read(address)
        var result: Int
        if (isDecimalMode) {
            var AL = (registerA and 0xF) + (value and 0xF) + if (isCarry) 1 else 0
            if (AL >= 0x0A) {
                AL = (AL + 0x6 and 0xF) + 0x10
            }
            result = (registerA and 0xF0) + (value and 0xF0) + AL
            if (result >= 0xA0) {
                result += 0x60
            }
        } else {
            result = value + registerA + if (isCarry) 1 else 0
        }
        isCarry = result shr 8 != 0
        isOverflow = registerA xor value and 0x80 == 0 && registerA xor result and 0x80 != 0
        registerA = result and 0xff
        setflags(registerA)
    }

    private fun sbc(address: Int) {
        val value = cpuRAM.read(address)
        var result: Int
        if (isDecimalMode) {
            var AL = (registerA and 0xF) - (value and 0xF) + (if (isCarry) 1 else 0) - 1
            if (AL < 0) {
                AL = (AL - 0x6 and 0xF) - 0x10
            }
            result = (registerA and 0xF0) + (value and 0xF0) + AL
            if (result < 0) {
                result -= 0x60
            }
        } else {
            result = registerA - value - if (isCarry) 0 else 1
        }
        isCarry = result shr 8 == 0
        isOverflow = registerA xor value and 0x80 != 0 && registerA xor result and 0x80 != 0
        registerA = result and 0xff
        setflags(registerA)
    }

    private fun and(address: Int) {
        registerA = registerA and cpuRAM.read(address)
        setflags(registerA)
    }

    private fun asl(address: Int) {
        var data = cpuRAM.read(address)
        cpuRAM.write(address, data)
        isCarry = data and DataConstants.BIT7 != 0
        data = data shl 1
        data = data and 0xff
        setflags(data)
        cpuRAM.write(address, data)
    }

    private fun aslA() {
        isCarry = registerA and DataConstants.BIT7 != 0
        registerA = registerA shl 1
        registerA = registerA and 0xff
        setflags(registerA)
    }

    private fun cmp(regval: Int, address: Int) {
        val result = regval - cpuRAM.read(address)
        when {
            result < 0 -> {
                isNegative = result and DataConstants.BIT7 != 0
                isCarry = false
                isZero = false
            }
            result == 0 -> {
                isNegative = false
                isCarry = true
                isZero = true
            }
            else -> {
                isNegative = result and DataConstants.BIT7 != 0
                isCarry = true
                isZero = false
            }
        }
    }

    private fun lda(address: Int) {
        registerA = cpuRAM.read(address)
        setflags(registerA)
    }

    private fun ldx(address: Int) {
        registerX = cpuRAM.read(address)
        setflags(registerX)
    }

    private fun ldy(address: Int) {
        registerY = cpuRAM.read(address)
        setflags(registerY)
    }

    private fun setflags(result: Int) {
        isZero = result == 0
        isNegative = result and DataConstants.BIT7 != 0
    }

    private fun sta(address: Int) {
        cpuRAM.write(address, registerA)
    }

    private fun stx(address: Int) {
        cpuRAM.write(address, registerX)
    }

    private fun sty(address: Int) {
        cpuRAM.write(address, registerY)
    }

    private fun dcp(regval: Int, address: Int) {
        dec(address)
        cmp(regval, address)
    }

    private fun lax(address: Int) {
        registerX = cpuRAM.read(address)
        registerA = registerX
        setflags(registerA)
    }

    private fun isc(address: Int) {
        inc(address)
        sbc(address)
    }

    private fun rla(address: Int) {
        rol(address)
        and(address)
    }

    private fun rra(address: Int) {
        ror(address)
        adc(address)
    }

    private fun sax(address: Int) {
        cpuRAM.write(address, registerA and registerX and 0xFF)
    }

    private fun slo(address: Int) {
        asl(address)
        ora(address)
    }

    private fun sre(address: Int) {
        lsr(address)
        eor(address)
    }

    private fun imm(): Int {
        return programCounter++
    }

    private fun zpg(): Int {
        return cpuRAM.read(programCounter++)
    }

    private fun zpg(reg: Int): Int {
        return cpuRAM.read(programCounter++) + reg and 0xff
    }

    private fun rel(): Int {
        return cpuRAM.read(programCounter++).toByte() + programCounter
    }

    private fun abs(): Int {
        return cpuRAM.read(programCounter++) + (cpuRAM.read(programCounter++) shl 8)
    }

    private fun abs(reg: Int, d: Dummy): Int {
        val address = cpuRAM.read(programCounter++) or (cpuRAM.read(programCounter++) shl 8)

        if (address shr 8 != address + reg shr 8) {
            pageBoundryIndex = 1
        }

        if (address and 0xFF00 != address + reg and 0xFF00 && d == Dummy.ONCARRY) {
            cpuRAM.read(address and 0xFF00 or (address + reg and 0xFF))
        }
        if (d == Dummy.ALWAYS) {
            cpuRAM.read(address and 0xFF00 or (address + reg and 0xFF))
        }

        return address + reg and 0xffff
    }

    private fun ind(): Int {
        val readloc = abs()
        return cpuRAM.read(readloc) + (cpuRAM.read(if (readloc and 0xff == 0xff)
            readloc - 0xff
        else
            readloc + 1) shl 8)
    }

    private fun indX(): Int {
        val arg = cpuRAM.read(programCounter++)
        return cpuRAM.read(arg + registerX and 0xff) + (cpuRAM.read(arg + 1 + registerX and 0xff) shl 8)
    }

    private fun indY(d: Dummy): Int {
        val arg = cpuRAM.read(programCounter++)
        val address = cpuRAM.read(arg and 0xff) or (cpuRAM.read(arg + 1 and 0xff) shl 8)

        if (address shr 8 != address + registerY shr 8) {
            pageBoundryIndex = 1
        }

        if (address and 0xFF00 != address + registerY and 0xFF00 && d == Dummy.ONCARRY) {
            cpuRAM.read(address and 0xFF00 or (address + registerY and 0xFF))
        }
        if (d == Dummy.ALWAYS) {
            cpuRAM.read(address and 0xFF00 or (address + registerY and 0xFF))
        }

        return address + registerY and 0xffff
    }

    private fun delayInterrupt() {
        isInterruptDelay = true
        isPreviousInterrupt = isInterruptDisabled
    }
}