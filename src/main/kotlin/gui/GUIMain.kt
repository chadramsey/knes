package gui

import NESMain
import constants.MenuConstants
import java.awt.Canvas
import java.awt.FileDialog
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.image.BufferStrategy
import java.awt.image.BufferedImage
import java.io.File
import java.io.FilenameFilter
import ScreenRenderer
import java.awt.Graphics
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.WindowConstants

class GUIMain : ActionListener, Runnable, JFrame() {

    private val canvas: Canvas = Canvas()
    private val windowWidth: Int = 256
    private val windowHeight: Int = 224
    private val scale: Int = 2

    private lateinit var buffer: BufferStrategy
    private lateinit var frame: BufferedImage

    private var renderer: ScreenRenderer = ScreenRenderer()

    private var frames: LongArray = LongArray(60)

    private var framesPerSecond: Double = 0.0
    private var framePointer: Int = 0
    private var frameSkip: Int = 0

    init {
        run()
    }

    private fun constructMenuOptions() {
        val menuBar = JMenuBar()

        val menuOptionFile = JMenu("File")
        menuOptionFile.add(JMenuItem(MenuConstants.OPEN_ROM))
                .addActionListener(this)
        menuOptionFile.addSeparator()
        menuOptionFile.add(JMenuItem(MenuConstants.QUIT))
                .addActionListener(this)
        menuBar.add(menuOptionFile)

        val menuOptionAbout = JMenu("About")
        menuOptionAbout.add(JMenuItem(MenuConstants.ROM_INFO))
                .addActionListener(this)
        menuBar.add(menuOptionAbout)

        jMenuBar = menuBar
    }

    private fun setRenderProperties() {
        canvas.setSize(windowWidth * scale, windowHeight * scale)
        canvas.isEnabled = false
        this.add(canvas)
        this.pack()
        canvas.createBufferStrategy(2)
        buffer = canvas.bufferStrategy
    }

    private fun loadROMFromLocalFileSystem() {
        val fileDialog = FileDialog(this)
        fileDialog.mode = FileDialog.LOAD
        fileDialog.title = "Select a ROM"
        fileDialog.filenameFilter = FilenameFilter {
            _: File?, name: String? ->
            name!!.endsWith(".nes") ||
                    name.endsWith(".fds") ||
                    name.endsWith(".nsf")
        }
        fileDialog.isVisible = true
        if (fileDialog.file != null) {
            println("Selected ${fileDialog.directory}${fileDialog.file}")
            NESMain.loadSelectedROMFile(fileDialog.directory + fileDialog.file)
            Thread({
                NESMain.beginEmulation()
            }, "Emulation Thread").start()
        }
    }

    override fun actionPerformed(e: ActionEvent?) {
        when (e?.actionCommand) {
            MenuConstants.OPEN_ROM -> {
                loadROMFromLocalFileSystem()
            }
            MenuConstants.QUIT -> {
                NESMain.isPoweredOff = true
                System.exit(0)
            }
            MenuConstants.ROM_INFO -> {
                JOptionPane.showMessageDialog(this,
                        NESMain.romMapper?.getROMMetadata() ?: "No ROM file has been loaded")
            }
            else -> print("Invalid Action")
        }
    }

    fun renderFrame(nextFrame: IntArray) {
        frame = renderer.render(nextFrame)
        render()
    }

    fun render() {
        val graphics: Graphics = buffer.drawGraphics
        graphics.drawImage(frame, 0, 0, windowWidth * scale, windowHeight * scale, null)
        graphics.dispose()
        buffer.show()
    }

    override fun run() {
        this.title = "kNES Emulator"
        this.isResizable = true
        constructMenuOptions()
        setRenderProperties()
        this.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        this.isVisible = true
    }
}