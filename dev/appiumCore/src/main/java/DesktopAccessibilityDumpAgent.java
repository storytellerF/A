import java.awt.Rectangle;
import java.awt.Window;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;

public class DesktopAccessibilityDumpAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        dump(args);
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        dump(args);
    }

    private static void dump(String path) {
        new Thread(() -> {
            try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
                Window[] windows = Window.getWindows();
                out.println("WINDOW_COUNT " + windows.length);
                for (Window window : windows) {
                    out.println(
                        "WINDOW " + window.getClass().getName()
                            + " showing=" + window.isShowing()
                            + " visible=" + window.isVisible()
                            + " title=" + quote(title(window))
                    );
                    dumpAccessible(window, out, 1);
                }
            } catch (Throwable throwable) {
                try (PrintWriter out = new PrintWriter(new FileWriter(path + ".error"))) {
                    throwable.printStackTrace(out);
                } catch (Throwable ignored) {
                }
            }
        }, "desktop-accessibility-dump-agent").start();
    }

    private static String title(Window window) {
        try {
            if (window instanceof java.awt.Frame) return ((java.awt.Frame) window).getTitle();
            if (window instanceof java.awt.Dialog) return ((java.awt.Dialog) window).getTitle();
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static void dumpAccessible(Object object, PrintWriter out, int depth) {
        if (object == null || depth > 60) return;
        Accessible accessible = object instanceof Accessible ? (Accessible) object : null;
        AccessibleContext context = accessible == null ? null : accessible.getAccessibleContext();
        String indent = "  ".repeat(depth);
        if (context == null) {
            out.println(indent + "NO_CONTEXT class=" + object.getClass().getName());
            return;
        }

        out.println(
            indent
                + "role=" + quote(role(context))
                + " name=" + quote(read(context::getAccessibleName))
                + " desc=" + quote(read(context::getAccessibleDescription))
                + " actions=" + quote(actions(context))
                + " bounds=" + quote(bounds(context))
                + " class=" + object.getClass().getName()
                + " children=" + context.getAccessibleChildrenCount()
        );

        int count = context.getAccessibleChildrenCount();
        for (int index = 0; index < count; index++) {
            try {
                dumpAccessible(context.getAccessibleChild(index), out, depth + 1);
            } catch (Throwable throwable) {
                out.println(indent + "  CHILD_ERROR " + throwable);
            }
        }
    }

    private static String role(AccessibleContext context) {
        try {
            AccessibleRole role = context.getAccessibleRole();
            return role == null ? "" : role.toDisplayString();
        } catch (Throwable throwable) {
            return "<err " + throwable.getClass().getSimpleName() + ">";
        }
    }

    private static String actions(AccessibleContext context) {
        try {
            AccessibleAction action = context.getAccessibleAction();
            if (action == null) return "";
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < action.getAccessibleActionCount(); index++) {
                if (index > 0) builder.append(',');
                builder.append(action.getAccessibleActionDescription(index));
            }
            return builder.toString();
        } catch (Throwable throwable) {
            return "<err " + throwable.getClass().getSimpleName() + ">";
        }
    }

    private static String bounds(AccessibleContext context) {
        try {
            AccessibleComponent component = context.getAccessibleComponent();
            if (component == null) return "";
            Rectangle rectangle = component.getBounds();
            if (rectangle == null) return "";
            return rectangle.x + "," + rectangle.y + "," + rectangle.width + "," + rectangle.height;
        } catch (Throwable throwable) {
            return "<err " + throwable.getClass().getSimpleName() + ">";
        }
    }

    private interface Reader {
        String read() throws Throwable;
    }

    private static String read(Reader reader) {
        try {
            String value = reader.read();
            return value == null ? "" : value;
        } catch (Throwable throwable) {
            return "<err " + throwable.getClass().getSimpleName() + ">";
        }
    }

    private static String quote(String value) {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            + "\"";
    }
}
