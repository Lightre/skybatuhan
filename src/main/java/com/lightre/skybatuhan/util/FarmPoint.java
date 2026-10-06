package com.lightre.skybatuhan.util;

import net.minecraft.world.phys.Vec3;

public class FarmPoint {
    public double x, y, z;

    // empty constructor is required for GSON to read
    @SuppressWarnings("unused")
    public FarmPoint() {
    }

    public FarmPoint(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double distanceTo(Vec3 pos) {
        double dx = x - pos.x;
        double dy = y - pos.y;
        double dz = z - pos.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}