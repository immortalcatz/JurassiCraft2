package org.jurassicraft.server.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.Vec3d;
import org.jurassicraft.server.entity.DinosaurEntity;

public class DinosaurWanderEntityAI extends EntityAIBase {
    private DinosaurEntity entity;
    private double xPosition;
    private double yPosition;
    private double zPosition;
    private double speed;
    private int executionChance;
    private boolean mustUpdate;

    public DinosaurWanderEntityAI(DinosaurEntity creatureIn, double speedIn) {
        this(creatureIn, speedIn, 120);
    }

    public DinosaurWanderEntityAI(DinosaurEntity creatureIn, double speedIn, int chance) {
        this.entity = creatureIn;
        this.speed = speedIn;
        this.executionChance = chance;
        this.setMutexBits(Mutex.MOVEMENT);
    }

    @Override
    public boolean shouldExecute() {
        if (!this.mustUpdate) {
            if (this.entity.getRNG().nextInt(this.executionChance) != 0) {
                return false;
            }
        }

        if (this.entity.getNavigator().noPath()) {
            Vec3d wanderPosition = RandomPositionGenerator.findRandomTarget(this.entity, 10, 7);

            if (wanderPosition != null) {
                this.xPosition = wanderPosition.xCoord;
                this.yPosition = wanderPosition.yCoord;
                this.zPosition = wanderPosition.zCoord;
                this.mustUpdate = false;

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean continueExecuting() {
        return !this.entity.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        this.entity.getNavigator().tryMoveToXYZ(this.xPosition, this.yPosition, this.zPosition, this.speed);
    }

    public void makeUpdate() {
        this.mustUpdate = true;
    }

    public void setExecutionChance(int chance) {
        this.executionChance = chance;
    }
}