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

    // Cache framebuffer-to-screen scale factors — only changes on window/framebuffer resize.
    // Avoids 2 double divisions per MouseCursorEvent (~60+ Hz).
    private var factorW: Double = computeFactorW()
    private var factorH: Double = computeFactorH()

    private fun computeFactorW(): Double =
        mc.window.width.toDouble() / mc.window.screenWidth.toDouble()

    private fun computeFactorH(): Double =
        mc.window.height.toDouble() / mc.window.screenHeight.toDouble()

    @Suppress("unused")
    private val framebufferResizeHandler = handler<FramebufferResizeEvent> {
        factorW = computeFactorW()
        factorH = computeFactorH()
    }

    @Suppress("unused")
    private val mouseButtonHandler = handler<MouseButtonEvent> { event ->
        if (!acceptor.acceptsInput()) {
            return@handler
        }

        if (event.action == GLFW.GLFW_PRESS) {
            val transformedX = mouseX - browser.viewport.x
            val transformedY = mouseY - browser.viewport.y
            inputHandler.mouseClicked(transformedX, transformedY, event.button)
        } else if (event.action == GLFW.GLFW_RELEASE) {
            val transformedX = mouseX - browser.viewport.x
            val transformedY = mouseY - browser.viewport.y
            inputHandler.mouseReleased(transformedX, transformedY, event.button)
        }
    }

    @Suppress("unused")
    private val mouseScrollHandler = handler<MouseScrollEvent> { event ->
        if (!acceptor.acceptsInput()) {
            return@handler
        }

        val transformedX = mouseX - browser.viewport.x
        val transformedY = mouseY - browser.viewport.y
        inputHandler.mouseScrolled(transformedX, transformedY, event.vertical)
    }

    @Suppress("unused")
    private val mouseCursorHandler = handler<MouseCursorEvent> { event ->
        val mouseX = event.x * factorW
        val mouseY = event.y * factorH

        if (acceptor.acceptsInput()) {
            val transformedX = mouseX - browser.viewport.x
            val transformedY = mouseY - browser.viewport.y
            inputHandler.mouseMoved(transformedX, transformedY)
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
