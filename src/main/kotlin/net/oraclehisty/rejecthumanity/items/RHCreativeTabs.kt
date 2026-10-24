package net.oraclehisty.rejecthumanity.items

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredRegister
import net.oraclehisty.rejecthumanity.RejectHumanity
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

object RHCreativeTabs {
    private val TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RejectHumanity.ID).apply { register(MOD_BUS) }

    val MAIN = TABS.register("main") { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.${RejectHumanity.ID}"))
            // Placeholder icon until the mod has its own item art.
            .icon { ItemStack(RHItems.ELF_SPAWN_EGG) }
            .displayItems { _, output ->
                output.accept(RHItems.ELF_SPAWN_EGG)
                output.accept(RHItems.DROW_SPAWN_EGG)
                output.accept(RHItems.WEREWOLF_SPAWN_EGG)
            }
            .build()
    }

    fun register() {}
}
