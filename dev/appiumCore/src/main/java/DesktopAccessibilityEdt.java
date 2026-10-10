/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;

/** Synchronous boundary for the external bridge's socket thread; all AWT access stays on EDT. */
public final class DesktopAccessibilityEdt {
    private DesktopAccessibilityEdt() {}

    public static boolean isQuery(String command) {
        return Set.of("getWindowRoot", "getChildren", "getInfo", "findFirst", "findAll",
                "getValue", "isAlive", "getToggleState").contains(command);
    }

    public static Object dispatchOnEdt(Class<?> handler, String command, Map<?, ?> parameters, Object registry) {
        Object[] result = new Object[1];
        RuntimeException[] failure = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Method dispatch = handler.getDeclaredMethod("dispatch", String.class, Map.class, registry.getClass());
                    dispatch.setAccessible(true);
                    result[0] = dispatch.invoke(null, command, parameters, registry);
                } catch (ReflectiveOperationException exception) {
                    Throwable cause = exception instanceof InvocationTargetException
                            ? ((InvocationTargetException) exception).getCause() : exception;
                    failure[0] = new IllegalStateException("Desktop accessibility query failed", cause);
                }
            });
        } catch (InterruptedException exception) {
            java.lang.Thread.currentThread().interrupt();
            throw new IllegalStateException("Desktop accessibility query interrupted", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Desktop accessibility dispatcher failed", exception.getCause());
        }
        if (failure[0] != null) throw failure[0];
        return result[0];
    }
}
