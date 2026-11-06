package com.moakiee.ae2lt.packaged.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.world.item.Items;

class TerminalCoreOrdersTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.initialize(); }

    @Test void existingRequesterBypassesTerminalRoutingAndSubmitsOnce() {
        var requester = proxy(ICraftingRequester.class);
        var calls = new AtomicInteger();
        var result = TerminalCoreOrders.submit(null, proxy(ICraftingPlan.class), requester, actual -> {
            assertSame(requester, actual);
            calls.incrementAndGet();
            return CraftingSubmitResult.CPU_BUSY;
        });
        assertSame(CraftingSubmitResult.CPU_BUSY, result);
        assertEquals(1, calls.get());
    }

    @Test void absentPlanKeepsNativeSubmissionAndFailure() {
        var calls = new AtomicInteger();
        var result = TerminalCoreOrders.submit(null, null, null, requester -> {
            assertNull(requester);
            calls.incrementAndGet();
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        });
        assertSame(CraftingSubmitResult.INCOMPLETE_PLAN, result);
        assertEquals(1, calls.get());
    }

    @Test void simulationNeverReservesAProviderOrReadsFurtherPlanState() {
        var plan = (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                new Class<?>[]{ICraftingPlan.class}, (object, method, args) -> {
                    if (method.getName().equals("simulation")) return true;
                    throw new AssertionError("Unexpected plan read: " + method);
                });
        assertEquals("", TerminalCoreOrders.inspect(null, plan).mode());
        assertNull(TerminalCoreOrders.inspect(null, plan).provider());
    }

    @Test void missingIngredientsStayWithNativeConfirmationInsteadOfReservingAProvider() {
        var missing = new KeyCounter();
        missing.add(AEItemKey.of(Items.IRON_INGOT), 1);
        var plan = (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                new Class<?>[]{ICraftingPlan.class}, (object, method, args) -> switch (method.getName()) {
                    case "simulation" -> false;
                    case "missingItems" -> missing;
                    default -> throw new AssertionError("Unexpected plan read: " + method);
                });
        var decision = TerminalCoreOrders.inspect(null, plan);
        assertEquals("", decision.mode());
        assertFalse(decision.blocked());
        assertNull(decision.provider());
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> { throw new AssertionError("Unexpected call: " + method); }));
    }
}
