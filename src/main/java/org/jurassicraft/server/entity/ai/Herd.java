package org.jurassicraft.server.entity.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jurassicraft.server.dinosaur.Dinosaur;
import org.jurassicraft.server.entity.DinosaurEntity;
import org.jurassicraft.server.util.GameRuleHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class Herd implements Iterable<DinosaurEntity> {
    public List<DinosaurEntity> members = new LinkedList<>();
    public DinosaurEntity leader;

    private Vec3d center;

    private float moveX;
    private float moveZ;

    public State state = State.STATIC;
    public int stateTicks;

    private Random random = new Random();

    public List<EntityLivingBase> enemies = new ArrayList<>();

    public boolean fleeing;

    private Dinosaur herdType;

    public Herd(DinosaurEntity leader) {
        this.herdType = leader.getDinosaur();
        this.members.add(leader);
        this.leader = leader;
        this.resetStateTicks();
    }

    public void onUpdate() {
        if (this.leader == null || this.leader.isCarcass() || this.leader.isDead) {
            this.updateLeader();
        }

        if (this.stateTicks > 0) {
            this.stateTicks--;
        } else {
            this.state = this.state == State.MOVING ? State.STATIC : State.MOVING;
            this.resetStateTicks();
            this.enemies.clear();
            this.fleeing = false;
        }

        if (this.leader != null) {
            if (this.leader.shouldSleep()) {
                this.state = State.STATIC;
                this.resetStateTicks();
            }

            this.center = this.getCenterPosition();

            if (this.enemies.size() > 0) {
                if (this.fleeing) {
                    this.state = State.MOVING;

                    float angle = 0.0F;

                    for (EntityLivingBase attacker : this.enemies) {
                        angle += Math.atan2(this.center.zCoord - attacker.posZ, this.center.xCoord - attacker.posX);
                    }

                    angle /= this.enemies.size();

                    this.moveX = (float) -Math.cos(angle);
                    this.moveZ = (float) Math.sin(angle);

                    this.normalizeMovement();
                } else {
                    this.state = State.STATIC;
                }
            } else {
                this.fleeing = false;
            }

            List<DinosaurEntity> remove = new LinkedList<>();

            for (DinosaurEntity entity : this) {
                double distance = entity.getDistanceSq(this.center.xCoord, this.center.yCoord, this.center.zCoord);

                if (distance > 2048) {
                    remove.add(entity);
                }
            }

            for (DinosaurEntity entity : remove) {
                this.members.remove(entity);
                entity.herd = null;

                if (entity == this.leader) {
                    this.updateLeader();
                }
            }

            if (this.leader == null) {
                return;
            }

            for (DinosaurEntity entity : this) {
                if (this.enemies.size() == 0 || this.fleeing) {
                    if (!(entity.getMetabolism().isHungry() || entity.getMetabolism().isThirsty()) && !entity.isMovementBlocked() && !entity.isInWater() && (this.fleeing || entity.getNavigator().noPath()) && (this.state == State.MOVING || this.random.nextInt(50) == 0)) {
                        float entityMoveX = this.moveX * 2.0F;
                        float entityMoveZ = this.moveZ * 2.0F;

                        float centerDistance = (float) Math.abs(entity.getDistance(this.center.xCoord, entity.posY, this.center.zCoord));

                        if (this.fleeing) {
                            centerDistance *= 4.0F;
                        }

                        if (centerDistance > 0) {
                            entityMoveX += (this.center.xCoord - entity.posX) / centerDistance;
                            entityMoveZ += (this.center.zCoord - entity.posZ) / centerDistance;
                        }

                        for (DinosaurEntity other : this) {
                            if (other != entity) {
                                float distance = Math.abs(entity.getDistanceToEntity(other));

                                float separation = (entity.width * 1.5F) + 1.5F;

                                if (distance < separation) {
                                    float scale = distance / separation;
                                    entityMoveX += (entity.posX - other.posX) / scale;
                                    entityMoveZ += (entity.posZ - other.posZ) / scale;
                                }
                            }
                        }

                        double navigateX = entity.posX + entityMoveX;
                        double navigateZ = entity.posZ + entityMoveZ;

                        double speed = this.state == State.STATIC ? 0.8 : entity.getDinosaur().getFlockSpeed();

                        if (this.fleeing) {
                            if (entity.getDinosaur().getAttackSpeed() > speed) {
                                speed = entity.getDinosaur().getAttackSpeed();
                            }
                        }

                        if (entity.getAttackTarget() == null && this.members.size() > 1) {
                            entity.getNavigator().tryMoveToXYZ(navigateX, entity.worldObj.getHeight(new BlockPos(navigateX, 0, navigateZ)).getY() + 1, navigateZ, speed);
                        }
                    }
                } else if (!this.fleeing && (entity.getAttackTarget() == null || this.random.nextInt(20) == 0) && this.enemies.size() > 0) {
                    if (entity.getAgePercentage() > 50) {
                        entity.setAttackTarget(this.enemies.get(this.random.nextInt(this.enemies.size())));
                    }
                }
            }

            List<EntityLivingBase> invalidEnemies = new LinkedList<>();

            for (EntityLivingBase enemy : this.enemies) {
                if (enemy.isDead || (enemy instanceof DinosaurEntity && ((DinosaurEntity) enemy).isCarcass()) || (enemy instanceof EntityPlayer && ((EntityPlayer) enemy).capabilities.isCreativeMode) || enemy.getDistanceSq(this.center.xCoord, this.center.yCoord, this.center.zCoord) > 1024 || this.members.contains(enemy)) {
                    invalidEnemies.add(enemy);
                }
            }

            this.enemies.removeAll(invalidEnemies);

            if (this.enemies.size() == 0) {
                this.fleeing = false;
                this.state = State.STATIC;
            }

            if (this.state == State.STATIC) {
                this.moveX = 0.0F;
                this.moveZ = 0.0F;
            } else {
                this.moveX += (this.random.nextFloat() - 0.5F) * 0.1F;
                this.moveZ += (this.random.nextFloat() - 0.5F) * 0.1F;

                this.normalizeMovement();
            }

            this.refreshMembers();
        }
    }

    private void resetStateTicks() {
        this.stateTicks = this.random.nextInt(this.state == State.MOVING ? 2000 : 4000) + 1000;
    }

    public void refreshMembers() {
        List<DinosaurEntity> remove = new LinkedList<>();

        for (DinosaurEntity entity : this) {
            if (entity.isCarcass() || entity.isDead || entity.getMetabolism().isStarving() || entity.getMetabolism().isDehydrated()) {
                remove.add(entity);
            }
        }

        this.members.removeAll(remove);

        AxisAlignedBB searchBounds = new AxisAlignedBB(this.center.xCoord - 16, this.center.yCoord - 5, this.center.zCoord - 16, this.center.xCoord + 16, this.center.yCoord + 5, this.center.zCoord + 16);

        List<Herd> otherHerds = new LinkedList<>();

        for (DinosaurEntity entity : this.leader.worldObj.getEntitiesWithinAABB(DinosaurEntity.class, searchBounds)) {
            if (this.leader.getClass().isAssignableFrom(entity.getClass())) {
                if (!entity.isCarcass() && !entity.isDead && !(entity.getMetabolism().isStarving() || entity.getMetabolism().isDehydrated())) {
                    Herd otherHerd = entity.herd;
                    if (otherHerd == null) {
                        if (this.size() >= this.herdType.getMaxHerdSize()) {
                            if (GameRuleHandler.KILL_HERD_OUTCAST.getBoolean(this.leader.worldObj) && this.herdType.getDiet().isCarnivorous() && !this.enemies.contains(entity)) {
                                this.enemies.add(entity);
                            }
                            return;
                        }
                        this.addMember(entity);
                    } else if (otherHerd != this && !otherHerds.contains(otherHerd)) {
                        otherHerds.add(otherHerd);
                    }
                }
            }
        }

        for (Herd otherHerd : otherHerds) {
            int originalSize = this.size();

            if (otherHerd.size() <= originalSize && otherHerd.size() + originalSize < this.herdType.getMaxHerdSize()) {
                for (DinosaurEntity member : otherHerd) {
                    this.members.add(member);
                    member.herd = this;
                }

                otherHerd.disband();
            } else if (originalSize + 1 >= this.herdType.getMaxHerdSize()) {
                if (GameRuleHandler.KILL_HERD_OUTCAST.getBoolean(this.leader.worldObj) && this.herdType.getDiet().isCarnivorous()) {
                    for (DinosaurEntity entity : otherHerd) {
                        if (!this.enemies.contains(entity)) {
                            this.enemies.add(entity);
                        }
                    }
                }
            }
        }
    }

    public void updateLeader() {
        if (this.members.size() > 0) {
            this.leader = this.members.get(new Random().nextInt(this.members.size()));
        } else {
            this.leader = null;
        }
    }

    public Vec3d getCenterPosition() {
        double x = 0.0;
        double z = 0.0;

        int count = 0;

        for (DinosaurEntity member : this.members) {
            if (!member.isCarcass() && !member.isInWater()) {
                x += member.posX;
                z += member.posZ;

                count++;
            }
        }

        x /= count;
        z /= count;

        return new Vec3d(x, this.leader.worldObj.getHeight(new BlockPos(x, 0, z)).getY(), z);
    }

    public void addMember(DinosaurEntity entity) {
        if (entity.herd != null) {
            entity.herd.members.remove(entity);

            if (entity.herd.leader == entity) {
                entity.herd.updateLeader();
            }
        }

        entity.herd = this;
        this.members.add(entity);
    }

    public void disband() {
        this.leader = null;
        this.members.clear();
    }

    public int size() {
        return this.members.size();
    }

    @Override
    public Iterator<DinosaurEntity> iterator() {
        return this.members.iterator();
    }

    public void normalizeMovement() {
        float length = (float) Math.sqrt(Math.pow(this.moveX, 2) + Math.pow(this.moveZ, 2));
        this.moveX = this.moveX / length;
        this.moveZ = this.moveZ / length;
    }

    public boolean shouldDefend(List<EntityLivingBase> entities) {
        return this.getScore(this) + (this.herdType.getAttackBias() * this.members.size()) > this.getScore(entities);
    }

    public double getScore(Iterable<? extends EntityLivingBase> entities) {
        double score = 0.0F;

        for (EntityLivingBase entity : entities) {
            if (entity != null && entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE) != null) {
                score += entity.getHealth() * entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue();
            }
        }

        return score;
    }

    public enum State {
        MOVING,
        STATIC
    }
}