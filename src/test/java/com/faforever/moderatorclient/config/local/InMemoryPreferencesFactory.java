package com.faforever.moderatorclient.config.local;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * In-memory {@link PreferencesFactory} used for tests (wired via the {@code java.util.prefs.PreferencesFactory}
 * system property in build.gradle's test task) so credential tests never touch the real OS-backed Preferences
 * store (Windows registry / ~/.java) that the packaged app uses for the same node.
 */
public final class InMemoryPreferencesFactory implements PreferencesFactory {
    private static final InMemoryPreferences USER_ROOT = new InMemoryPreferences(null, "");
    private static final InMemoryPreferences SYSTEM_ROOT = new InMemoryPreferences(null, "");

    @Override
    public Preferences userRoot() {
        return USER_ROOT;
    }

    @Override
    public Preferences systemRoot() {
        return SYSTEM_ROOT;
    }

    private static final class InMemoryPreferences extends AbstractPreferences {
        private final InMemoryPreferences parentNode;
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, InMemoryPreferences> children = new TreeMap<>();

        InMemoryPreferences(InMemoryPreferences parent, String name) {
            super(parent, name);
            this.parentNode = parent;
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            if (parentNode != null) {
                parentNode.children.remove(name());
            }
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return children.keySet().toArray(new String[0]);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, key -> new InMemoryPreferences(this, key));
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
            // no-op: nothing to synchronize with, values only ever live in this process
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
            // no-op: nothing to persist, values only ever live in this process
        }
    }
}
