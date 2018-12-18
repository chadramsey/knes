package controller

import net.java.games.input.Component
import net.java.games.input.Controller
import net.java.games.input.ControllerEnvironment
import net.java.games.input.Event
import constants.DataConstants
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class GameController(guiComponent: java.awt.Component) : KeyListener {

    init {
        guiComponent.addKeyListener(this)
    }

    private val controller: Controller = ControllerEnvironment.getDefaultEnvironment().controllers.first()
    private val buttons: Array<Component> = controller.components.filter {
        it.identifier is Component.Identifier.Button
    }.toTypedArray()
    private val thread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val controllerMap: HashMap<Int, Int> = hashMapOf(
            KeyEvent.VK_UP to DataConstants.BIT4,
            KeyEvent.VK_DOWN to DataConstants.BIT5,
            KeyEvent.VK_LEFT to DataConstants.BIT6,
            KeyEvent.VK_RIGHT to DataConstants.BIT7,
            KeyEvent.VK_X to DataConstants.BIT0,
            KeyEvent.VK_Z to DataConstants.BIT1,
            KeyEvent.VK_SHIFT to DataConstants.BIT2,
            KeyEvent.VK_ENTER to DataConstants.BIT3
    )
    private var latchByte: Int = 0
    private var controllerByte: Int = 0
    private var previousControllerByte: Int = 0
    var outByte: Int = 0
    private var gamepadByte: Int = 0

    override fun keyTyped(e: KeyEvent?) {
    }

    override fun keyPressed(e: KeyEvent?) {
        pressKey(e!!.keyCode)
    }

    private fun pressKey(keyCode: Int) {
        previousControllerByte = controllerByte
        if (!controllerMap.containsKey(keyCode)) {
            return
        }
        controllerByte = controllerByte or controllerMap[keyCode]!!
        if (controllerByte and (DataConstants.BIT4 or DataConstants.BIT5) == DataConstants.BIT4 or DataConstants.BIT5) {
            controllerByte = controllerByte and (DataConstants.BIT4 or DataConstants.BIT5).inv()
            controllerByte = controllerByte or (previousControllerByte and (DataConstants.BIT4 or DataConstants.BIT5).inv())
        }
        if (controllerByte and (DataConstants.BIT6 or DataConstants.BIT7) == DataConstants.BIT6 or DataConstants.BIT7) {
            controllerByte = controllerByte and (DataConstants.BIT6 or DataConstants.BIT7).inv()
            controllerByte = controllerByte or (previousControllerByte and (DataConstants.BIT6 or DataConstants.BIT7).inv())
        }
    }

    override fun keyReleased(e: KeyEvent?) {
        releaseKey(e!!.keyCode)
    }

    private fun releaseKey(keyCode: Int) {
        previousControllerByte = controllerByte
        if (!controllerMap.containsKey(keyCode)) {
            return
        }
        controllerByte = controllerByte and controllerMap[keyCode]!!.inv()
    }

    private fun eventQueueLoop(): Runnable {
        return Runnable {
            val event = Event()
            while (!Thread.interrupted()) {
                controller.poll()
                val queue = controller.eventQueue
                while (queue.getNextEvent(event)) {
                    val component = event.component
                    when {
                        component === buttons[0] -> gamepadByte = if (isKeyPressed(event)) {
                            gamepadByte or DataConstants.BIT0
                        } else {
                            gamepadByte and DataConstants.BIT0.inv()
                        }
                        component === buttons[1] -> gamepadByte = if (isKeyPressed(event)) {
                            gamepadByte or DataConstants.BIT1
                        } else {
                            gamepadByte and DataConstants.BIT1.inv()
                        }
                        component === buttons[2] -> gamepadByte = if (isKeyPressed(event)) {
                            gamepadByte or DataConstants.BIT2
                        } else {
                            gamepadByte and DataConstants.BIT2.inv()
                        }
                        component === buttons[3] -> gamepadByte = if (isKeyPressed(event)) {
                            gamepadByte or DataConstants.BIT3
                        } else {
                            gamepadByte and DataConstants.BIT3.inv()
                        }
                    }
                }

                try {
                    Thread.sleep(5)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    fun startEventQueue() {
        thread.execute(eventQueueLoop())
    }

    private fun isKeyPressed(event: Event): Boolean {
        return event.value.toInt() != 0
    }

    fun strobe() {
        outByte = latchByte and 1
        latchByte = latchByte shr 1 or 0x100
    }

    fun output() {
        latchByte = gamepadByte or controllerByte
    }
}