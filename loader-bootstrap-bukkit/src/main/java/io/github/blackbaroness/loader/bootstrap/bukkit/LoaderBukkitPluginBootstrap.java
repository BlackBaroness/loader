package io.github.blackbaroness.loader.bootstrap.bukkit;

import com.google.common.base.Throwables;
import io.github.blackbaroness.loader.bootstrap.LoaderBootstrap;
import lombok.SneakyThrows;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public abstract class LoaderBukkitPluginBootstrap extends JavaPlugin {

    private final LoaderBootstrap bootstrap;
    private Throwable loadException = null;

    protected LoaderBukkitPluginBootstrap() {
        final Plugin plugin = this;
        this.bootstrap = new LoaderBootstrap(
            getLibrariesDirectory(),
            getTempDirectory(),
            getLogger(),
            getMainClass().getName(),
            Server.class.getClassLoader()
        ) {
            @SneakyThrows
            @Override
            protected Object createMainInstance0(Class<?> clazz) {
                return clazz.getDeclaredConstructor(Plugin.class).newInstance(plugin);
            }
        };
    }

    @Override
    public void onLoad() {
        try {
            bootstrap.createMainInstance();
            bootstrap.invokeMainMethod("onLoad");
        } catch (Throwable e) {
            loadException = e;
            die(loadException);
        }
    }

    @Override
    public void onEnable() {
        if (loadException != null) {
            die(loadException);
            return;
        }

        try {
            bootstrap.invokeMainMethod("onEnable");
        } catch (Throwable e) {
            die(e);
        }
    }

    @SneakyThrows
    @Override
    public void onDisable() {
        if (bootstrap.getMainInstance() != null) {
            bootstrap.invokeMainMethod("onDisable");
        }
    }

    private void die(Throwable e) {
        getLogger().severe("Failed to load the plugin: " + Throwables.getStackTraceAsString(e));
        getServer().shutdown();
    }

    protected abstract Path getLibrariesDirectory();

    protected abstract Path getTempDirectory();

    protected abstract Class<? extends LoaderPluginProxy> getMainClass();
}
