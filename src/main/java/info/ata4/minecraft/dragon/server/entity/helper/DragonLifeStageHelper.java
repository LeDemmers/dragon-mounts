/*
 ** 2013 October 27
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.minecraft.dragon.server.entity.helper;

import info.ata4.minecraft.dragon.server.entity.EntityTameableDragon;
import static info.ata4.minecraft.dragon.server.entity.helper.DragonLifeStage.*;

import info.ata4.minecraft.dragon.server.util.ClientServerSynchronisedTickCount;
import net.minecraft.block.Block;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class DragonLifeStageHelper extends DragonHelper {
    
    private static final Logger L = LogManager.getLogger();
    
    private DragonLifeStage lifeStagePrev;
    private DragonScaleModifier scaleModifier = new DragonScaleModifier();
    private int eggWiggleX;
    private int eggWiggleZ;
    private int TICKS_SINCE_CREATION_UPDATE_INTERVAL = 100;

    // the ticks since creation is used to control the dragon's life stage.  It is only updated by the server occasionally.
    // the client keeps a cached copy of it and uses client ticks to interpolate in the gaps.
    // when the watcher is updated from the server, the client will tick it faster or slower to resynchronise

    private int dataWatcherIndexTicksSinceCreation;
    private int ticksSinceCreationServer;
    private ClientServerSynchronisedTickCount ticksSinceCreationClient;

    public DragonLifeStageHelper(EntityTameableDragon dragon, int dataWatcherIndex) {
        super(dragon);
        dataWatcherIndexTicksSinceCreation = dataWatcherIndex;

        ticksSinceCreationServer = 0;
        dataWatcher.addObject(dataWatcherIndexTicksSinceCreation, ticksSinceCreationServer);
        ticksSinceCreationClient = new ClientServerSynchronisedTickCount(TICKS_SINCE_CREATION_UPDATE_INTERVAL);
        ticksSinceCreationClient.reset(ticksSinceCreationServer);
    }
    
    @Override
    public void applyEntityAttributes() {
        scaleModifier.setScale(getScale());

        dragon.getEntityAttribute(SharedMonsterAttributes.maxHealth).applyModifier(scaleModifier);
        dragon.getEntityAttribute(SharedMonsterAttributes.attackDamage).applyModifier(scaleModifier);
    }
    
    /**
     * Generates some egg shell particles and a breaking sound.
     */
    public void playEggCrackEffect() {
        int bx = (int) Math.round(dragon.posX - 0.5);
        int by = (int) Math.round(dragon.posY);
        int bz = (int) Math.round(dragon.posZ - 0.5);
        dragon.worldObj.playAuxSFX(2001, new BlockPos(bx, by, bz), Block.getIdFromBlock(Blocks.dragon_egg));
    }
    
    public int getEggWiggleX() {
        return eggWiggleX;
    }

    public int getEggWiggleZ() {
        return eggWiggleZ;
    }
    
    /**
     * Returns the current life stage of the dragon.
     * 
     * @return current life stage
     */
    public DragonLifeStage getLifeStage() {
        TODO fix int age = dragon.getGrowingAge();
        if (age >= ADULT.ageLimit) {
            return ADULT;
        } else if (age >= JUVENILE.ageLimit) {
            return JUVENILE;
        } else if (age >= HATCHLING.ageLimit) {
            return HATCHLING;
        } else {
            return EGG;
        }
    }

    private int getTicksSinceCreationClient()
    {
      if (!dragon.worldObj.isRemote)  {
        return this.dataWatcher.getWatchableObjectByte(12)
      }

    }

    /**
     * Returns the size multiplier for the current age.
     * 
     * @return size
     */
    public float getScale() {
        // constant size for egg stage
        if (isEgg()) {
            return 0.9f / EntityTameableDragon.BASE_WIDTH;
        }
        
        // use relative distance from the current age to the egg age as scale
      TODOFIX  return 1 - (dragon.getGrowingAge() / (float) DragonLifeStage.EGG.ageLimit);
    }
    
    /**
     * Transforms the dragon to an egg (item form)
     */
    public void transformToEgg() {
        if (dragon.getHealth() <= 0) {
            // no can do
            return;
        }
        
        L.debug("transforming to egg");

        float volume = 1;
        float pitch = 0.5f + (0.5f - rand.nextFloat()) * 0.1f;
        dragon.worldObj.playSoundAtEntity(dragon, "mob.endermen.portal", volume, pitch);
        
        if (dragon.isSaddled()) {
            dragon.dropItem(Items.saddle, 1);
        }
        
        dragon.entityDropItem(new ItemStack(Blocks.dragon_egg), 0);
        dragon.setDead();
    }
    
    /**
     * Sets a new life stage for the dragon.
     * 
     * @param lifeStage
     */
    public final void setLifeStage(DragonLifeStage lifeStage) {
        L.trace("setLifeStage({})", lifeStage);
        dragon.setGrowingAge(lifeStage.ageLimit);
        updateLifeStage();
    }
    
    /**
     * Called when the dragon enters a new life stage.
     */ 
    private void onNewLifeStage(DragonLifeStage lifeStage, DragonLifeStage prevLifeStage) {
        L.trace("onNewLifeStage({},{})", prevLifeStage, lifeStage);
        
        if (dragon.isClient()) {
            if (prevLifeStage != null && prevLifeStage == EGG && lifeStage == HATCHLING) {
                playEggCrackEffect();
            }
        } else {
            // eggs and hatchlings can't fly
            dragon.setCanFly(lifeStage != EGG && lifeStage != HATCHLING);
            
            // only hatchlings are small enough for doors
            // (eggs don't move on their own anyway and are ignored)
            // TODO: removed in 1.8?
//            dragon.getNavigator().setEnterDoors(lifeStage == HATCHLING);
            
            // update AI states so the egg won't move
            if (lifeStage == EGG) {
                // TODO: removed in 1.8?
//                dragon.setPathToEntity(null);
                dragon.setAttackTarget(null);
            }
            
            // update attribute modifier
            IAttributeInstance healthAttrib = dragon.getEntityAttribute(SharedMonsterAttributes.maxHealth);
            IAttributeInstance damageAttrib = dragon.getEntityAttribute(SharedMonsterAttributes.attackDamage);

            // remove old size modifiers
            healthAttrib.removeModifier(scaleModifier);
            damageAttrib.removeModifier(scaleModifier);

            // update modifier
            scaleModifier.setScale(getScale());

            // set new size modifiers
            healthAttrib.applyModifier(scaleModifier);
            damageAttrib.applyModifier(scaleModifier);

            // heal dragon to updated full health
            dragon.setHealth(dragon.getMaxHealth());
        }
    }

    @Override
    public void onLivingUpdate() {
        // testing code
//        if (dragon.isServer()) {
//            dragon.setGrowingAge((int) ((((Math.sin(Math.toRadians(dragon.ticksExisted))) + 1) * 0.5) * EGG.ageLimit));
//        }

        // if the dragon is not an adult, update its growth ticks
        if (!dragon.worldObj.isRemote) {
          if (getLifeStage() != ADULT) {
            ++ticksSinceCreationServer;
            if (ticksSinceCreationServer % TICKS_SINCE_CREATION_UPDATE_INTERVAL == 0){
              dataWatcher.updateObject(dataWatcherIndexTicksSinceCreation, ticksSinceCreationServer);
            }
          }
        } else {
            ticksSinceCreationClient.updateFromServer(dataWatcher.getWatchableObjectInt(dataWatcherIndexTicksSinceCreation));
            if (getLifeStage() != ADULT) {
                ticksSinceCreationClient.tick();
            }
        }

        updateLifeStage();
        updateEgg();
        updateScale();
    }
    
    private void updateLifeStage() {
        // trigger event when a new life stage was reached
        DragonLifeStage lifeStage = getLifeStage();
        if (lifeStagePrev != lifeStage) {
            onNewLifeStage(lifeStage, lifeStagePrev);
            lifeStagePrev = lifeStage;
        }
    }
    
    private void updateEgg() {
        if (!isEgg()) {
            return;
        }
        
        TODO FIX int age = dragon.getGrowingAge();

        // animate egg wiggle based on the time the eggs take to hatch
        int eggAge = DragonLifeStage.EGG.ageLimit;
        int hatchAge = DragonLifeStage.HATCHLING.ageLimit;
        float chance = (age - eggAge) / (float) (hatchAge - eggAge);

        // wait until the egg is nearly hatched
        if (chance > 0.66f) {
            // reduce chance so it can run every tick
            chance /= 60;

            if (eggWiggleX > 0) {
                eggWiggleX--;
            } else if (rand.nextFloat() < chance) {
                eggWiggleX = rand.nextBoolean() ? 10 : 20;
                playEggCrackEffect();
            }

            if (eggWiggleZ > 0) {
                eggWiggleZ--;
            } else if (rand.nextFloat() < chance) {
                eggWiggleZ = rand.nextBoolean() ? 10 : 20;
                playEggCrackEffect();
            }
        }

        // spawn generic particles
        double px = dragon.posX + (rand.nextDouble() - 0.5);
        double py = dragon.posY + (rand.nextDouble() - 0.5);
        double pz = dragon.posZ + (rand.nextDouble() - 0.5);
        double ox = (rand.nextDouble() - 0.5) * 2;
        double oy = (rand.nextDouble() - 0.5) * 2;
        double oz = (rand.nextDouble() - 0.5) * 2;
        dragon.worldObj.spawnParticle(EnumParticleTypes.PORTAL, px, py, pz, ox, oy, oz);
    }

    private void updateScale() {
        dragon.setScalePublic(getScale());
    }
    
    @Override
    public void onDeath() {
        if (dragon.isClient() && isEgg()) {
            playEggCrackEffect();
        }
    }
    
    public boolean isEgg() {
        return getLifeStage() == EGG;
    }
    
    public boolean isHatchling() {
        return getLifeStage() == HATCHLING;
    }
    
    public boolean isJuvenile() {
        return getLifeStage() == JUVENILE;
    }
    
    public boolean isAdult() {
        return getLifeStage() == ADULT;
    }
}
