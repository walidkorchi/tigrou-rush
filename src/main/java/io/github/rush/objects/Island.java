package io.github.rush.objects;

import lombok.Getter;

public class Island {
    @Getter
    private final int x, y, z;
    @Getter
    private final int rotation;

    public Island(int x, int y, int z, int rotation) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation;
    }
}
