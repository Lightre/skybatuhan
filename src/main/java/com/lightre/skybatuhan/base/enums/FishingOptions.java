package com.lightre.skybatuhan.base.enums;

import com.google.gson.annotations.SerializedName;

public final class FishingOptions {

    private FishingOptions() {
    }

    public enum FishMode {
        @SerializedName("Vanilla") VANILLA("Vanilla"),
        @SerializedName("SkyBlock") SKYBLOCK("SkyBlock");

        private final String displayName;

        FishMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum ActionSlot {
        @SerializedName("Slot 1") SLOT_1(0),
        @SerializedName("Slot 2") SLOT_2(1),
        @SerializedName("Slot 3") SLOT_3(2),
        @SerializedName("Slot 4") SLOT_4(3),
        @SerializedName("Slot 5") SLOT_5(4),
        @SerializedName("Slot 6") SLOT_6(5),
        @SerializedName("Slot 7") SLOT_7(6),
        @SerializedName("Slot 8") SLOT_8(7),
        @SerializedName("Slot 9") SLOT_9(8);

        private final int hotbarIndex;

        ActionSlot(int hotbarIndex) {
            this.hotbarIndex = hotbarIndex;
        }

        public int getHotbarIndex() {
            return hotbarIndex;
        }

        @Override
        public String toString() {
            return "Slot " + (hotbarIndex + 1);
        }
    }
}