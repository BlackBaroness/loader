package io.github.blackbaroness.loader.bootstrap.bungeecord;

import com.google.common.base.Throwables;
import io.github.blackbaroness.loader.bootstrap.LoaderBootstrap;
import lombok.SneakyThrows;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class LoaderBungeeCordPluginBootstrap extends Plugin {

    private final LoaderBootstrap bootstrap;
    private Throwable loadException = null;

    @SneakyThrows
    protected LoaderBungeeCordPluginBootstrap() {
        final Plugin plugin = this;
        this.bootstrap = new LoaderBootstrap(
            getLibrariesDirectory(),
            getTempDirectory(),
            getLogger(),
            getMainClass().getName(),
            getParentClassLoader(),
            Paths.get(getMainClass().getProtectionDomain().getCodeSource().getLocation().toURI())
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
        getProxy().stop(getDescription().getName() + " failed to load");
    }

    protected ClassLoader getParentClassLoader() {
        return ProxyServer.class.getClassLoader();
    }

    protected abstract Path getLibrariesDirectory();

    protected abstract Path getTempDirectory();

    protected abstract Class<? extends LoaderPluginProxy> getMainClass();
}
