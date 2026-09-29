package com.lightre.skybatuhan.base;

public class ModConfig {
    public SafetyCategory safety = new SafetyCategory();
    public FarmingCategory farming = new FarmingCategory();
    public FishingCategory fishing = new FishingCategory();

    public static class SafetyCategory {
        public long timeoutMs = 3000;
        public float threshold = 0.1f;
    }

    public static class FarmingCategory {
        public GeneralSettings general = new GeneralSettings();
        public FarmingMovements farmingMovements = new FarmingMovements();
    }

    public static class GeneralSettings {
        public double pointRange = 0.8;
        public boolean attackEnabled = true;
    }

    public static class FarmingMovements {
        public MoveSettings firstMove = new MoveSettings();
        public MoveSettings secondMove = new MoveSettings();
    }

    public static class MoveSettings {
        public boolean forward = false;
        public boolean left = false;
        public boolean back = false;
        public boolean right = false;
    }

    public static class FishingCategory {
        public String fishMode = "Vanilla";

        public boolean reelJump = false;

        public double minReelDelay = 400.0;
        public double maxReelDelay = 1000.0;

        public double minCastDelay = 200.0;
        public double maxCastDelay = 1000.0;

        public double afkTimeoutSeconds = 30.0;

        public static final long MIN_CAST_DELAY_MS = 200;
    }
}