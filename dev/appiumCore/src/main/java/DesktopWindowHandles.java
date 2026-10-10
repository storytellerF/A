/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium;

import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class DesktopWindowHandles {
    private DesktopWindowHandles() {}

    public static long getWindowHandle(Component component) {
        try {
            Field peerField = Component.class.getDeclaredField("peer");
            peerField.setAccessible(true);
            Object peer = peerField.get(component);
            if (peer == null) return 0L;
            for (Class<?> type = peer.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Method handle = type.getDeclaredMethod("getHWnd");
                    handle.setAccessible(true);
                    return ((Number) handle.invoke(peer)).longValue();
                } catch (NoSuchMethodException ignored) {
                    // The HWND accessor is declared on WComponentPeer, not every subclass.
                }
            }
            throw new IllegalStateException("Windows AWT peer does not expose getHWnd");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read the JDK 21 Windows window handle", exception);
        }
    }
}
