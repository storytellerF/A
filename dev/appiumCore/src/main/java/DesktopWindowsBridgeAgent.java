/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.Opcodes;

/** JDK 21 compatibility for DesktopDriver 2.8.0's removed Component.getPeer() lookup. */
public final class DesktopWindowsBridgeAgent {
    private static final String HANDLER = "io/verisoft/appium/CommandHandler";
    private static final String DESCRIPTOR = "(Ljava/awt/Component;)J";

    private DesktopWindowsBridgeAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
        verifyBridgeVersion();
        Module agentModule = DesktopWindowsBridgeAgent.class.getModule();
        instrumentation.redefineModule(java.awt.Component.class.getModule(), Set.of(), Map.of(),
                Map.of("java.awt", Set.of(agentModule), "sun.awt.windows", Set.of(agentModule)),
                Set.of(), Map.of());
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                return HANDLER.equals(name) ? patchHandler(bytes) : null;
            }
        }, true);
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals(HANDLER.replace('/', '.'))) {
                instrumentation.retransformClasses(loaded);
            }
        }
    }

    private static void verifyBridgeVersion() throws Exception {
        Class<?> agent = Class.forName("io.verisoft.appium.AppiumDesktopAgent", false,
                DesktopWindowsBridgeAgent.class.getClassLoader());
        Path jarPath = Path.of(agent.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile jar = new JarFile(jarPath.toFile());
                BufferedInputStream bytes = new BufferedInputStream(jar.getInputStream(jar.getJarEntry(HANDLER + ".class")))) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
            if (!hash.equals("a0073066d5ab59acee432c847f5725217822a703e002570b3c8c69e014ae07f9")) {
                throw new IllegalStateException("Unsupported Java bridge; install appium-desktop-driver@2.8.0");
            }
        }
    }

    static byte[] patchHandler(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        boolean[] patched = {false, false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("dispatch") && descriptor.equals(
                        "(Ljava/lang/String;Ljava/util/Map;Lio/verisoft/appium/ComponentRegistry;)Ljava/lang/Object;")) {
                    patched[1] = true;
                    return new MethodVisitor(Opcodes.ASM9, method) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            Label onEdt = new Label();
                            visitMethodInsn(Opcodes.INVOKESTATIC, "javax/swing/SwingUtilities",
                                    "isEventDispatchThread", "()Z", false);
                            visitJumpInsn(Opcodes.IFNE, onEdt);
                            visitVarInsn(Opcodes.ALOAD, 0);
                            visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/storyteller_f/a/dev/appium/DesktopAccessibilityEdt", "isQuery",
                                    "(Ljava/lang/String;)Z", false);
                            visitJumpInsn(Opcodes.IFEQ, onEdt);
                            visitLdcInsn(Type.getObjectType(reader.getClassName()));
                            visitVarInsn(Opcodes.ALOAD, 0);
                            visitVarInsn(Opcodes.ALOAD, 1);
                            visitVarInsn(Opcodes.ALOAD, 2);
                            visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/storyteller_f/a/dev/appium/DesktopAccessibilityEdt", "dispatchOnEdt",
                                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;", false);
                            visitInsn(Opcodes.ARETURN);
                            visitLabel(onEdt);
                        }
                    };
                }
                if (!name.equals("getHwnd") || !descriptor.equals(DESCRIPTOR)) return method;
                patched[0] = true;
                method.visitCode();
                method.visitVarInsn(Opcodes.ALOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/storyteller_f/a/dev/appium/DesktopWindowHandles", "getWindowHandle", DESCRIPTOR, false);
                method.visitInsn(Opcodes.LRETURN);
                method.visitMaxs(2, 1);
                method.visitEnd();
                return null;
            }
        }, ClassReader.SKIP_FRAMES);
        if (!patched[0] || !patched[1]) {
            throw new IllegalStateException("Unsupported DesktopDriver Java bridge: getHwnd/dispatch shape changed");
        }
        return writer.toByteArray();
    }
}
