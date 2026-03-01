package io.github.rush.objects;

import lombok.Getter;

public class Island {
    @Getter
    private final int x, z;
    @Getter
    private final int rotation;

    public Island(int x, int z, int rotation) {
        this.x = x;
        this.z = z;
        this.rotation = rotation;
    }
}
