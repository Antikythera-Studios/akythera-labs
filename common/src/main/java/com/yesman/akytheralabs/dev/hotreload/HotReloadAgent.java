package com.yesman.akytheralabs.dev.hotreload;

import org.jspecify.annotations.Nullable;

import java.lang.instrument.Instrumentation;

/// The Java agent of the AkytheraLabs jar, started by `-javaagent:<AkytheraLabs jar>`. Holds the
/// [Instrumentation] [HotReloader] redefines classes with.
///
/// The JVM loads the agent through the system class loader, while the mod loader loads AkytheraLabs again
/// through its own class loader. So the two copies of this class are different classes: [#instrumentation] reads
/// the field of the system class loader's copy, the only one [#premain] ran on. Uses JDK types only, since the
/// system class loader can't see the game.
public final class HotReloadAgent {
    /// Set by [#premain]. Null on the mod loader's copy of this class.
    public static volatile @Nullable Instrumentation instrumentation;

    private HotReloadAgent() {}

    public static void premain(final String arguments, final Instrumentation instrumentation) {
        HotReloadAgent.instrumentation = instrumentation;
    }

    /// The instrumentation the JVM handed [#premain], or null when the game runs without the agent.
    public static @Nullable Instrumentation instrumentation() {
        try {
            Class<?> agent = Class.forName(HotReloadAgent.class.getName(), true, ClassLoader.getSystemClassLoader());

            return (Instrumentation) agent.getField("instrumentation").get(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
