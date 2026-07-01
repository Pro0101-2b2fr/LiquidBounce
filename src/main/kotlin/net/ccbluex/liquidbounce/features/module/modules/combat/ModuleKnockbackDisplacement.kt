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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.CRITICAL_MODIFICATION
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.network.sendStartSprinting
import net.ccbluex.liquidbounce.utils.network.sendStopSprinting
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Knockback Displacement module.
 *
 * Manipulate knockback dealt to entities.
 */

@Suppress("MagicNumber")
object ModuleKnockbackDisplacement : ClientModule("KnockbackDisplacement", ModuleCategories.COMBAT, aliases = listOf("KnockbackDirection", "Displace")) {

    private val modes = choices<DisplacementMode>("Mode", 0) {
        arrayOf(
            DisplacementMode.Push(it),
            DisplacementMode.Pull(it),
            DisplacementMode.Upward(it),
            DisplacementMode.Horizontal(it),
            DisplacementMode.Custom(it)
        )
    }.apply(::tagBy)

    private val cooldownTicks by int("Cooldown", 0, 0..40, "ticks")
    private val forceSprint by boolean("ForceSprint", false)
    private val onlyKillAura by boolean("OnlyKillAura", false)
    private val onlyStick by boolean("OnlyStick", false)

    private object VoidDetection : ToggleableValueGroup(ModuleKnockbackDisplacement, "VoidDetection", false) {
        val maxFov by float("MaxFOV", 90f, 0f..180f, "°")
        val checkDistance by float("CheckDistance", 3f, 1f..10f, "blocks")
        val voidLevel by int("VoidLevel", 0, -64..0)
    }

    init {
        tree(VoidDetection)
    }

    private var ticksSinceLastUse = 0

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (ticksSinceLastUse > 0) {
            ticksSinceLastUse--
        }
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent>(priority = CRITICAL_MODIFICATION) { event ->
        val target = event.entity

        if (!shouldOperate(target)) {
            return@handler
        }

        val displacementRotation = getDisplacementRotation(target) ?: return@handler

        val fixedRotation = displacementRotation.normalize()
        network.send(
            PosRot(
                player.x,
                player.y,
                player.z,
                fixedRotation.yaw,
                fixedRotation.pitch,
                player.onGround(),
                player.horizontalCollision
            )
        )

        ticksSinceLastUse = cooldownTicks
    }

    override fun onEnabled() {
        ticksSinceLastUse = 0
    }

    private fun getDisplacementRotation(target: Entity): Rotation? {
        if (ticksSinceLastUse > 0) return null

        if (!player.isSprinting) return null

        if (VoidDetection.enabled && !isVoidBehindTarget(target)) {
            return null
        }

        val dx = target.x - player.x
        val dz = target.z - player.z

        val yawToTarget = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val rawRotation = modes.activeMode.computeRawRotation(yawToTarget)

        val gcd = RotationUtil.gcd.toFloat().coerceAtLeast(0.001f)
        val yaw = Mth.wrapDegrees((rawRotation.x / gcd).roundToInt() * gcd)
        val pitch = Mth.clamp((rawRotation.y / gcd).roundToInt() * gcd, -90f, 90f)

        if (forceSprint && player.isSprinting) {
            network.sendStopSprinting()
            network.sendStartSprinting()
        }

        return Rotation(yaw, pitch)
    }

    private fun isVoidBehindTarget(target: Entity): Boolean {
        val dx = target.x - player.x
        val dz = target.z - player.z

        val distance = Mth.sqrt((dx * dx + dz * dz).toFloat()).toDouble()
        if (distance < 0.01) return false

        val dirX = dx / distance
        val dirZ = dz / distance

        val yawToVoid = Math.toDegrees(atan2(-dirX, dirZ)).toFloat()
        val voidRotation = Rotation(yawToVoid, 0f)
        val angleToVoid = player.rotation.angleTo(voidRotation)

        if (angleToVoid > VoidDetection.maxFov) {
            return false
        }

        val checkDist = VoidDetection.checkDistance.toDouble()
        val checkX = target.x + dirX * checkDist
        val checkZ = target.z + dirZ * checkDist
        val checkY = target.y

        // Create a bounding box from target position extending down to void level
        val checkBox = AABB(
            checkX - 0.3, VoidDetection.voidLevel.toDouble(), checkZ - 0.3,
            checkX + 0.3, checkY, checkZ + 0.3
        )

        // If no collisions found between check position and void level, there's void
        return world.getBlockCollisions(player, checkBox).allEmpty()
    }

    private fun shouldOperate(target: Entity): Boolean {
        if (target !is LivingEntity) return false

        if (onlyKillAura && !ModuleKillAura.running) {
            return false
        }

        // This is specifically meant for gamemodes like BedWars.
        if (onlyStick) {
            val item = player.mainHandItem.item
            val isStick = item == Items.BLAZE_ROD || item == Items.STICK
            if (!isStick) return false
        }

        return true
    }

    private sealed class DisplacementMode(name: String, override val parent: ModeValueGroup<*>) : Mode(name) {
        abstract fun computeRawRotation(yawToTarget: Float): Vec2

        class Push(parent: ModeValueGroup<*>) : DisplacementMode("Push", parent) {
            override fun computeRawRotation(yawToTarget: Float) = Vec2(yawToTarget, player.xRot)
        }

        class Pull(parent: ModeValueGroup<*>) : DisplacementMode("Pull", parent) {
            override fun computeRawRotation(yawToTarget: Float) = Vec2(yawToTarget + 180f, player.xRot)
        }

        class Upward(parent: ModeValueGroup<*>) : DisplacementMode("Upward", parent) {
            override fun computeRawRotation(yawToTarget: Float) = Vec2(yawToTarget, -70f)
        }

        class Horizontal(parent: ModeValueGroup<*>) : DisplacementMode("Horizontal", parent) {
            override fun computeRawRotation(yawToTarget: Float) = Vec2(yawToTarget + 90f, player.xRot)
        }

        class Custom(parent: ModeValueGroup<*>) : DisplacementMode("Custom", parent) {
            private val customYaw by float("CustomYaw", 0f, -180f..180f, "°")
            private val customPitch by float("CustomPitch", 0f, -90f..90f, "°")

            override fun computeRawRotation(yawToTarget: Float) =
                Vec2(yawToTarget + customYaw, player.xRot + customPitch)
        }
    }
}
