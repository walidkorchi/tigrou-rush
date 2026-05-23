package io.github.rush.game;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class HostTransfer {

    private HostTransfer() {
    }

    public static @Nullable UUID nextHost(List<UUID> joinOrder, UUID disconnected, Predicate<UUID> isOnline) {
        for (UUID candidate : joinOrder) {
            if (!candidate.equals(disconnected) && isOnline.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
