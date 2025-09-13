package io.github.blackbaroness.loader.bootstrap.bukkit;

import org.bukkit.plugin.Plugin;

@SuppressWarnings("unused")
public abstract class LoaderPluginProxy {

    public LoaderPluginProxy(Plugin plugin) {
    }

    protected abstract void onLoad();

    protected abstract void onEnable();

    protected abstract void onDisable();
}
