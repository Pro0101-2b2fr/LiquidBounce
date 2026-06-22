/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.integration.backend.input

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.FramebufferResizeEvent
import net.ccbluex.liquidbounce.event.events.KeyboardCharEvent
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.MouseCursorEvent
import net.ccbluex.liquidbounce.event.events.MouseScrollEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.integration.backend.browser.Browser
import net.ccbluex.liquidbounce.utils.client.mc
import org.lwjgl.glfw.GLFW
import java.lang.AutoCloseable

/**
 * Handles input events for a single browser instance.
 *
 * This [EventListener] needs to be unregistered when the browser is closed.
 */
class InputListener(
    val browser: Browser,
    val inputHandler: InputHandler,
    val acceptor: InputAcceptor,
) : EventListener, AutoCloseable {

    private var mouseX: Double = 0.0
    private var mouseY: Double = 0.0

    // Cached framebuffer-to-screen scale factors, updated only on resize
    private var factorW: Double = mc.window.width.toDouble() / mc.window.screenWidth.toDouble()
    private var factorH: Double = mc.window.height.toDouble() / mc.window.screenHeight.toDouble()

    @Suppress("unused")
    private val framebufferResizeHandler = handler<FramebufferResizeEvent> {
        val screenW = mc.window.screenWidth.toDouble()
        val screenH = mc.window.screenHeight.toDouble()
        if (screenW > 0 && screenH > 0) {
            factorW = mc.window.width.toDouble() / screenW
            factorH = mc.window.height.toDouble() / screenH
        }
    }

    @Suppress("unused")
    private val mouseButtonHandler = handler<MouseButtonEvent> { event ->
        if (!acceptor.acceptsInput()) {
            return@handler
        }

        val vp = browser.viewport
        if (event.action == GLFW.GLFW_PRESS) {
            inputHandler.mouseClicked(mouseX - vp.x, mouseY - vp.y, event.button)
        } else if (event.action == GLFW.GLFW_RELEASE) {
            inputHandler.mouseReleased(mouseX - vp.x, mouseY - vp.y, event.button)
        }
    }

    @Suppress("unused")
    private val mouseScrollHandler = handler<MouseScrollEvent> { event ->
        if (!acceptor.acceptsInput()) {
            return@handler
        }

        val vp = browser.viewport
        inputHandler.mouseScrolled(mouseX - vp.x, mouseY - vp.y, event.vertical)
    }

    @Suppress("unused")
    private val mouseCursorHandler = handler<MouseCursorEvent> { event ->
        val mouseX = event.x * factorW
        val mouseY = event.y * factorH

        if (acceptor.acceptsInput()) {
            val vp = browser.viewport
            inputHandler.mouseMoved(mouseX - vp.x, mouseY - vp.y)
        }

        this.mouseX = mouseX
        this.mouseY = mouseY
    }

    @Suppress("unused")
    private val keyboardKeyHandler = handler<KeyboardKeyEvent> { event ->
        if (!acceptor.acceptsInput()) {
            return@handler
        }

        val action = event.action
        val key = event.keyCode
        val scancode = event.scanCode
        val modifiers = event.mods

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            inputHandler.keyPressed(key, scancode, modifiers)
        } else if (action == GLFW.GLFW_RELEASE) {
            inputHandler.keyReleased(key, scancode, modifiers)
        }
    }

    @Suppress("unused")
    private val keyboardCharHandler = handler<KeyboardCharEvent> { ev ->
        if (!acceptor.acceptsInput()) {
            return@handler
        }

        inputHandler.charTyped(ev.codePoint)
    }

    override fun close() {
        EventManager.unregisterEventHandler(this)
    }

}
