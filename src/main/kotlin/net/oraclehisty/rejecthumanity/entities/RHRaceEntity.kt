package net.oraclehisty.rejecthumanity.entities

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.SpawnGroupData
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.BreedGoal
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.FollowParentGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.PanicGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.TemptGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.phys.AABB
import net.oraclehisty.rejecthumanity.faction.Faction
import net.oraclehisty.rejecthumanity.genetics.GeneRegistry
import net.oraclehisty.rejecthumanity.genetics.Genome
import net.oraclehisty.rejecthumanity.genetics.Phenotype
import net.oraclehisty.rejecthumanity.genetics.RHDataSerializers
import net.oraclehisty.rejecthumanity.helpers.id
import net.oraclehisty.rejecthumanity.race.Appearance
import net.oraclehisty.rejecthumanity.race.RaceDefinition
import net.oraclehisty.rejecthumanity.race.RaceRegistry
import net.oraclehisty.rejecthumanity.race.Temperament
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * Base for every humanoid race. All looks and inheritance come from the race's datapack definition;
 * subclasses only say which race and faction they are.
 */
abstract class RHRaceEntity(type: EntityType<out RHRaceEntity>, level: Level) : Animal(type, level), GeoEntity {
    abstract val raceKey: ResourceKey<RaceDefinition>
    abstract val faction: Faction

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    val race: RaceDefinition? get() = RaceRegistry.get(level().registryAccess(), raceKey)
    val appearance: Appearance? get() = race?.appearance

    /** Stable per-individual seed for cosmetic choices that are not genes (e.g. which vitiligo patch texture). */
    val appearanceSeed: Long get() = uuid.mostSignificantBits xor uuid.leastSignificantBits

    var genome: Genome
        get() = entityData.get(GENOME)
        set(value) = entityData.set(GENOME, value)

    var phenotype: Phenotype = Phenotype.EMPTY
        private set

    fun rerollGenome() {
        val race = race ?: return
        genome = Genome.random(race.genes, GeneRegistry.lookup(level().registryAccess()), random)
    }

    private fun refreshPhenotype() {
        phenotype = genome.express(GeneRegistry.lookup(level().registryAccess()))
        if (level().isClientSide) return
        val scale = appearance?.scaleFor(phenotype) ?: 1f
        getAttribute(Attributes.SCALE)?.addOrUpdateTransientModifier(
            AttributeModifier(SCALE_MODIFIER, scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        )
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(GENOME, Genome.EMPTY)
    }

    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        if (key == GENOME) refreshPhenotype()
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        Genome.CODEC.encodeStart(NbtOps.INSTANCE, genome).result().ifPresent { tag.put(GENOME_TAG, it) }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        val saved = tag.get(GENOME_TAG)?.let { Genome.CODEC.parse(NbtOps.INSTANCE, it).result().orElse(null) } ?: return
        val race = race
        genome = if (race == null) saved else saved.completedWith(race.genes, GeneRegistry.lookup(level().registryAccess()), random)
    }

