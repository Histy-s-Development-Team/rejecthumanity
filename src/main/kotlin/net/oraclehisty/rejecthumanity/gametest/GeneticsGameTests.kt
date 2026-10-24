package net.oraclehisty.rejecthumanity.gametest

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import net.oraclehisty.rejecthumanity.RejectHumanity
import net.oraclehisty.rejecthumanity.entities.DrowEntity
import net.oraclehisty.rejecthumanity.entities.ElfEntity
import net.oraclehisty.rejecthumanity.entities.RHEntities
import net.oraclehisty.rejecthumanity.entities.WerewolfEntity
import net.oraclehisty.rejecthumanity.genetics.Allele
import net.oraclehisty.rejecthumanity.genetics.GeneRegistry
import net.oraclehisty.rejecthumanity.genetics.Genome
import net.oraclehisty.rejecthumanity.genetics.Genotype
import net.oraclehisty.rejecthumanity.helpers.id
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.oraclehisty.rejecthumanity.race.BoneRule
import net.oraclehisty.rejecthumanity.race.LifeStage
import net.oraclehisty.rejecthumanity.race.Tint
import net.oraclehisty.rejecthumanity.race.RaceRegistry
import net.oraclehisty.rejecthumanity.race.Temperament

/** Run with `./gradlew runGameTestServer`. Only active when the rejecthumanity gametest namespace is enabled (dev runs). */
@GameTestHolder(RejectHumanity.ID)
@PrefixGameTestTemplate(false)
object GeneticsGameTests {
    private val SEX = "sex".id()
    private val ALBINISM = "albinism".id()
    private val EYE = "eye_color".id()
    private val SKIN = "skin_tone".id()
    private val FACIAL_HAIR = "facial_hair".id()
    private val UNDERTONE = "skin_undertone".id()

    private fun x(id: String) = Allele.Discrete(id)

    /** The floor template's stone sits at relative y = 1, so mobs stand at y = 2 (y = 1 would bury them in it). */
    private const val FLOOR_TOP = 2

