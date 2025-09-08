package org.apache.pdfbox.examples.signature;

import io.github.aakira.napier.Napier;

import java.util.function.Supplier;

public class Log {
    public static final Log LOG = new Log();

    public void info(String message, Object... params) {
        Napier.INSTANCE.i(format(message, params), null, null);
    }

    public void warn(String message, Exception ex) {
        Napier.INSTANCE.w(message, ex, null);
    }

    public void debug(String message, Exception ex) {
        Napier.INSTANCE.d(message, ex, null);
    }

    public void warn(String message, Object... params) {
        Napier.INSTANCE.w(String.format(message, params), null, null);
    }

    public void error(String message, Object... params) {
        Napier.INSTANCE.e(String.format(message, params), null, null);
    }

    public void error(String message) {
        Napier.INSTANCE.e(message, null, null);
    }

    public void warn(Supplier<String> supplier, Exception ex) {
        Napier.INSTANCE.w(ex, null, supplier::get);
    }

    public void error(String message, Exception ex) {
        Napier.INSTANCE.e(message, ex, null);
    }

    public void error(Exception message, Exception ex) {
        Napier.INSTANCE.e(message.getMessage(), ex, null);
    }

    public void error(String message, Supplier<String > supplier) {
        Napier.INSTANCE.e(format(message, supplier.get()), null, null);
    }

    public void error(Supplier<String > supplier, Exception ex) {
        Napier.INSTANCE.e(ex, null, supplier::get);
    }

    public void debug(String message) {
        Napier.INSTANCE.d(message, null, null);
    }

    public String format(String template, Object... args) {
        for (Object arg : args) {
            template = template.replaceFirst("\\{}", arg == null ? "null" : arg.toString());
        }
        return template;
    }
}
