package io.github.rush.replay;

import io.github.rush.utils.ReplayUtils.BlockRestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReplaySeek {

    private ReplaySeek() {}

    /**
     * Returns the block restores needed to seek from {@code fromMs} back to {@code toMs}.
     * Only meaningful when toMs < fromMs (backwards seek). Returns empty list for forward seeks.
     * Results are ordered reverse-chronologically (most recent change first).
     */
    public static List<BlockRestore> computeRestores(List<ReplayAction> frames, long fromMs, long toMs) {
        if (toMs >= fromMs) return List.of();

        final List<BlockRestore> restores = new ArrayList<>();

        for (ReplayAction frame : frames) {
            if (frame instanceof BlockChangeAction b && b.timestamp() > toMs && b.timestamp() <= fromMs) {
                restores.add(new BlockRestore(b.x(), b.y(), b.z(), b.worldName(), b.oldMaterial()));
            }
        }

        Collections.reverse(restores);

        return restores;
    }
}
