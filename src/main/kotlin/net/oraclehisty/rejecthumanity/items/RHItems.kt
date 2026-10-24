package net.oraclehisty.rejecthumanity.items

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.neoforged.neoforge.common.DeferredSpawnEggItem
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.oraclehisty.rejecthumanity.entities.RHEntities
import net.oraclehisty.rejecthumanity.helpers.SingletonRegister
import java.util.function.Supplier

object RHItems: SingletonRegister<Item>(BuiltInRegistries.ITEM) {
    val GENERIC by create("generic") {
        Item(Item.Properties())
    }

    val ELF_SPAWN_EGG by create("elf_spawn_egg") {
        DeferredSpawnEggItem(Supplier { RHEntities.ELF }, 0x2E6B3A, 0xD9C27A, Item.Properties())
    }

    fun addToCreativeTabs(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.SPAWN_EGGS) event.accept(ELF_SPAWN_EGG)
    }
}
