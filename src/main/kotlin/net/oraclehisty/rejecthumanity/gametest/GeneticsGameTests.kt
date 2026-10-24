package net.oraclehisty.rejecthumanity.gametest

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.ai.attributes.Attributes
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.oraclehisty.rejecthumanity.RejectHumanity
import net.oraclehisty.rejecthumanity.entities.ElfEntity
import net.oraclehisty.rejecthumanity.entities.RHEntities
import net.oraclehisty.rejecthumanity.genetics.Allele
import net.oraclehisty.rejecthumanity.genetics.GeneRegistry
import net.oraclehisty.rejecthumanity.genetics.Genome
import net.oraclehisty.rejecthumanity.genetics.Genotype
import net.oraclehisty.rejecthumanity.helpers.id
import net.oraclehisty.rejecthumanity.race.RaceRegistry

/** Run with `./gradlew runGameTestServer`. Only active when the rejecthumanity gametest namespace is enabled (dev runs). */
@GameTestHolder(RejectHumanity.ID)
@PrefixGameTestTemplate(false)
object GeneticsGameTests {
    private val SEX = "sex".id()
    private val ALBINISM = "albinism".id()
    private val EYE = "eye_color".id()
    private val SKIN = "skin_tone".id()
    private val FACIAL_HAIR = "facial_hair".id()

    private fun x(id: String) = Allele.Discrete(id)

    @GameTest(template = "floor")
    @JvmStatic
    fun datapackLoads(helper: GameTestHelper) {
        val access = helper.level.registryAccess()
        val genes = access.registry(GeneRegistry.KEY).map { it.size() }.orElse(0)
        helper.assertTrue(genes >= 18, "Expected the 18 built-in genes, found $genes")
        helper.assertTrue(RaceRegistry.get(access, ElfEntity.RACE) != null, "Elf race did not load")
        helper.succeed()
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun elfGetsGenomeAndScale(helper: GameTestHelper) {
        val elf = helper.spawn(RHEntities.ELF, BlockPos(2, 1, 2))
        helper.succeedWhen {
            helper.assertFalse(elf.genome.isEmpty, "Elf has no genome")
            helper.assertTrue(elf.phenotype.variant(SEX) != null, "Sex not expressed")
            val scale = elf.getAttributeValue(Attributes.SCALE)
            helper.assertTrue(scale in 0.85..1.11, "Scale $scale outside the race's range")
        }
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun genomeSurvivesSaveAndLoad(helper: GameTestHelper) {
        val elf = helper.spawn(RHEntities.ELF, BlockPos(2, 1, 2))
        helper.succeedWhen {
            helper.assertFalse(elf.genome.isEmpty, "Elf has no genome yet")
            val tag = elf.saveWithoutId(CompoundTag())
            val copy = RHEntities.ELF.create(helper.level)!!
            copy.load(tag)
            helper.assertTrue(copy.genome == elf.genome, "Loaded genome differs from saved one")
        }
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun inheritanceFollowsMendel(helper: GameTestHelper) {
        val access = helper.level.registryAccess()
        val lookup = GeneRegistry.lookup(access)
        val plan = RaceRegistry.get(access, ElfEntity.RACE)!!.genes
        val random = helper.level.random
        val base = Genome.random(plan, lookup, random)
        val mother = base.with(SEX, Genotype(x("x"), x("x"))).with(ALBINISM, Genotype(x("normal"), x("albino")))
        val father = base.with(SEX, Genotype(x("x"), x("y"))).with(ALBINISM, Genotype(x("albino"), x("normal")))

        val children = List(2000) { Genome.cross(mother, father, plan, lookup, random) }
        val males = children.count { it.express(lookup).variant(SEX)?.primary == "y" }
        val albinos = children.count { it.express(lookup).variant(ALBINISM)?.primary == "albino" }
        val yy = children.count { it[SEX] == Genotype(x("y"), x("y")) }

        helper.assertTrue(yy == 0, "$yy children were YY")
        helper.assertTrue(males in 850..1150, "Expected ~50% males, got $males/2000")
        // Carrier x carrier: 25% albino, plus a tiny mutation contribution.
        helper.assertTrue(albinos in 400..600, "Expected ~25% albino, got $albinos/2000")
        helper.succeed()
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun epistasisAndSexLimitedTraits(helper: GameTestHelper) {
        val access = helper.level.registryAccess()
        val lookup = GeneRegistry.lookup(access)
        val plan = RaceRegistry.get(access, ElfEntity.RACE)!!.genes
        val base = Genome.random(plan, lookup, helper.level.random)
            .with(FACIAL_HAIR, Genotype(x("beard"), x("beard")))
            .with(EYE, Genotype(x("green"), x("blue")))

        val albino = base.with(ALBINISM, Genotype(x("albino"), x("albino"))).express(lookup)
        helper.assertTrue(albino.variant(EYE)?.primary == "red", "Albinism should force red eyes")
        helper.assertTrue(albino.value(SKIN) == 0f, "Albinism should zero skin pigment")

        val female = base.with(SEX, Genotype(x("x"), x("x"))).express(lookup)
        val male = base.with(SEX, Genotype(x("x"), x("y"))).express(lookup)
        helper.assertTrue(female.variant(FACIAL_HAIR)?.primary == "none", "Females must not express facial hair")
        helper.assertTrue(male.variant(FACIAL_HAIR)?.primary == "beard", "Males should express their beard alleles")
        helper.assertTrue(male.variant(EYE)?.let { it.primary == "green" && it.secondary == "blue" } == true, "Secondary eye allele lost")
        helper.succeed()
    }
}
