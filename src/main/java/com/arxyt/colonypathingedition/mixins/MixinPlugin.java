package com.arxyt.colonypathingedition.mixins;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MixinPlugin implements IMixinConfigPlugin {

    private static final Map<String, ClassNode> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_CLASSES = ConcurrentHashMap.newKeySet();

    private static ClassNode tryGetClassNode(final String internalName) {
        if (MISSING_CLASSES.contains(internalName)) {
            return null;
        }
        final ClassNode cached = CLASS_CACHE.get(internalName);
        if (cached != null) {
            return cached;
        }
        try {
            final ClassNode classNode = MixinService.getService().getBytecodeProvider().getClassNode(internalName);
            CLASS_CACHE.put(internalName, classNode);
            return classNode;
        } catch (final Throwable ignored) {
            MISSING_CLASSES.add(internalName);
            return null;
        }
    }

    private static boolean hasMethod(final String internalOwner, final String name) {
        final ClassNode classNode = tryGetClassNode(internalOwner);
        if (classNode == null) {
            return false;
        }
        for (final MethodNode method : classNode.methods) {
            if (name.equals(method.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(final String internalOwner, final String name, final String desc) {
        final ClassNode classNode = tryGetClassNode(internalOwner);
        if (classNode == null) {
            return false;
        }
        for (final MethodNode method : classNode.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        switch (mixinClassName) {
            case "com.arxyt.colonypathingedition.mixins.minecolonies.farm.FarmFieldsModuleWindowMixin":
                return tryGetClassNode("com/minecolonies/core/client/gui/modules/building/FarmFieldsModuleWindow") != null;
            case "com.arxyt.colonypathingedition.mixins.minecolonies.farm.FarmFieldsModuleWindowMixin_Legacy":
                return tryGetClassNode("com/minecolonies/core/client/gui/modules/FarmFieldsModuleWindow") != null;

            case "com.arxyt.colonypathingedition.mixins.minecolonies.linkage.ItemListModuleWindowMixin":
                return hasMethod("com/minecolonies/core/client/gui/modules/building/ItemListModuleWindow", "lambda$updateResources$1");
            case "com.arxyt.colonypathingedition.mixins.minecolonies.linkage.ItemListModuleWindowMixin_Legacy":
                return hasMethod("com/minecolonies/core/client/gui/modules/ItemListModuleWindow", "lambda$updateResources$3");

            case "com.arxyt.colonypathingedition.mixins.minecolonies.linkage.RestaurantMenuModuleWindowMixin":
                return hasMethod("com/minecolonies/core/client/gui/modules/building/RestaurantMenuModuleWindow", "lambda$updateResources$1");
            case "com.arxyt.colonypathingedition.mixins.minecolonies.linkage.RestaurantMenuModuleWindowMixin_Legacy":
                return hasMethod("com/minecolonies/core/client/gui/modules/RestaurantMenuModuleWindow", "lambda$updateResources$1");

            case "com.arxyt.colonypathingedition.mixins.minecolonies.linkage.WindowPostBoxMixin":
                return hasMethod("com/minecolonies/core/client/gui/WindowPostBoxMain", "lambda$updateResources$3");
            case "com.arxyt.colonypathingedition.mixins.minecolonies.linkage.WindowPostBoxMixin_Legacy":
                return hasMethod("com/minecolonies/core/client/gui/WindowPostBox", "lambda$updateResources$1");

            case "com.arxyt.colonypathingedition.mixins.minecolonies.pathfinding.AbstractPathJobMixin":
                return shouldApplyAbstractPathJobMixin();

            default:
                return true;
        }
    }

    private static boolean shouldApplyAbstractPathJobMixin() {
        final String owner = "com/minecolonies/core/entity/pathfinding/pathjobs/AbstractPathJob";

        return hasMethod(owner, "getGroundHeight", "(Lcom/minecolonies/core/entity/pathfinding/MNode;III)I")
                && hasMethod(owner, "createNode", "(Lcom/minecolonies/core/entity/pathfinding/MNode;IIIDD)Lcom/minecolonies/core/entity/pathfinding/MNode;")
                && hasMethod(owner, "calculateSwimming", "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lcom/minecolonies/core/entity/pathfinding/MNode;)Z")
                && hasMethod(owner, "modifyCost", "(DLcom/minecolonies/core/entity/pathfinding/MNode;ZZIIILnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)D")
                && hasMethod(owner, "computeHeuristic", "(III)D");
    }

    @Override
    public void onLoad(final String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }
}

