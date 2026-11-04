package io.github.blackbaroness.loader.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;

public class ChildFirstClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    public ChildFirstClassLoader(URL[] classpath, ClassLoader parent) {
        super(classpath, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                if (name.startsWith("java.")) {
                    type = loadFromParent(name);
                } else {
                    try {
                        type = findClass(name);
                    } catch (ClassNotFoundException e) {
                        type = loadFromParent(name);
                    }
                }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    private Class<?> loadFromParent(String name) throws ClassNotFoundException {
        var parent = getParent();
        return parent == null ? Class.forName(name, false, null) : parent.loadClass(name);
    }

    @Override
    public URL getResource(String name) {
        URL local = findResource(name);
        if (local != null) return local;
        var parent = getParent();
        return parent == null ? super.getResource(name) : parent.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        var resources = new LinkedHashMap<String, URL>();
        findResources(name).asIterator().forEachRemaining(url -> resources.putIfAbsent(url.toExternalForm(), url));
        var parent = getParent();
        var inherited = parent == null ? super.getResources(name) : parent.getResources(name);
        inherited.asIterator().forEachRemaining(url -> resources.putIfAbsent(url.toExternalForm(), url));
        return Collections.enumeration(resources.values());
    }
}
