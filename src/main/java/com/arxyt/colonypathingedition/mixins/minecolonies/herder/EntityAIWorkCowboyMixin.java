package com.arxyt.colonypathingedition.mixins.minecolonies.herder;

import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCowboy;
import com.minecolonies.core.colony.jobs.JobCowboy;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkCowboy;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

import java.util.Collections;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.MILKING_ATTEMPTS;

@Mixin(value = EntityAIWorkCowboy.class, remap = false)
public abstract class EntityAIWorkCowboyMixin extends AbstractEntityAIHerder<JobCowboy, BuildingCowboy> {

    @Shadow(remap = false) @Final private static VisibleCitizenStatus HERD_COW;

    @Shadow(remap = false) private int milkCoolDown;

    @Shadow(remap = false) @Final private static int MILK_COOL_DOWN;

    @Shadow(remap = false) private int stewCoolDown;

    public EntityAIWorkCowboyMixin(@NotNull final JobCowboy job)
    {
        super(job);
    }

    /**
     * @author ARxyt
     * @reason Fix milking priority being suppressed by breeding/butchering
     * 修复挤奶优先级被繁殖/屠宰压制的问题
     */
    @Overwrite(remap = false)
    public IAIState decideWhatToDo()
    {
        // 先处理冷却
        if (milkCoolDown > 0)
        {
            --milkCoolDown;
        }
        if (stewCoolDown > 0)
        {
            --stewCoolDown;
        }

        // 挤奶和盛汤有更高的优先级（在繁殖/屠宰之前）
        if (building != null)
        {
            final BuildingCowboy.HerdingModule module = building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class);

            // 检查是否有可挤奶的牛
            if (milkCoolDown == 0 && module.canTryToMilk())
            {
                final boolean hasCow = !searchForAnimals(a -> a instanceof Cow && !a.isBaby()).isEmpty();
                if (hasCow)
                {
                    return COWBOY_MILK;
                }
            }

            // 检查是否有可盛汤的蘑菇牛
            if (stewCoolDown == 0 && module.canTryToStew())
            {
                final boolean hasMoosh = !searchForAnimals(a -> a instanceof MushroomCow && !a.isBaby()).isEmpty();
                if (hasMoosh)
                {
                    return COWBOY_STEW;
                }
            }
        }

        // 其他操作由父类决策（繁殖、屠宰、饲养）
        return super.decideWhatToDo();
    }

    /**
     * @author ARxyt
     * @reason Fix milking priority and item stack count handling
     * 修复挤奶优先级和物品数量处理
     */
    @Overwrite(remap = false)
    private IAIState milkCows()
    {
        worker.getCitizenData().setVisibleStatus(HERD_COW);

        // 准备输入物品：总是使用 count=1 的栈
        final ItemStack milkInputItem = building.getMilkInputItem().copy();
        milkInputItem.setCount(1);

        if (!worker.getCitizenInventoryHandler().hasItemInInventory(milkInputItem.getItem()))
        {
            if (InventoryUtils.hasBuildingEnoughElseCount(building, new ItemStorage(milkInputItem), 1) > 0
                    && walkToBuilding())
            {
                checkAndTransferFromHut(milkInputItem);
            }
            else
            {
                milkCoolDown = MILK_COOL_DOWN;
                return DECIDE;
            }
        }

        // 寻找可挤奶的牛
        final Cow cow = searchForAnimals(a -> a instanceof Cow && !a.isBaby()).stream()
                .map(a -> (Cow) a).findFirst().orElse(null);

        if (cow == null)
        {
            milkCoolDown = MILK_COOL_DOWN;
            return DECIDE;
        }

        walkingToAnimal(cow);

        // 装备物品并执行挤奶
        if (equipItem(InteractionHand.MAIN_HAND, Collections.singletonList(new ItemStorage(milkInputItem))))
        {
            // 准备输出物品
            final ItemStack milkOutputItem = building.getMilkOutputItem().copy();
            milkOutputItem.setCount(1);

            // 尝试添加输出物品
            if (InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), milkOutputItem))
            {
                building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class).onMilked();
                CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, getItemSlot(milkOutputItem.getItem()));
                // 移除一个输入物品
                InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), milkInputItem);

                incrementActionsDoneAndDecSaturation();
                StatsUtil.trackStat(building, MILKING_ATTEMPTS, 1);
                worker.getCitizenExperienceHandler().addExperience(1.0);

                // 检查是否还可以继续挤奶
                if (building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class).canTryToMilk())
                {
                    setDelay(10);
                    return COWBOY_MILK;
                }
                else
                {
                    return INVENTORY_FULL;
                }
            }
            else
            {
                // 输出物品插入失败，背包满
                return INVENTORY_FULL;
            }
        }
        return DECIDE;
    }

    /**
     * @author ARxyt
     * @reason Fix mooshroom stewing priority and item stack count handling
     * 修复蘑菇牛盛汤优先级和物品数量处理
     */
    @Overwrite(remap = false)
    private IAIState milkMooshrooms()
    {
        worker.getCitizenData().setVisibleStatus(HERD_COW);

        // 准备输入物品：总是使用 count=1 的栈
        final ItemStack bowlStack = new ItemStack(Items.BOWL, 1);

        if (!worker.getCitizenInventoryHandler().hasItemInInventory(Items.BOWL))
        {
            if (InventoryUtils.hasBuildingEnoughElseCount(building, new ItemStorage(bowlStack), 1) > 0
                    && walkToBuilding())
            {
                checkAndTransferFromHut(bowlStack);
            }
            else
            {
                stewCoolDown = MILK_COOL_DOWN;
                return DECIDE;
            }
        }

        // 寻找可盛汤的蘑菇牛（幼牛除外）
        final MushroomCow mooshroom = searchForAnimals(a -> a instanceof MushroomCow && !a.isBaby()).stream()
                .map(a -> (MushroomCow) a).findFirst().orElse(null);

        if (mooshroom == null)
        {
            stewCoolDown = MILK_COOL_DOWN;
            return DECIDE;
        }

        walkingToAnimal(mooshroom);

        // 装备碗并执行盛汤
        if (equipItem(InteractionHand.MAIN_HAND, Collections.singletonList(new ItemStorage(Items.BOWL))))
        {
            final FakePlayer fakePlayer = FakePlayerFactory.getMinecraft((ServerLevel) worker.level());
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 1));

            if (mooshroom.mobInteract(fakePlayer, InteractionHand.MAIN_HAND).equals(InteractionResult.CONSUME))
            {
                // 获取输出物品（通常是蘑菇煲汤）
                ItemStack stewOutput = fakePlayer.getMainHandItem().copy();
                stewOutput.setCount(1);

                if (InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), stewOutput))
                {
                    building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class).onStewed();
                    CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, getItemSlot(stewOutput.getItem()));
                    // 移除一个碗
                    InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), bowlStack);

                    incrementActionsDoneAndDecSaturation();
                    StatsUtil.trackStat(building, MILKING_ATTEMPTS, 1);
                    worker.getCitizenExperienceHandler().addExperience(1.0);

                    // 检查是否还可以继续盛汤
                    if (building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class).canTryToStew())
                    {
                        setDelay(10);
                        return COWBOY_STEW;
                    }
                    else
                    {
                        return INVENTORY_FULL;
                    }
                }
                else
                {
                    // 输出物品插入失败，背包满
                    return INVENTORY_FULL;
                }
            }
            else
            {
                // 交互失败
                return DECIDE;
            }
        }
        return DECIDE;
    }
}