    override fun finalizeSpawn(
        level: ServerLevelAccessor,
        difficulty: DifficultyInstance,
        spawnType: MobSpawnType,
        spawnGroupData: SpawnGroupData?,
    ): SpawnGroupData? {
        if (genome.isEmpty) rerollGenome()
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData)
    }

    override fun tick() {
        super.tick()
        // Catch spawns that skip finalizeSpawn (e.g. /summon with NBT, other mods).
        if (!level().isClientSide && genome.isEmpty) rerollGenome()
    }

    // --- Breeding -------------------------------------------------------------------------------

    override fun isFood(stack: ItemStack) = stack.`is`(Items.SWEET_BERRIES)

    override fun canMate(otherAnimal: Animal): Boolean {
        if (otherAnimal !is RHRaceEntity || otherAnimal.raceKey != raceKey || !super.canMate(otherAnimal)) return false
        val gene = race?.mating?.differOn ?: return true
        val mine = phenotype.variant(gene)?.primary
        return mine != null && mine != otherAnimal.phenotype.variant(gene)?.primary
    }

    override fun getBreedOffspring(level: ServerLevel, otherParent: AgeableMob): AgeableMob? {
        val partner = otherParent as? RHRaceEntity ?: return null
        val race = race ?: return null
        val child = type.create(level) as? RHRaceEntity ?: return null
        child.genome = Genome.cross(genome, partner.genome, race.genes, GeneRegistry.lookup(level.registryAccess()), random)
        return child
    }

    // --- AI -------------------------------------------------------------------------------------

    /** From the race file; [Temperament.NEUTRAL] when the race isn't loaded. */
    val temperament: Temperament get() = race?.temperament ?: Temperament.NEUTRAL

    private val fightsBack get() = !isBaby && temperament != Temperament.PASSIVE

    fun sharesFaction(other: Entity?) = other is RHRaceEntity && other.faction == faction

    // Same-faction members are allies: vanilla targeting checks both of these, so no goal can pick a faction member.
    override fun isAlliedTo(other: Entity) = sharesFaction(other) || super.isAlliedTo(other)

    override fun canAttack(target: LivingEntity) = !sharesFaction(target) && super.canAttack(target)

    // Last line of defence for anything that sets a target directly (other mods, commands).
    override fun setTarget(target: LivingEntity?) = super.setTarget(target?.takeUnless(::sharesFaction))

    // Every race gets the same goals; temperament decides which of them may run.
    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, object : PanicGoal(this, 1.4) {
            override fun shouldPanic() = !fightsBack && super.shouldPanic()
        })
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.2, true))
        goalSelector.addGoal(3, BreedGoal(this, 1.0))
        goalSelector.addGoal(4, TemptGoal(this, 1.1, ::isFood, false))
        goalSelector.addGoal(5, FollowParentGoal(this, 1.1))
        goalSelector.addGoal(6, WaterAvoidingRandomStrollGoal(this, 0.8))
        goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 6f))
        goalSelector.addGoal(8, RandomLookAroundGoal(this))
        targetSelector.addGoal(1, FactionHurtByTargetGoal(this).setAlertOthers())
        targetSelector.addGoal(2, object : NearestAttackableTargetGoal<Player>(this, Player::class.java, true) {
            override fun canUse() = !isBaby && temperament == Temperament.HOSTILE && super.canUse()
        })
    }

    /** Like vanilla's "alert same type", but rallies faction members that are willing to fight, instead of the same class. */
    private class FactionHurtByTargetGoal(private val owner: RHRaceEntity) : HurtByTargetGoal(owner) {
        override fun canUse() = owner.fightsBack && !owner.sharesFaction(owner.lastHurtByMob) && super.canUse()

        override fun alertOthers() {
            val attacker = owner.lastHurtByMob ?: return
            val range = followDistance
            val area = AABB.unitCubeFromLowerCorner(owner.position()).inflate(range, 10.0, range)
            owner.level().getEntitiesOfClass(RHRaceEntity::class.java, area) {
                it !== owner && it.faction == owner.faction && it.target == null && it.fightsBack && !it.isAlliedTo(attacker)
            }.forEach { alertOther(it, attacker) }
        }
    }

    // --- GeckoLib -------------------------------------------------------------------------------

    // Locomotion (walk, arm swing, attack, riding, head look) is procedural in RaceGeoModel, mirroring vanilla's
    // HumanoidModel. Keyframed animations are for expressive one-offs layered on top, like the ear twitch.
    override fun registerControllers(registrar: AnimatableManager.ControllerRegistrar) {
        registrar.add(
            AnimationController(this, "ears", 2) { PlayState.STOP }
                .triggerableAnim(EAR_TWITCH, RawAnimation.begin().thenPlay(EAR_TWITCH))
        )
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide && isAlive && random.nextInt(EAR_TWITCH_CHANCE) == 0) triggerAnim("ears", EAR_TWITCH)
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = geoCache

    companion object {
        val GENOME: EntityDataAccessor<Genome> = SynchedEntityData.defineId(RHRaceEntity::class.java, RHDataSerializers.GENOME)
        private const val GENOME_TAG = "Genome"
        private val SCALE_MODIFIER = "genetic_height".id()

        private const val EAR_TWITCH = "ear_twitch"
        private const val EAR_TWITCH_CHANCE = 160

        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 24.0)
    }
}
