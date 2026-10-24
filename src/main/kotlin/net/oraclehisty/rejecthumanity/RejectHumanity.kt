package net.oraclehisty.rejecthumanity

import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.registries.DataPackRegistryEvent
import net.oraclehisty.rejecthumanity.commands.RHCommands
import net.oraclehisty.rejecthumanity.entities.RHEntities
import net.oraclehisty.rejecthumanity.genetics.GeneDefinition
import net.oraclehisty.rejecthumanity.genetics.GeneRegistry
import net.oraclehisty.rejecthumanity.genetics.RHDataSerializers
import net.oraclehisty.rejecthumanity.items.RHItems
import net.oraclehisty.rejecthumanity.race.RaceDefinition
import net.oraclehisty.rejecthumanity.race.RaceRegistry
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(RejectHumanity.ID)
object RejectHumanity {
    const val ID = "rejecthumanity"
    val bus = MOD_BUS
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(RHEntities::registerAttributes)
        MOD_BUS.addListener(RHEntities::registerSpawnPlacements)
        MOD_BUS.addListener(RHItems::addToCreativeTabs)
        MOD_BUS.addListener<DataPackRegistryEvent.NewRegistry> { event ->
            // Same codec for the network so clients receive genes/races and can express + render locally.
            event.dataPackRegistry(GeneRegistry.KEY, GeneDefinition.CODEC, GeneDefinition.CODEC)
            event.dataPackRegistry(RaceRegistry.KEY, RaceDefinition.CODEC, RaceDefinition.CODEC)
        }
        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent> { event -> RHCommands.register(event.dispatcher) }

        RHDataSerializers.register()
        RHItems.register()
        RHEntities.register()
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.info("Reject humanity, NeoForge is Live! (Kotlin {})", KotlinVersion.CURRENT)
    }
}
