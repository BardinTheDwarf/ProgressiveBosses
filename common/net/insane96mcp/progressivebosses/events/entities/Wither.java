package net.insane96mcp.progressivebosses.events.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;

import net.insane96mcp.progressivebosses.events.entities.ai.WitherMinionAIHurtByTarget;
import net.insane96mcp.progressivebosses.item.ModItems;
import net.insane96mcp.progressivebosses.lib.ModConfig;
import net.insane96mcp.progressivebosses.lib.Reflection;
import net.insane96mcp.progressivebosses.lib.Utils;
import net.insane96mcp.progressivebosses.lib.Utils.CustomReward;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIRestrictSun;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

public class Wither {

	public static List<CustomReward> customRewards = new ArrayList<>();
	
	public static void SetStats(EntityJoinWorldEvent event) {
		if (!(event.getEntity() instanceof EntityWither))
			return;
		
		EntityWither wither = (EntityWither)event.getEntity();
		
		NBTTagCompound tags = wither.getEntityData();
		boolean alreadySpawned = tags.getBoolean("progressivebosses:spawned");
		
		if (alreadySpawned)
			return;
		
		tags.setBoolean("progressivebosses:spawned", true);

		int radius = ModConfig.config.wither.general.spawnRadiusPlayerCheck;
		BlockPos pos1 = wither.getPosition().add(-radius, -radius, -radius);
		BlockPos pos2 = wither.getPosition().add(radius, radius, radius);
		AxisAlignedBB bb = new AxisAlignedBB(pos1, pos2);
		
		List<EntityPlayerMP> players = event.getWorld().getEntitiesWithinAABB(EntityPlayerMP.class, bb);
		if (players.size() == 0)
			return;
		
		float spawnedCount = 1;
		for (EntityPlayerMP player : players) {
			NBTTagCompound playerTags = player.getEntityData();
			int c = playerTags.getInteger("progressivebosses:spawnedwithers");
			spawnedCount += c;
			if (c >= ModConfig.config.wither.general.maxDifficulty)
				continue;
			playerTags.setInteger("progressivebosses:spawnedwithers", c + 1);
		}
		
		if (spawnedCount == 1)
			return;
		
		if (!ModConfig.config.wither.general.sumSpawnedWitherDifficulty)
			spawnedCount /= players.size();
		
		SetHealth(wither, spawnedCount);
		SetArmor(wither, spawnedCount);
		SetExperience(wither, spawnedCount);
		
		tags.setFloat("progressivebosses:difficulty", spawnedCount);
		
		int cooldown = MathHelper.getInt(wither.getRNG(), ModConfig.config.wither.minions.minCooldown, ModConfig.config.wither.minions.maxCooldown);
		tags.setInteger("progressivebosses:skeletons_cooldown", ModConfig.config.wither.minions.minCooldown);
	}
	
	private static void SetExperience(EntityWither wither, float difficulty) {
		int xp = 50 + (int) (50 * (ModConfig.config.wither.rewards.bonusExperience * difficulty / 100f));
		Reflection.Set(Reflection.EntityLiving_experienceValue, wither, xp);
	}
	
	private static void SetHealth(EntityWither wither, float spawnedCount) {
		IAttributeInstance health = wither.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
		if (spawnedCount < 0) {
			health.setBaseValue(health.getBaseValue() / -(spawnedCount - 1));
		}
		else {
			health.setBaseValue(health.getBaseValue() + (spawnedCount * ModConfig.config.wither.health.bonusPerDifficulty));
		}
		wither.setHealth(Math.max(1, (float) health.getBaseValue() - 200));
	}
	
	private static void SetArmor(EntityWither wither, float killedCount) {
		IAttributeInstance attribute = wither.getEntityAttribute(SharedMonsterAttributes.ARMOR);
		float armor = killedCount * ModConfig.config.wither.armor.bonusPerDifficulty;
		if (armor > ModConfig.config.wither.armor.maximum)
			armor = ModConfig.config.wither.armor.maximum;
		attribute.setBaseValue(armor);
	}
	
	
	public static void Update(LivingUpdateEvent event) {
		if (!(event.getEntity() instanceof EntityWither))
			return;
		
		World world = event.getEntity().world;
		
		EntityWither wither = (EntityWither)event.getEntity();
		NBTTagCompound tags = wither.getEntityData();

		if (wither.getInvulTime() > 0) {
			Reflection.Invoke(Reflection.BossInfoServer_setPercent, Reflection.Get(Reflection.EntityWither_bossInfo, wither), wither.getHealth() / wither.getMaxHealth());
		}
		else {
			SpawnSkeletons(wither, world);
			Heal(wither, tags);
		}
	}
	
	private static void Heal(EntityWither wither, NBTTagCompound tags) {
		if (ModConfig.config.wither.health.maximumBonusRegen == 0.0f)
			return;
		
		if (wither.ticksExisted % 20 != 0)
			return;
		
		float difficulty = tags.getFloat("progressivebosses:difficulty");
		
		if (difficulty <= 0)
			return;
		
		float maxHeal = ModConfig.config.wither.health.maximumBonusRegen;
		float heal = difficulty * ModConfig.config.wither.health.bonusRegenPerSpawned;
		
		if (heal > maxHeal)
			heal = maxHeal;
		
		float health = wither.getHealth();

		if (wither.getHealth() < wither.getMaxHealth() && wither.getHealth() > 0.0f)
			wither.setHealth(health + heal);
	}
	
