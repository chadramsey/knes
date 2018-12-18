import controller.GameController
import gui.GUIMain
import mappers.Mapper
import java.lang.System.setProperty
import java.nio.file.Paths

object NESMain {

    private var guiMain: GUIMain? = null
    private var romLoader: ROMLoader? = null
    var romMapper: Mapper? = null
    private var cpuRAM: RAM? = null
    var cpu: CPU? = null
    var ppu: PPU? = null
    var controller: GameController? = null

    private var isEmulatorRunning = false
    var isPoweredOff = false

    // Initialize the application
    @JvmStatic fun main(args: Array<String>) {

        // For jInput natives
        setProperty("java.library.path", Paths.get("./libs").toAbsolutePath().toString())
        val fieldSysPath = ClassLoader::class.java.getDeclaredField("sys_paths")
        fieldSysPath.isAccessible = true
        fieldSysPath.set(null, null)

        guiMain = GUIMain()
        controller = GameController(guiMain!!)
        controller!!.startEventQueue()
    }

    fun loadSelectedROMFile(filepath: String) {
        romLoader = ROMLoader(filepath)
        romMapper = Mapper.locateMapperForROMAndSetLoader(romLoader!!)
        romMapper?.loadROMValues()
        cpuRAM = RAM(romMapper!!)
        cpu = CPU(cpuRAM!!)
        ppu = PPU(romMapper!!)

        isEmulatorRunning = true
    }

    fun beginEmulation() {
        while (!isPoweredOff) {
            if (isEmulatorRunning) {
                Thread.sleep(16)
                performFrameOperation()
            } else {
                if (ppu != null) {
                    guiMain!!.render()
                }
            }
        }
    }

    private fun performFrameOperation() {
        ppu?.performFrameOperation()
        cpu?.clocks = 0
        ppu?.renderFrame(guiMain!!)
    }
}