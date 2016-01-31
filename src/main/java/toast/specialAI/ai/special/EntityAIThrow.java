package toast.specialAI.ai.special;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.nbt.NBTTagCompound;

public class EntityAIThrow extends EntityAIBase implements ISpecialAI {
    // The weight of this AI pattern.
    private int WEIGHT;

    // The owner of this AI.
    protected EntityLiving theEntity;

    // The mob the host wants to throw.
    private EntityLiving throwTarget;
    // Ticks until next attack.
    private byte attackTime;
    // Ticks until the entity gives up.
    private int giveUpDelay;

    public EntityAIThrow() {}

    private EntityAIThrow(EntityLiving entity) {
        this.theEntity = entity;
        this.setMutexBits(3);
    }

    // Returns the string name of this AI for use in Properties.
    @Override
    public String getName() {
        return "throw";
    }

    // Gets/sets the weight as defined in Properties.
    @Override
    public int getWeight() {
        return this.WEIGHT;
    }

    @Override
    public void setWeight(int weight) {
        this.WEIGHT = weight;
    }

    // Adds a copy of this AI to the given entity.
    @Override
    public void addTo(EntityLiving entity, NBTTagCompound aiTag) {
        entity.tasks.addTask(0, new EntityAIThrow(entity));
    }

    // Saves this AI to the tag with its default value.
    @Override
    public void save(NBTTagCompound aiTag) {
        aiTag.setByte(this.getName(), (byte) 1);
    }

    // Returns true if a copy of this AI is saved to the tag.
    @Override
    public boolean isSaved(NBTTagCompound aiTag) {
        return aiTag.getByte(this.getName()) > 0;
    }

    // Initializes any one-time effects on the entity.
    @Override
    public void initialize(EntityLiving entity) {
        entity.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).applyModifier(new AttributeModifier(UUID.randomUUID(), "Thrower knockback resistance", 1.0, 0));
        entity.getEntityAttribute(SharedMonsterAttributes.maxHealth).applyModifier(new AttributeModifier(UUID.randomUUID(), "Thrower health boost", 10.0, 0));
        entity.setHealth(entity.getHealth() + 10.0F);
    }

    // Returns whether the AI should begin execution.
    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = this.theEntity.getAttackTarget();
        if (target == null || this.theEntity.ridingEntity != null)
            return false;
        if (this.theEntity.riddenByEntity != null)
            return true;
        if (this.theEntity.getRNG().nextInt(20) == 0)
            return this.findThrowTarget();
        return false;
    }

    // Called once when the AI begins execution.
    @Override
    public void startExecuting() {
        if (this.throwTarget != null) {
            this.theEntity.getNavigator().tryMoveToEntityLiving(this.throwTarget, 1.5);
        }
        else {
            this.theEntity.getNavigator().tryMoveToEntityLiving(this.theEntity.getAttackTarget(), 1.2);
        }
    }

    // Returns whether an in-progress EntityAIBase should continue executing
    @Override
    public boolean continueExecuting() {
        return this.theEntity.getAttackTarget() != null && this.theEntity.ridingEntity == null && (this.throwTarget != null && this.throwTarget.isEntityAlive() || this.theEntity.riddenByEntity != null);
    }

    // Determine if this AI task is interruptible by a higher priority task.
    @Override
    public boolean isInterruptible() {
        return false;
    }

    // Called every tick while this AI is executing.
    @Override
    public void updateTask() {
        EntityLivingBase target = this.theEntity.getAttackTarget();
        if (this.throwTarget != null) {
            this.theEntity.getLookHelper().setLookPositionWithEntity(this.throwTarget, 30.0F, 30.0F);

            double range = this.theEntity.width * 2.0F * this.theEntity.width * 2.0F + this.throwTarget.width;
            if (this.theEntity.getDistanceSq(this.throwTarget.posX, this.throwTarget.boundingBox.minY, this.throwTarget.posZ) <= range) {
                this.throwTarget.mountEntity(this.theEntity);
                this.throwTarget = null;
                this.theEntity.getNavigator().tryMoveToEntityLiving(target, 1.2);
                this.theEntity.swingItem();
                this.attackTime = 10;
            }
            else {
                if (this.theEntity.getNavigator().noPath()) {
                    this.theEntity.getNavigator().tryMoveToEntityLiving(this.throwTarget, 1.5);
                }
            }
        }
        if (this.theEntity.riddenByEntity != null) {
            this.theEntity.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
            this.attackTime--;

            if (this.attackTime <= 0 && this.theEntity.getRNG().nextInt(10) == 0 && this.theEntity.getDistanceSqToEntity(target) <= 100.0) {
                double dX = target.posX - this.theEntity.posX;
                double dZ = target.posZ - this.theEntity.posZ;
                double dH = Math.sqrt(dX * dX + dZ * dZ);
                Entity entity = this.theEntity.riddenByEntity;
                entity.mountEntity((Entity) null);
                entity.motionX = dX / dH + this.theEntity.motionX * 0.2;
                entity.motionZ = dZ / dH + this.theEntity.motionZ * 0.2;
                entity.motionY = 0.4;
                entity.onGround = false;
                entity.fallDistance = 0.0F;
                this.theEntity.getNavigator().clearPathEntity();
                this.theEntity.swingItem();
            }
            else {
                if (this.theEntity.getNavigator().noPath()) {
                    this.theEntity.getNavigator().tryMoveToEntityLiving(target, 1.2);
                }
            }
        }
        if (++this.giveUpDelay > 400) {
            this.theEntity.getNavigator().clearPathEntity();
            this.throwTarget = null;
        }
    }

    // Resets the task.
    @Override
    public void resetTask() {
        if (this.theEntity.riddenByEntity != null) {
            this.theEntity.riddenByEntity.mountEntity((Entity) null);
        }
        this.theEntity.getNavigator().clearPathEntity();
        this.giveUpDelay = 0;
        this.throwTarget = null;
    }

    // Searches for a nearby mount and targets it. Returns true if one is found.
    private boolean findThrowTarget() {
        EntityLivingBase target = this.theEntity.getAttackTarget();
        double distance = this.theEntity.getDistanceSqToEntity(target);
        if (distance < 9.0)
            return false;

        List list = this.theEntity.worldObj.getEntitiesWithinAABBExcludingEntity(this.theEntity, this.theEntity.boundingBox.expand(16.0, 8.0, 16.0));
        EntityLiving entity;
        double dist;
        for (Object obj : list) {
            if (obj instanceof EntityLiving) {
                entity = (EntityLiving) obj;
                if (!entity.onGround || entity.ridingEntity != null || target != entity.getAttackTarget() || entity.getDistanceSqToEntity(target) <= 36.0) {
                    continue;
                }
                dist = this.theEntity.getDistanceSqToEntity(entity);
                if (dist < distance) {
                    distance = dist;
                    this.throwTarget = entity;
                }
            }
        }
        return this.throwTarget != null;
    }
}