	private static void SpawnSkeletons(EntityWither wither, World world) {
		if (ModConfig.config.wither.minions.difficultyToSpawn < 0)
			return;
		
		int radius = 24;
		BlockPos pos1 = wither.getPosition().add(-radius, -radius, -radius);
		BlockPos pos2 = wither.getPosition().add(radius, radius, radius);
		AxisAlignedBB bb = new AxisAlignedBB(pos1, pos2);
		List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, bb);
		
		if (players.isEmpty())
			return;
		
		List<EntityWitherSkeleton> minions = world.getEntitiesWithinAABB(EntityWitherSkeleton.class, bb);
		int minionsCount = minions.size();

		if (minionsCount >= ModConfig.config.wither.minions.maxAround && ModConfig.config.wither.minions.maxAround > 0)
			return;

		if (ModConfig.config.wither.minions.maxSpawned == 0)
			return;
		
		NBTTagCompound tags = wither.getEntityData();
		
		//Mobs Properties Randomness
		tags.setBoolean("mobsrandomizzation:preventProcessing", true);
		
		float difficulty = tags.getFloat("progressivebosses:difficulty");
		
		int cooldown = tags.getInteger("progressivebosses:skeletons_cooldown");
		if (cooldown > 0) {
			tags.setInteger("progressivebosses:skeletons_cooldown", cooldown - 1);
		}
		else { 
			cooldown = MathHelper.getInt(world.rand, ModConfig.config.wither.minions.minCooldown, ModConfig.config.wither.minions.maxCooldown);
			tags.setInteger("progressivebosses:skeletons_cooldown", cooldown);
			for (int i = ModConfig.config.wither.minions.difficultyToSpawn; i <= difficulty; i++) {
				if (minionsCount >= ModConfig.config.wither.minions.maxAround && ModConfig.config.wither.minions.maxAround > 0)
					return;
				
				int spawn = i - ModConfig.config.wither.minions.difficultyToSpawn;
				
				//Stops spawning if max count has reached
				if (spawn / ModConfig.config.wither.minions.difficultyToSpawnOneMore >= ModConfig.config.wither.minions.maxSpawned)
					break;
				
				if (spawn % ModConfig.config.wither.minions.difficultyToSpawnOneMore == 0) {
					EntityWitherSkeleton witherSkeleton = new EntityWitherSkeleton(world);
					NBTTagCompound skellyTags = witherSkeleton.getEntityData();
					//Scaling Health
					skellyTags.setShort("scalinghealth:difficulty", (short) -1);
					
					int x = 0;
					int y = 0;
					int z = 0;
					
					boolean shouldSpawn = false;
					//Try to spawn the wither skeleton up to 10 times
					for (int t = 0; t < 10; t++) {
						x = (int) (wither.posX + (MathHelper.getInt(world.rand, -3, 3)));
						y = (int) (wither.posY - 3);
						z = (int) (wither.posZ + (MathHelper.getInt(world.rand, -3, 3)));
						
						for (; y < wither.posY + 4; y++) {
							if (canSpawn(witherSkeleton, new BlockPos(x, y, z), world)) {
								shouldSpawn = true;
								break;
							}
						}
						if (shouldSpawn)
							break;
					}
					if (!shouldSpawn)
						continue;
					
					IAttributeInstance armor = witherSkeleton.getEntityAttribute(SharedMonsterAttributes.ARMOR);
					float minArmor = ModConfig.config.wither.minions.minArmor;
					float maxArmor = ModConfig.config.wither.minions.maxArmor;
					armor.setBaseValue(Utils.Math.getFloat(world.rand, minArmor, maxArmor));
					
					IAttributeInstance speedAttibute = witherSkeleton.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
					float maxSpeedMultiplier = 1.25f;
					float speedMultiplier = difficulty / 100f + 1f;
					if (speedMultiplier > maxSpeedMultiplier)
						speedMultiplier = maxSpeedMultiplier;
					speedAttibute.setBaseValue(speedAttibute.getBaseValue() * speedMultiplier);
					
					witherSkeleton.setPosition(x + 0.5f, y + 0.5f, z + 0.5f);
					witherSkeleton.setCustomNameTag("Wither's Minion");
					Reflection.Set(Reflection.EntityLiving_deathLootTable, witherSkeleton, new ResourceLocation("minecraft:empty"));
					Reflection.Set(Reflection.EntityLiving_experienceValue, witherSkeleton, 1);
					
					NBTTagList minionsList = tags.getTagList("minions", Constants.NBT.TAG_COMPOUND);
					NBTTagCompound uuid = new NBTTagCompound();
					uuid.setUniqueId("uuid", witherSkeleton.getUniqueID());
					tags.setTag("minions", minionsList);
					minionsList.appendTag(uuid);

					ArrayList<EntityAITaskEntry> toRemove = new ArrayList<EntityAITaskEntry>();
					for (EntityAITaskEntry task : witherSkeleton.tasks.taskEntries) {
						if (task.action instanceof EntityAIFleeSun)
							toRemove.add(task);
						
						if (task.action instanceof EntityAIRestrictSun)
							toRemove.add(task);
						
						if (task.action instanceof EntityAIAvoidEntity)
							toRemove.add(task);
					}
					for (EntityAITaskEntry entityAITaskEntry : toRemove) {
						witherSkeleton.tasks.taskEntries.remove(entityAITaskEntry);
					}
					toRemove.clear();
					
					
					for (EntityAITaskEntry targetTask : witherSkeleton.targetTasks.taskEntries) {
						if (targetTask.action instanceof EntityAINearestAttackableTarget)
							toRemove.add(targetTask);
						if (targetTask.action instanceof EntityAIHurtByTarget)
							toRemove.add(targetTask);
					}
					for (EntityAITaskEntry entityAITaskEntry : toRemove) {
						witherSkeleton.targetTasks.taskEntries.remove(entityAITaskEntry);
					}
					toRemove.clear();

			        witherSkeleton.targetTasks.addTask(1, new WitherMinionAIHurtByTarget(witherSkeleton, true));
			        witherSkeleton.targetTasks.addTask(2, new EntityAINearestAttackableTarget(witherSkeleton, EntityPlayer.class, false));
			        witherSkeleton.targetTasks.addTask(3, new EntityAINearestAttackableTarget(witherSkeleton, EntityLiving.class, 0, false, false, NOT_UNDEAD));
					
					world.spawnEntity(witherSkeleton);
					
					minionsCount++;
				}
			}
		}
	}
	
	/*
	 * Check if the mob has space to spawn and if sits on solid ground
	 */
	private static boolean canSpawn(Entity mob, BlockPos pos, World world) {
		int height = (int) Math.ceil(mob.height);
		boolean canSpawn = true;
		for (int i = 0; i < height; i++) {
			if (world.getBlockState(pos.up(i)).getMaterial().blocksMovement()) {
				canSpawn = false;
				break;
			}
		}
		if (!world.getBlockState(pos.down()).getMaterial().blocksMovement()) {
			canSpawn = false;
		}
		
		return canSpawn;
	}

	public static void OnDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof EntityWither))
			return;
		
		EntityWither wither = (EntityWither)event.getEntity();
		World world = wither.world;
		
		NBTTagCompound tags = wither.getEntityData();
		NBTTagList minionsList = tags.getTagList("minions", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < minionsList.tagCount(); i++) {
			UUID uuid = minionsList.getCompoundTagAt(i).getUniqueId("uuid");
			AxisAlignedBB axisAlignedBB = new AxisAlignedBB(new BlockPos(wither.getPosition().add(-128, -128, -128)), wither.getPosition().add(128, 128, 128));
			List<EntityWitherSkeleton> witherSkeletons = world.getEntitiesWithinAABB(EntityWitherSkeleton.class, axisAlignedBB);
			for (EntityWitherSkeleton skeleton : witherSkeletons) {
				if (skeleton.getUniqueID().equals(uuid)) {
					skeleton.addPotionEffect(new PotionEffect(Potion.getPotionFromResourceLocation("minecraft:wither"), 10000, 10));
					break;
				}
			}
		}
	}
	
	public static void SetDrops(LivingDropsEvent event) {
		if (!(event.getEntityLiving() instanceof EntityWither))
			return;
		
		EntityWither wither = (EntityWither)event.getEntityLiving();
		
		NBTTagCompound tags = wither.getEntityData();
		float difficulty = tags.getFloat("progressivebosses:difficulty");

		float chance = ModConfig.config.wither.rewards.shardPerDifficulty * difficulty;
		if (chance > ModConfig.config.wither.rewards.shardMaxChance)
			chance = ModConfig.config.wither.rewards.shardMaxChance;

		int tries = (int) (difficulty / ModConfig.config.wither.rewards.shardDivider) + 1;
		if (tries > ModConfig.config.wither.rewards.shardMaxCount)
			tries = ModConfig.config.wither.rewards.shardMaxCount;
		int count = 0;
		for (int i = 0; i < tries; i++) {
			if (wither.world.rand.nextFloat() >= chance / 100f)
				continue;
			count++;
		}
		if (count == 0)
			return;
		
		EntityItem shard = new EntityItem(wither.world, wither.posX, wither.posY, wither.posZ, new ItemStack(ModItems.starShard, count));
		
		event.getDrops().add(shard);
	}

	private static final Predicate<Entity> NOT_UNDEAD = new Predicate<Entity>()
    {
        public boolean apply(@Nullable Entity p_apply_1_)
        {
            return p_apply_1_ instanceof EntityLivingBase && ((EntityLivingBase)p_apply_1_).getCreatureAttribute() != EnumCreatureAttribute.UNDEAD && ((EntityLivingBase)p_apply_1_).attackable();
        }
    };
}
