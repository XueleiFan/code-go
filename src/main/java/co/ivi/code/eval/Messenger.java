/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package co.ivi.code.eval;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * User message reporting support
 *
 * @author Robert Field
 */
public record Messenger(PrintStream ops) {

    public PrintStream ops() {
        return ops;
    }

    public void log(String format, Object... args) {
        ops.printf(format, args);
        if (format != null) {
            ops.println();
        }
    }

    public void out(String format, Object... args) {
        ops.printf(format, args);
        if (format != null) {
            ops.println();
        }
    }

    public void msg(String resourceKey, Object... args) {
        ops.println(MessageFormat.format(ResourceKeys.resource(resourceKey), args));
    }

    public static class ResourceKeys {
        private static final String L10N_RB = "l10n";
        private static final ResourceBundle rb;

        static {
            ResourceBundle temp = null;
            try {
                temp = ResourceBundle.getBundle(L10N_RB);
            } catch (MissingResourceException mre) {
                System.err.printf("Cannot find ResourceBundle: %s", L10N_RB);
            }

            rb = temp;
        }

        public static String resource(String key) {
            if (rb != null) {
                try {
                    return rb.getString(key);
                } catch (MissingResourceException mre) {
                    System.err.printf(
                            "Missing resource: %s in %s", key, L10N_RB);
                }
            }

            return "";
        }
    }
}
