package io.github.rush.game;

import io.github.rush.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LoadingBar {
    private static final long TICK_INTERVAL = 3L;
    private static final int DOT_CYCLE = 3;
    private static final long CLEAR_DELAY = 40L;
    private static final String BAR_CHAR = "█";
    private static final int MAX_NULL_TICKS = 5;

    private final UUID viewerId;
    private final AtomicReference<String> label;
    private final AtomicInteger step;
    private final AtomicInteger dotTick = new AtomicInteger();
    private final AtomicInteger nullTickCount = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final BukkitTask task;

    LoadingBar(Main plugin, Player viewer, int totalSteps, String initialLabel) {
        this.viewerId = viewer.getUniqueId();
        this.label = new AtomicReference<>(initialLabel);
        this.step = new AtomicInteger(0);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final Player p = Bukkit.getPlayer(viewerId);

            if (p != null) {
                nullTickCount.set(0);

                final String message = label.get()
                        + ".".repeat((dotTick.getAndIncrement() / DOT_CYCLE) % DOT_CYCLE + 1);

                p.sendActionBar(buildComponent(message, step.get(), totalSteps));
            } else if (nullTickCount.incrementAndGet() >= MAX_NULL_TICKS)
                cancel();
        }, 0L, TICK_INTERVAL);
    }

    private static Component buildComponent(String message, int currentStep, int totalSteps) {
        Component bar = Component.text("[", NamedTextColor.GRAY);

        for (int i = 0; i < totalSteps; i++) {
            if (i < currentStep)
                bar = bar.append(Component.text(BAR_CHAR, NamedTextColor.GREEN));
            else if (i == currentStep)
                bar = bar.append(Component.text(BAR_CHAR, NamedTextColor.YELLOW));
            else
                bar = bar.append(Component.text(BAR_CHAR, NamedTextColor.DARK_GRAY));
        }

        bar = bar.append(Component.text("] ", NamedTextColor.GRAY));
        bar = bar.append(Component.text(message, NamedTextColor.WHITE));

        return bar;
    }

    void update(String label, int step) {
        this.label.set(label);
        this.step.set(step);
    }

    void cancel() {
        if (cancelled.compareAndSet(false, true))
            task.cancel();
    }

    void stop(Main plugin) {
        cancel();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final Player p = Bukkit.getPlayer(viewerId);
            if (p != null && p.isOnline())
                p.sendActionBar(Component.empty());
        }, CLEAR_DELAY);
    }
}
