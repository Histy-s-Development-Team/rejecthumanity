package net.oraclehisty.rejecthumanity.items

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.neoforged.neoforge.common.DeferredSpawnEggItem
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

    val DROW_SPAWN_EGG by create("drow_spawn_egg") {
        DeferredSpawnEggItem(Supplier { RHEntities.DROW }, 0x2B2440, 0xD8DCE6, Item.Properties())
    }

    val WEREWOLF_SPAWN_EGG by create("werewolf_spawn_egg") {
        DeferredSpawnEggItem(Supplier { RHEntities.WEREWOLF }, 0x6B5A48, 0xC9B08A, Item.Properties())
    }
}
