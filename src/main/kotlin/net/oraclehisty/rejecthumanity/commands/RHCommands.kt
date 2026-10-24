package net.oraclehisty.rejecthumanity.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.oraclehisty.rejecthumanity.RejectHumanity
import net.oraclehisty.rejecthumanity.entities.RHRaceEntity
import net.oraclehisty.rejecthumanity.genetics.Allele
import net.oraclehisty.rejecthumanity.genetics.GeneRegistry
import net.oraclehisty.rejecthumanity.genetics.Genotype
import net.oraclehisty.rejecthumanity.helpers.argument
import net.oraclehisty.rejecthumanity.helpers.entity
import net.oraclehisty.rejecthumanity.helpers.execute
import net.oraclehisty.rejecthumanity.helpers.literal
import net.oraclehisty.rejecthumanity.helpers.register
import net.oraclehisty.rejecthumanity.race.RaceRegistry

/** `/rh ...` debug commands for inspecting and editing genomes. */
object RHCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register("rh") {
            requires { it.hasPermission(2) }
            literal("testCommand") {
                execute {
                    RejectHumanity.LOGGER.info("Test Command executed")
                    1
                }
            }
            literal("genes") { execute { listDefinitions() } }
            literal("genome") { entity("targets") { execute { forEachRaceEntity { showGenome(it) } } } }
            literal("reroll") {
                entity("targets") {
                    execute {
                        forEachRaceEntity {
                            it.rerollGenome()
                            source.sendSuccess({ Component.literal("Rerolled ${it.name.string}") }, true)
                        }
                    }
                }
            }
            literal("setgene") {
                entity("targets") {
                    argument("gene", ResourceLocationArgument.id()) {
                        suggests { ctx, builder -> SharedSuggestionProvider.suggestResource(geneIds(ctx), builder) }
                        argument("first", StringArgumentType.word()) {
                            argument("second", StringArgumentType.word()) {
                                execute { setGene() }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun geneIds(ctx: CommandContext<CommandSourceStack>): Iterable<ResourceLocation> =
        ctx.source.registryAccess().registry(GeneRegistry.KEY).map { it.keySet() }.orElse(emptySet())

    private fun CommandContext<CommandSourceStack>.forEachRaceEntity(action: (RHRaceEntity) -> Unit): Int {
        val targets = EntityArgument.getEntities(this, "targets").filterIsInstance<RHRaceEntity>()
        if (targets.isEmpty()) source.sendFailure(Component.literal("No genetic entities selected"))
        targets.forEach(action)
        return targets.size
    }

    private fun CommandContext<CommandSourceStack>.listDefinitions(): Int {
        val access = source.registryAccess()
        val genes = access.registry(GeneRegistry.KEY).map { it.keySet().sorted() }.orElse(emptyList())
        val races = access.registry(RaceRegistry.KEY).map { it.keySet().sorted() }.orElse(emptyList())
        source.sendSuccess({ Component.literal("Genes (${genes.size}): ${genes.joinToString()}") }, false)
        source.sendSuccess({ Component.literal("Races (${races.size}): ${races.joinToString()}") }, false)
        return genes.size
    }

    private fun CommandContext<CommandSourceStack>.showGenome(entity: RHRaceEntity) {
        val phenotype = entity.phenotype
        val lines = entity.genome.genotypes.entries.sortedBy { it.key.toString() }.joinToString("\n") { (id, genotype) ->
            val shown = phenotype[id]?.toString() ?: "-"
            val hidden = if (id in phenotype.hidden) " [hidden]" else ""
            "  ${id.path}: $genotype -> $shown$hidden"
        }
        source.sendSuccess({ Component.literal("${entity.name.string} (${entity.uuid}):\n$lines") }, false)
    }

    private fun CommandContext<CommandSourceStack>.setGene(): Int {
        val gene = ResourceLocationArgument.getId(this, "gene")
        val def = GeneRegistry.lookup(source.registryAccess())(gene)
        if (def == null) {
            source.sendFailure(Component.literal("Unknown gene $gene"))
            return 0
        }
        val first = Allele.parse(StringArgumentType.getString(this, "first"))
        val second = Allele.parse(StringArgumentType.getString(this, "second"))
        val invalid = listOf(first, second).filterNot(def::accepts)
        if (invalid.isNotEmpty()) {
            source.sendFailure(Component.literal("Gene $gene does not accept: ${invalid.joinToString()}"))
            return 0
        }
        return forEachRaceEntity {
            it.genome = it.genome.with(gene, Genotype(first, second))
            source.sendSuccess({ Component.literal("Set ${gene.path} = $first/$second on ${it.name.string}") }, true)
        }
    }
}
