package com.lightre.skybatuhan.compat;

import com.lightre.skybatuhan.manager.ModuleManager;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ModuleManager.openMenu();
            return parent;
        };
    }
}