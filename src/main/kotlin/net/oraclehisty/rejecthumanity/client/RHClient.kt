package net.oraclehisty.rejecthumanity.client

import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.neoforged.api.distmarker.Dist.CLIENT
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.oraclehisty.rejecthumanity.RejectHumanity
import net.oraclehisty.rejecthumanity.entities.DrowEntity
import net.oraclehisty.rejecthumanity.entities.ElfEntity
import net.oraclehisty.rejecthumanity.entities.RHEntities
import net.oraclehisty.rejecthumanity.entities.WerewolfEntity
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(RejectHumanity.ID, dist = [CLIENT])
object RHClient {
    init {
        MOD_BUS.addListener(::registerEntityRenderers)
        MOD_BUS.addListener<RegisterClientReloadListenersEvent> { event ->
            event.registerReloadListener(ResourceManagerReloadListener { AppearanceLayer.clearCache() })
        }
    }

    fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(RHEntities.ELF) { RaceRenderer<ElfEntity>(it) }
        event.registerEntityRenderer(RHEntities.DROW) { RaceRenderer<DrowEntity>(it) }
        event.registerEntityRenderer(RHEntities.WEREWOLF) { RaceRenderer<WerewolfEntity>(it) }
    }
}