    @GameTest(template = "floor")
    @JvmStatic
    fun datapackLoads(helper: GameTestHelper) {
        val access = helper.level.registryAccess()
        val genes = access.registry(GeneRegistry.KEY).map { it.size() }.orElse(0)
        helper.assertTrue(genes >= 32, "Expected the 32 built-in genes, found $genes")
        helper.assertTrue(RaceRegistry.get(access, ElfEntity.RACE) != null, "Elf race did not load")
        helper.assertTrue(RaceRegistry.get(access, DrowEntity.RACE) != null, "Drow race did not load")
        helper.assertTrue(RaceRegistry.get(access, WerewolfEntity.RACE) != null, "Werewolf race did not load")
        helper.succeed()
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun elfGetsGenomeAndScale(helper: GameTestHelper) {
        val elf = helper.spawn(RHEntities.ELF, BlockPos(2, FLOOR_TOP, 2))
        helper.succeedWhen {
            helper.assertFalse(elf.genome.isEmpty, "Elf has no genome")
            helper.assertTrue(elf.phenotype.variant(SEX) != null, "Sex not expressed")
            val scale = elf.getAttributeValue(Attributes.SCALE)
            helper.assertTrue(scale in 0.85..1.11, "Scale $scale outside the race's range")
        }
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun werewolfGetsCoatGenes(helper: GameTestHelper) {
        val wolf = helper.spawn(RHEntities.WEREWOLF, BlockPos(2, FLOOR_TOP, 2))
        helper.succeedWhen {
            for (gene in listOf("coat_pigment", "coat_pattern", "ear_shape", "ear_size", "ear_set", "ear_carriage", "tail_shape", "muzzle_length")) {
                helper.assertTrue(wolf.phenotype[gene.id()] != null, "Werewolf is missing $gene")
            }
            helper.assertTrue(wolf.phenotype["skin_tone".id()] == null, "Werewolves should not carry elf skin genes")
        }
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun genomeSurvivesSaveAndLoad(helper: GameTestHelper) {
        val elf = helper.spawn(RHEntities.ELF, BlockPos(2, FLOOR_TOP, 2))
        helper.succeedWhen {
            helper.assertFalse(elf.genome.isEmpty, "Elf has no genome yet")
            val tag = elf.saveWithoutId(CompoundTag())
            val copy = RHEntities.ELF.create(helper.level)!!
            copy.load(tag)
            helper.assertTrue(copy.genome == elf.genome, "Loaded genome differs from saved one")
        }
    }

    // Rallying reaches ~24 blocks, across neighbouring test areas, so each faction test runs in its own batch.
    @GameTest(template = "floor", batch = "faction_rally")
    @JvmStatic
    fun neutralElvesDefendTogether(helper: GameTestHelper) {
        helper.assertTrue(
            RaceRegistry.get(helper.level.registryAccess(), ElfEntity.RACE)?.temperament == Temperament.NEUTRAL,
            "Elves should be neutral",
        )
        val elf = helper.spawn(RHEntities.ELF, BlockPos(2, FLOOR_TOP, 2))
        val bystander = helper.spawn(RHEntities.ELF, BlockPos(3, FLOOR_TOP, 3))
        val attacker = helper.spawn(EntityType.ZOMBIE, BlockPos(2, FLOOR_TOP, 3))
        attacker.isNoAi = true
        helper.runAfterDelay(5) {
            elf.hurt(helper.level.damageSources().mobAttack(attacker), 1f)
        }
        helper.succeedWhen {
            helper.assertTrue(elf.target === attacker, "Hurt neutral elf did not fight back")
            helper.assertTrue(bystander.target === attacker, "Nearby elf was not rallied")
        }
    }

    @GameTest(template = "floor", batch = "faction_kin")
    @JvmStatic
    fun elvesNeverTargetTheirOwnFaction(helper: GameTestHelper) {
        val victim = helper.spawn(RHEntities.ELF, BlockPos(2, FLOOR_TOP, 2))
        val kin = helper.spawn(RHEntities.ELF, BlockPos(3, FLOOR_TOP, 3))
        helper.runAfterDelay(5) {
            victim.hurt(helper.level.damageSources().mobAttack(kin), 1f)
            kin.setTarget(victim)
        }
        helper.runAfterDelay(45) {
            helper.assertTrue(victim.target == null, "Elf targeted ${victim.target} after being hit by its own faction")
            helper.assertTrue(kin.target == null, "Elf accepted a same-faction target")
            helper.succeed()
        }
    }

    @GameTest(template = "floor", batch = "faction_split")
    @JvmStatic
    fun factionsOnlyRallyTheirOwn(helper: GameTestHelper) {
        val drow = helper.spawn(RHEntities.DROW, BlockPos(2, FLOOR_TOP, 2))
        val drowAlly = helper.spawn(RHEntities.DROW, BlockPos(3, FLOOR_TOP, 3))
        val elf = helper.spawn(RHEntities.ELF, BlockPos(3, FLOOR_TOP, 2))
        val attacker = helper.spawn(EntityType.ZOMBIE, BlockPos(2, FLOOR_TOP, 3))
        attacker.isNoAi = true
        helper.runAfterDelay(5) { drow.hurt(helper.level.damageSources().mobAttack(attacker), 1f) }
        // Goals don't run every tick, so wait for the rally rather than checking at a fixed tick.
        helper.succeedWhen {
            helper.assertTrue(elf.target == null, "Elf was rallied by a Drow")
            helper.assertTrue(drowAlly.target === attacker, "Drow did not rally its own faction")
        }
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun undertoneChoosesSkinPalette(helper: GameTestHelper) {
        val access = helper.level.registryAccess()
        val lookup = GeneRegistry.lookup(access)
        val drow = RaceRegistry.get(access, DrowEntity.RACE)!!
        val base = Genome.random(drow.genes, lookup, helper.level.random)
            .with(ALBINISM, Genotype(x("normal"), x("normal")))
            .with(SKIN, Genotype(Allele.Continuous(0f), Allele.Continuous(0f)))
        val appearance = drow.appearance
        fun skin(undertone: String) = appearance.tint(
            appearance.base.tint, base.with(UNDERTONE, Genotype(x(undertone), x(undertone))).express(lookup), 0L,
        )
        helper.assertTrue(skin("blue") == 0x465A94, "Blue undertone gave #%06X".format(skin("blue")))
        helper.assertTrue(skin("natural") == 0xA5683A, "Natural undertone gave #%06X".format(skin("natural")))
        val albino = base.with(ALBINISM, Genotype(x("albino"), x("albino"))).with(UNDERTONE, Genotype(x("blue"), x("blue")))
        helper.assertTrue(appearance.tint(appearance.base.tint, albino.express(lookup), 0L) == 0xF2E8EC, "Albino Drow should be pale")
        helper.succeed()
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun lifeStagesFollowAge(helper: GameTestHelper) {
        val elf = helper.spawn(RHEntities.ELF, BlockPos(2, FLOOR_TOP, 2))
        val growth = elf.growth
        fun check(age: Int, stage: LifeStage, scale: Float) {
            elf.age = age
            helper.assertTrue(elf.lifeStage == stage, "Age $age should be $stage, was ${elf.lifeStage}")
            helper.assertTrue(elf.ageScale == scale, "$stage should be scale $scale, was ${elf.ageScale}")
        }
        elf.isBaby = true
        helper.assertTrue(elf.age == growth.newbornAge, "Newborns should start at ${growth.newbornAge}, got ${elf.age}")
        check(growth.newbornAge, LifeStage.BABY, growth.babyScale)
        check(-growth.teenTicks - 1, LifeStage.BABY, growth.babyScale)
        check(-growth.teenTicks, LifeStage.TEEN, growth.teenScale)
        helper.assertTrue(elf.isBaby, "Teens should still count as young to vanilla (no breeding)")
        check(-1, LifeStage.TEEN, growth.teenScale)
        check(0, LifeStage.ADULT, 1f)
        helper.succeed()
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun breedingProducesNewborns(helper: GameTestHelper) {
        val mother = helper.spawn(RHEntities.ELF, BlockPos(2, FLOOR_TOP, 2))
        val father = helper.spawn(RHEntities.ELF, BlockPos(3, FLOOR_TOP, 2))
        helper.runAfterDelay(2) {
            mother.genome = mother.genome.with(SEX, Genotype(x("x"), x("x")))
            father.genome = father.genome.with(SEX, Genotype(x("x"), x("y")))
            mother.spawnChildFromBreeding(helper.level, father)
            val child = helper.level.getEntitiesOfClass(ElfEntity::class.java, mother.boundingBox.inflate(4.0))
                .firstOrNull { it !== mother && it !== father }
            helper.assertTrue(child != null, "No child was born")
            helper.assertTrue(child!!.lifeStage == LifeStage.BABY, "Child should be born a baby, was ${child.lifeStage}")
            helper.assertTrue(child.age == child.growth.newbornAge, "Child should start at the newborn age")
            helper.succeed()
        }
    }

    @GameTest(template = "floor", batch = "teen_defence")
    @JvmStatic
    fun teensDefendBabiesFlee(helper: GameTestHelper) {
        val teen = helper.spawn(RHEntities.ELF, BlockPos(2, FLOOR_TOP, 2))
        val baby = helper.spawn(RHEntities.ELF, BlockPos(3, FLOOR_TOP, 3))
        val attacker = helper.spawn(EntityType.ZOMBIE, BlockPos(3, FLOOR_TOP, 2))
        attacker.isNoAi = true
        teen.age = -teen.growth.teenTicks
        baby.isBaby = true
        helper.runAfterDelay(5) {
            teen.hurt(helper.level.damageSources().mobAttack(attacker), 1f)
            baby.hurt(helper.level.damageSources().mobAttack(attacker), 1f)
        }
        helper.succeedWhen {
            helper.assertTrue(baby.target == null, "Baby should flee, not fight")
            helper.assertTrue(teen.target === attacker, "Teen should defend itself")
        }
    }

    @GameTest(template = "floor")
    @JvmStatic
    fun raceFilesRejectTypos(helper: GameTestHelper) {
        fun parses(codec: com.mojang.serialization.Codec<*>, json: String) =
            codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isPresent
        helper.assertTrue(parses(BoneRule.CODEC, """{"bone": "Head", "state": "aggressive", "rotate_axes": "xz"}"""), "Valid rule rejected")
        helper.assertFalse(parses(BoneRule.CODEC, """{"bone": "Head", "state": "agressive"}"""), "Misspelt state accepted")
        helper.assertFalse(parses(BoneRule.CODEC, """{"bone": "Head", "axes": "xw"}"""), "Bad axes accepted")
        helper.assertFalse(parses(Tint.CODEC, """{"palette": "eyes", "source": "secondry"}"""), "Misspelt tint source accepted")
        helper.succeed()
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
