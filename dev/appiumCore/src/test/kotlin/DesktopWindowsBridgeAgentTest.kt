/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import org.junit.Assume.assumeTrue
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.awt.Frame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopWindowsBridgeAgentTest {
    @Test
    fun `patched driver reads a real Windows HWND`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val method = loadHandler().getMethod("getHwnd", java.awt.Component::class.java)
        // AWT peers must be created and disposed on the EDT; this is a synchronous JUnit boundary.
        SwingUtilities.invokeAndWait {
            val frame = Frame("Desktop bridge test")
            try {
                frame.addNotify()
                val handle = method.invoke(null, frame) as Long
                assertTrue(handle != 0L, "The bridge must return a native HWND rather than guess by title")
                assertEquals(DesktopWindowHandles.getWindowHandle(frame), handle)
            } finally {
                frame.dispose()
            }
        }
    }

    @Test
    fun `unsupported driver shape fails explicitly`() {
        assertFailsWith<IllegalStateException> {
            DesktopWindowsBridgeAgent.patchHandler(buildHandlerFixture(false))
        }
    }

    @Test
    fun `bridge queries run on the Swing EDT`() {
        val handler = loadHandler()
        val registryType = handler.classLoader.loadClass("io.verisoft.appium.ComponentRegistry")
        val registry = registryType.getConstructor().newInstance()

        val dispatch = handler.getMethod("dispatch", String::class.java, Map::class.java, registryType)
        assertEquals("true", dispatch.invoke(null, "getChildren", emptyMap<String, String>(), registry))
        assertEquals("false", dispatch.invoke(null, "invoke", emptyMap<String, String>(), registry))
    }

    private fun loadHandler(): Class<*> {
        val registry = ClassWriter(0)
        registry.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC,
            "io/verisoft/appium/ComponentRegistry",
            null,
            "java/lang/Object",
            null,
        )
        val constructor = registry.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructor.visitCode()
        constructor.visitVarInsn(Opcodes.ALOAD, 0)
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        constructor.visitInsn(Opcodes.RETURN)
        constructor.visitMaxs(1, 1)
        constructor.visitEnd()
        registry.visitEnd()
        val loader =
            object : ClassLoader(javaClass.classLoader) {
                fun loadType(name: String, bytes: ByteArray): Class<*> = defineClass(name, bytes, 0, bytes.size)
            }
        loader.loadType("io.verisoft.appium.ComponentRegistry", registry.toByteArray())
        return loader.loadType("example.Handler", DesktopWindowsBridgeAgent.patchHandler(buildHandlerFixture(true)))
    }

    private fun buildHandlerFixture(includeMethod: Boolean): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "example/Handler", null, "java/lang/Object", null)
        if (includeMethod) {
            val method =
                writer.visitMethod(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    "getHwnd",
                    "(Ljava/awt/Component;)J",
                    null,
                    null,
                )
            method.visitCode()
            method.visitInsn(Opcodes.LCONST_0)
            method.visitInsn(Opcodes.LRETURN)
            method.visitMaxs(2, 1)
            method.visitEnd()
        }
        val handler =
            writer.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "dispatch",
                "(Ljava/lang/String;Ljava/util/Map;Lio/verisoft/appium/ComponentRegistry;)Ljava/lang/Object;",
                null,
                null,
            )
        handler.visitCode()
        handler.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "javax/swing/SwingUtilities",
            "isEventDispatchThread",
            "()Z",
            false,
        )
        handler.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Z)Ljava/lang/String;", false)
        handler.visitInsn(Opcodes.ARETURN)
        handler.visitMaxs(1, 3)
        handler.visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
    }
}
