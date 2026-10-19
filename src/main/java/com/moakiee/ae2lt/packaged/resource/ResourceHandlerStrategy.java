package com.moakiee.ae2lt.packaged.resource;

import java.util.ArrayList;
import java.util.Set;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;
import appeng.parts.automation.HandlerStrategy;

/** Uses AE2's standard import/export transaction and power accounting. */
final class ResourceHandlerStrategy extends HandlerStrategy<MEStorage, GenericStack> {
    ResourceHandlerStrategy() { super(PackagedResourceKeyType.INSTANCE); }
    @Override public GenericStack getStack(AEKey key, long amount) {
        return isSupported(key) && amount > 0 ? new GenericStack(key, amount) : null;
    }
    @Override public long insert(MEStorage storage, AEKey key, long amount, Actionable mode) {
        return isSupported(key) && amount > 0 ? storage.insert(key, amount, mode, IActionSource.empty()) : 0;
    }
    @Override public ExternalStorageFacade getFacade(MEStorage storage) {
        var snapshot = new ArrayList<GenericStack>();
        for (var entry : storage.getAvailableStacks()) {
            if (isSupported(entry.getKey()) && entry.getLongValue() > 0) snapshot.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return new ExternalStorageFacade() {
            @Override public int getSlots() { return snapshot.size(); }
            @Override public GenericStack getStackInSlot(int slot) { return snapshot.get(slot); }
            @Override public AEKeyType getKeyType() { return PackagedResourceKeyType.INSTANCE; }
            @Override protected int insertExternal(AEKey key, int amount, Actionable mode) {
                return (int) ResourceHandlerStrategy.this.insert(storage, key, amount, mode);
            }
            @Override protected int extractExternal(AEKey key, int amount, Actionable mode) {
                return isSupported(key) && amount > 0 ? (int) storage.extract(key, amount, mode, IActionSource.empty()) : 0;
            }
            @Override public boolean containsAnyFuzzy(Set<AEKey> keys) {
                return snapshot.stream().anyMatch(stack -> keys.contains(stack.what()));
            }
        };
    }
}
