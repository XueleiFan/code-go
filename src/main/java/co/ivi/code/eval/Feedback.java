/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static co.ivi.code.eval.Selector.*;

/**
 * Feedback customization support
 *
 * @author Robert Field
 */
public class Feedback {

    // Pattern for substituted fields within a customized format string
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\{(.*?)\\}");

    // Internal field name for truncation length
    private static final String TRUNCATION_FIELD = "<truncation>";

    // For encoding to Properties String
    private static final String RECORD_SEPARATOR = "\u241E";

    // Selector for truncation of var value
    private static final Selector VAR_VALUE_ADD_SELECTOR = new Selector(
            FormatCase.VARVALUE,
            FormatAction.ADDED,
            FormatWhen.PRIMARY,
            FormatResolve.OK,
            FormatUnresolved.UNRESOLVED0,
            FormatErrors.NO_ERROR);

    // Current mode -- initial value is placeholder during start-up
    private Mode mode = new Mode("");

    // Retained current mode -- for checks
    private Mode retainedCurrentMode = null;

    // Mapping of mode name to mode
    private final Map<String, Mode> modeMap = new HashMap<>();

    // Mapping of mode names to encoded retained mode
    private final Map<String, String> retainedMap = new HashMap<>();

    public String format(FormatCase fc, FormatAction fa, FormatWhen fw,
                         FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                         String name, String type, String value, String unresolved, Stream<String> errorLines) {
        return mode.format(fc, fa, fw, fr, fu, fe,
                name, type, value, unresolved, errorLines);
    }

    public String format(String field, FormatCase fc, FormatAction fa, FormatWhen fw,
                         FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                         String name, String type, String value, String unresolved, Stream<String> errorLines) {
        return mode.format(field, fc, fa, fw, fr, fu, fe,
                name, type, value, unresolved, errorLines);
    }

    public String truncateVarValue(String value) {
        return mode.truncateVarValue(value);
    }

    public boolean setFeedback(Messenger messenger, ArgTokenizer at, Consumer<String> retainer) {
        return new Setter(messenger, at).setFeedback(retainer);
    }

    public boolean setFormat(Messenger messenger, ArgTokenizer at) {
        return new Setter(messenger, at).setFormat();
    }

    public boolean setTruncation(Messenger messenger, ArgTokenizer at) {
        return new Setter(messenger, at).setTruncation();
    }

    public boolean setMode(Messenger messenger, ArgTokenizer at, Consumer<String> retainer) {
        return new Setter(messenger, at).setMode(retainer);
    }

    public boolean restoreEncodedModes(Messenger messenger, String encoded) {
        return new Setter(messenger, new ArgTokenizer("<init>", "")).restoreEncodedModes(encoded);
    }

    public void markModesReadOnly() {
        modeMap.values()
                .forEach(m -> m.readOnly = true);
    }

    /**
     * Holds all the context of a mode
     */
    private static class Mode {

        // Name of mode
        final String name;

        // Display command verification/information
        boolean commandFluff;

        // Setting (including format) by field
        final Map<String, List<Setting>> byField;

        boolean readOnly = false;

        record Setting(String format, Selector selector) {

            @Override
            public boolean equals(Object o) {
                if (o instanceof Setting ing) {
                    return format.equals(ing.format)
                            && selector.equals(ing.selector);
                } else {
                    return false;
                }
            }

            @Override
            public String toString() {
                        return "Setting(" + format + "," + selector.toString() + ")";
                    }
        }

        /**
         * Set up an empty mode.
         */
        Mode(String name) {
            this.name = name;
            this.byField = new HashMap<>();
            set("name", "%1$s", Selector.ALWAYS);
            set("type", "%2$s", Selector.ALWAYS);
            set("value", "%3$s", Selector.ALWAYS);
            set("unresolved", "%4$s", Selector.ALWAYS);
            set("errors", "%5$s", Selector.ALWAYS);
            set("err", "%6$s", Selector.ALWAYS);

            set("errorline", "    {err}%n", Selector.ALWAYS);

            set("pre", "|  ", Selector.ALWAYS);
            set("post", "%n", Selector.ALWAYS);
            set("errorpre", "|  ", Selector.ALWAYS);
            set("errorpost", "%n", Selector.ALWAYS);
        }

        private Mode(String name, boolean commandFluff) {
            this.name = name;
            this.commandFluff = commandFluff;
            this.byField = new HashMap<>();
        }

        /**
         * Set up a copied mode.
         */
        Mode(String name, Mode m) {
            this(name, m.commandFluff);
            m.byField.forEach((fieldName, settingList) ->
                    settingList.forEach(setting -> set(fieldName, setting.format, setting.selector)));

        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Mode m) {
                return name.equals((m.name))
                        && commandFluff == m.commandFluff
                        && byField.equals((m.byField));
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        /**
         * Set if this mode displays informative/confirmational messages on
         * commands.
         *
         * @param fluff the value to set
         */
        void setCommandFluff(boolean fluff) {
            commandFluff = fluff;
        }

        /**
         * Encodes the mode into a String, so it can be saved in Preferences.
         *
         * @return the string representation
         */
        String encode() {
            List<String> el = new ArrayList<>();
            el.add(name);
            el.add(String.valueOf(commandFluff));
            for (Entry<String, List<Setting>> es : byField.entrySet()) {
                el.add(es.getKey());
                el.add("(");
                for (Setting ing : es.getValue()) {
                    el.add(ing.selector.toString());
                    el.add(ing.format);
                }
                el.add(")");
            }
            el.add("***");
            return String.join(RECORD_SEPARATOR, el);
        }

        private void add(String field, Setting ing) {
            List<Setting> settings = byField.get(field);
            if (settings == null) {
                settings = new ArrayList<>();
                byField.put(field, settings);
            } else {
                // remove completely obscured settings.
                // transformation of partially obscured would be confusing to user and complex
                Selector addedSelector = ing.selector;
                settings.removeIf(t -> t.selector.includedIn(addedSelector));
            }
            settings.add(ing);
        }

        void set(String field, String format, Selector selector) {
            add(field, new Setting(format, selector));
        }

        /**
         * Lookup format Replace fields with context specific formats.
         *
         * @return format string
         */
        String format(String field, Selector selector) {
            List<Setting> settings = byField.get(field);
            if (settings == null) {
                return ""; //TODO error?
            }
            String format = null;
            // Iterate backward, as most recent setting that covers the case is used
            for (int i = settings.size() - 1; i >= 0; --i) {
                Setting ing = settings.get(i);
                if (ing.selector.covers(selector)) {
                    format = ing.format;
                    break;
                }
            }
            if (format == null || format.isEmpty()) {
                return "";
            }
            Matcher m = FIELD_PATTERN.matcher(format);
            StringBuilder sb = new StringBuilder(format.length());
            while (m.find()) {
                String fieldName = m.group(1);
                String sub = format(fieldName, selector);
                m.appendReplacement(sb, Matcher.quoteReplacement(sub));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        String truncateVarValue(String value) {
            return truncateValue(value, VAR_VALUE_ADD_SELECTOR);
        }

        String truncateValue(String value, Selector selector) {
            if (value==null) {
                return "";
            } else {
                // Retrieve the truncation length
                String truncationField = format(TRUNCATION_FIELD, selector);
                if (truncationField.isEmpty()) {
                    // No truncation set, use whole value
                    return value;
                } else {
                    // Convert truncation length to int
                    // this is safe since it has been tested before it is set
                    int truncationLen = Integer.parseUnsignedInt(truncationField);
                    int len = value.length();
                    if (len > truncationLen) {
                        if (truncationLen <= 13) {
                            // Very short truncations have no room for "..."
                            return value.substring(0, truncationLen);
                        } else {
                            // Normal truncation, make total length equal truncation length
                            int endLen = truncationLen / 3;
                            int startLen = truncationLen - 5 - endLen;
                            return value.substring(0, startLen) + " ... " + value.substring(len -endLen);
                        }
                    } else {
                        // Within truncation length, use whole value
                        return value;
                    }
                }
            }
        }

        // Compute the display output given full context and values
        String format(FormatCase fc, FormatAction fa, FormatWhen fw,
                      FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                      String name, String type, String value, String unresolved, Stream<String> errorLines) {
            return format("display", fc, fa, fw, fr, fu, fe,
                name, type, value, unresolved, errorLines);
        }

        // Compute the display output given full context and values
        String format(String field, FormatCase fc, FormatAction fa, FormatWhen fw,
                      FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                      String name, String type, String value, String unresolved, Stream<String> errorLines) {
            // Convert the context into a bit-representation used as selectors for store field formats
            Selector selector  = new Selector(fc, fa, fw, fr, fu, fe);
            String fname = name==null? "" : name;
            String ftype = type==null? "" : type;
            // Compute the representation of value
            String fvalue = truncateValue(value, selector);
            String funresolved = unresolved==null? "" : unresolved;
            String errors = errorLines.map(el -> String.format(
                            format("errorline", selector),
                            fname, ftype, fvalue, funresolved, "*cannot-use-errors-here*", el))
                    .collect(joining());
            return String.format(
                    format(field, selector),
                    fname, ftype, fvalue, funresolved, errors, "*cannot-use-err-here*");
        }
    }

    // Class used to set custom eval output formats
    // For both /set format  -- Parse arguments, setting custom format, or printing error
    private class Setter {

        private final ArgTokenizer at;
        private final Messenger messenger;
        boolean valid = true;

        Setter(Messenger messenger, ArgTokenizer at) {
            this.messenger = messenger;
            this.at = at;
            at.allowedOptions("-retain");
        }

        void out(String format, Object... args) {
            messenger.out(format, args);
        }

        void msg(String messageKey, Object... args) {
            messenger.msg(messageKey, args);
        }

        void err(String messageKey, Object... args) {
            if (!valid) {
                // no spew of errors
                return;
            }
            valid = false;
            Object[] a2 = Arrays.copyOf(args, args.length + 2);
            a2[args.length] = at.whole();
            messenger.msg(messageKey, a2);
        }

        // Show format settings -- in a predictable order, for testing...
        void showFormatSettings(Mode sm, String f) {
            if (sm == null) {
                modeMap.entrySet().stream()
                        .sorted(Entry.comparingByKey())
                        .forEach(m -> showFormatSettings(m.getValue(), f));
            } else {
                sm.byField.entrySet().stream()
                        .filter(ec -> (f == null)
                            ? !ec.getKey().equals(TRUNCATION_FIELD)
                            : ec.getKey().equals(f))
                        .sorted(Entry.comparingByKey())
                        .forEach(ec -> ec.getValue().forEach(s -> out("/set format %s %s %s %s",
                                sm.name, ec.getKey(), toStringLiteral(s.format),
                                s.selector.toString())));
            }
        }

        void showTruncationSettings(Mode sm) {
            if (sm == null) {
                modeMap.values().forEach(this::showTruncationSettings);
            } else {
                List<Mode.Setting> trunc = sm.byField.get(TRUNCATION_FIELD);
                if (trunc != null) {
                    trunc.forEach(s -> out("/set truncation %s %s %s",
                            sm.name, s.format,
                            s.selector.toString()));
                }
            }
        }

        void showModeSettings(String umode) {
            if (umode == null) {
                modeMap.values().forEach(this::showModeSettings);
            } else {
                Mode m;
                String retained = retainedMap.get(umode);
                if (retained == null) {
                    m = searchForMode(umode, "err.mode.creation");
                    if (m == null) {
                        return;
                    }
                    umode = m.name;
                    retained = retainedMap.get(umode);
                } else {
                    m = modeMap.get(umode);
                }
                if (retained != null) {
                    Mode rm = buildMode(encodedModeIterator(retained));
                    showModeSettings(rm);
                    out("/set mode -retain %s", umode);
                    if (m != null && !m.equals(rm)) {
                        out("");
                        showModeSettings(m);
                    }
                } else {
                    showModeSettings(m);
                }
            }
        }

        void showModeSettings(Mode sm) {
            out("/set mode %s %s",
                    sm.name, sm.commandFluff ? "-command" : "-quiet");
            showFormatSettings(sm, null);
            showTruncationSettings(sm);
        }

        void showFeedbackSetting() {
            if (retainedCurrentMode != null) {
                out("/set feedback -retain %s\n", retainedCurrentMode.name);
            }
            if (mode != retainedCurrentMode) {
                out("/set feedback %s\n", mode.name);
            }
        }

        /**
         * Set mode. Create, changed, or delete a feedback mode. For {@code /set
         * mode <mode> [<old-mode>] [-command|-quiet|-delete|-retain]}.
         *
         * @return true if successful
         */
        boolean setMode(Consumer<String> retainer) {
            class SetMode {

                final String umode;
                final String omode;
                final boolean commandOption;
                final boolean quietOption;
                final boolean deleteOption;
                final boolean retainOption;

                SetMode() {
                    at.allowedOptions("-command", "-quiet", "-delete", "-retain");
                    umode = nextModeIdentifier();
                    omode = nextModeIdentifier();
                    checkOptionsAndRemainingInput();
                    commandOption = at.hasOption("-command");
                    quietOption = at.hasOption("-quiet");
                    deleteOption = at.hasOption("-delete");
                    retainOption = at.hasOption("-retain");
                }

                void delete() {
                    // Note: delete, for safety reasons, does NOT do name matching
                    if (commandOption || quietOption) {
                        err("err.conflicting.options");
                    } else if (retainOption
                            ? !retainedMap.containsKey(umode) && !modeMap.containsKey(umode)
                            : !modeMap.containsKey(umode)) {
                        // Cannot delete a mode that does not exist
                        err("err.mode.unknown", umode);
                    } else if (omode != null) {
                        // old mode is for creation
                        err("err.unexpected.at.end", omode);
                    } else if (mode.name.equals(umode)) {
                        // Cannot delete the current mode out from under us
                        err("err.cannot.delete.current.mode", umode);
                    } else if (retainOption && retainedCurrentMode != null &&
                             retainedCurrentMode.name.equals(umode)) {
                        // Cannot delete the retained mode or re-start will have an error
                        err("err.cannot.delete.retained.mode", umode);
                    } else {
                        Mode m = modeMap.get(umode);
                        if (m != null && m.readOnly) {
                            err("err.not.valid.with.predefined.mode", umode);
                        } else {
                            // Remove the mode
                            modeMap.remove(umode);
                            if (retainOption) {
                                // Remove the retained mode
                                retainedMap.remove(umode);
                                updateRetainedModes();
                            }
                        }
                    }
                }

                void retain() {
                    if (commandOption || quietOption) {
                        err("err.conflicting.options");
                    } else if (omode != null) {
                        // old mode is for creation
                        err("err.unexpected.at.end", omode);
                    } else {
                        Mode m = modeMap.get(umode);
                        if (m == null) {
                            // can only retain existing modes
                            err("err.mode.unknown", umode);
                        } else if (m.readOnly) {
                            err("err.not.valid.with.predefined.mode", umode);
                        } else {
                            // Add to local cache of retained current encodings
                            retainedMap.put(m.name, m.encode());
                            updateRetainedModes();
                        }
                    }
                }

                void updateRetainedModes() {
                    // Join all the retained encodings
                    String encoded = String.join(RECORD_SEPARATOR, retainedMap.values());
                    // Retain it
                    retainer.accept(encoded);
                }

                void create() {
                    if (commandOption && quietOption) {
                        err("err.conflicting.options");
                    } else if (!commandOption && !quietOption) {
                        err("err.mode.creation");
                    } else if (modeMap.containsKey(umode)) {
                        // Mode already exists
                        err("err.mode.exists", umode);
                    } else {
                        Mode om = searchForMode(omode);
                        if (valid) {
                            // We are copying an existing mode and/or creating a
                            // brand-new mode -- in either case create from scratch
                            Mode m = (om != null)
                                    ? new Mode(umode, om)
                                    : new Mode(umode);
                            modeMap.put(umode, m);
                            msg("msg.feedback.new.mode", m.name);
                            m.setCommandFluff(commandOption);
                        }
                    }
                }

                boolean set() {
                    if (valid && !commandOption && !quietOption && !deleteOption &&
                            omode == null && !retainOption) {
                        // Not a creation, deletion, or retain -- show mode(s)
                        showModeSettings(umode);
                    } else if (valid && umode == null) {
                        err("err.missing.mode");
                    } else if (valid && deleteOption) {
                        delete();
                    } else if (valid && retainOption) {
                        retain();
                    } else if (valid) {
                        create();
                    }
                    if (!valid) {
                        msg("msg.see", "/help /set mode");
                    }
                    return valid;
                }
            }
            return new SetMode().set();
        }

        // For /set format <mode> <field> "<format>" <selector>...
        boolean setFormat() {
            Mode m = nextMode();
            String field = toIdentifier(next(), "err.field.name");
            String format = nextFormat();
            if (valid && format == null) {
                if (field != null && m != null && !m.byField.containsKey(field)) {
                    err("err.field.name", field);
                } else {
                    showFormatSettings(m, field);
                }
            } else {
                installFormat(m, field, format, "/help /set format");
            }
            return valid;
        }

        // For /set truncation <mode> <length> <selector>...
        boolean setTruncation() {
            Mode m = nextMode();
            String length = next();
            if (length == null) {
                showTruncationSettings(m);
            } else {
                try {
                    // Assure that integer format is correct
                    Integer.parseUnsignedInt(length);
                } catch (NumberFormatException ex) {
                    err("err.truncation.length.not.integer", length);
                }
                // install length into an internal format field
                installFormat(m, TRUNCATION_FIELD, length, "/help /set truncation");
            }
            return valid;
        }

        // For /set feedback <mode>
        boolean setFeedback(Consumer<String> retainer) {
            String umode = next();
            checkOptionsAndRemainingInput();
            boolean retainOption = at.hasOption("-retain");
            if (valid && umode == null && !retainOption) {
                showFeedbackSetting();
                showFeedbackModes();
                return true;
            }
            if (valid) {
                Mode m = umode == null
                        ? mode
                        : searchForMode(toModeIdentifier(umode));
                if (valid && retainOption && !m.readOnly && !retainedMap.containsKey(m.name)) {
                    err("err.retained.feedback.mode.must.be.retained.or.predefined");
                }
                if (valid) {
                    if (umode != null) {
                        mode = m;
                        msg("msg.feedback.mode", mode.name);
                    }
                    if (retainOption) {
                        retainedCurrentMode = m;
                        retainer.accept(m.name);
                    }
                }
            }
            if (!valid) {
                msg("msg.see", "/help /set feedback");
                return false;
            }
            return true;
        }

        boolean restoreEncodedModes(String allEncoded) {
            try {
                // Iterate over each record in each encoded mode
                Iterator<String> itr = encodedModeIterator(allEncoded);
                while (itr.hasNext()) {
                    // Reconstruct the encoded mode
                    Mode m = buildMode(itr);
                    modeMap.put(m.name, m);
                    // Continue to retain if a new retains occur
                    retainedMap.put(m.name, m.encode());
                }
                return true;
            } catch (Throwable exc) {
                // Catastrophic corruption -- clear map
                err("err.retained.mode.failure", exc);
                retainedMap.clear();
                return false;
            }
        }


        /**
         * Set up a mode reconstituted from a preferences string.
         *
         * @param it the encoded Mode broken into String chunks, may contain
         *           subsequent encoded modes
         */
        private Mode buildMode(Iterator<String> it) {
            Mode newMode = new Mode(it.next(), Boolean.parseBoolean(it.next()));
            Map<String, List<Mode.Setting>> fields = new HashMap<>();
            String field;
            while (!(field = it.next()).equals("***")) {
                String open = it.next();
                assert open.equals("(");
                List<Mode.Setting> settings = new ArrayList<>();
                String selectorText;
                while (!(selectorText = it.next()).equals(")")) {
                    String format = it.next();
                    Selector selector;
                    if (selectorText.isEmpty()) {
                        selector = Selector.ALWAYS;
                    } else if (Character.isDigit(selectorText.charAt(0))) {
                        // legacy format, bits
                        long bits = Long.parseLong(selectorText);
                        selector = new Selector(bits);
                    } else {
                        selector = parseSelector(selectorText);
                    }
                    Mode.Setting ing = new Mode.Setting(format, selector);
                    settings.add(ing);
                }
                fields.put(field, settings);
            }
            fields.forEach((fieldName, settingList) ->
                        settingList.forEach(setting -> newMode.set(fieldName, setting.format, setting.selector)));
            return newMode;
        }

        Iterator<String> encodedModeIterator(String encoded) {
            String[] ms = encoded.split(RECORD_SEPARATOR);
            return Arrays.asList(ms).iterator();
        }

        // install the format of a field under parsed selectors
        void installFormat(Mode m, String field, String format, String help) {
            String slRaw;
            List<Selector> selectorList = new ArrayList<>();
            while (valid && (slRaw = next()) != null) {
                selectorList.add(parseSelector(slRaw));
            }
            checkOptionsAndRemainingInput();
            if (valid) {
                if (m.readOnly) {
                    err("err.not.valid.with.predefined.mode", m.name);
                } else if (selectorList.isEmpty()) {
                    // No selectors specified, then always use the format
                    m.set(field, format, Selector.ALWAYS);
                } else {
                    // Set the format of the field for specified selector
                    selectorList.forEach(sel -> m.set(field, format, sel));
                }
            } else {
                msg("msg.see", help);
            }
        }

        void checkOptionsAndRemainingInput() {
            String junk = at.remainder();
            if (!junk.isEmpty()) {
                err("err.unexpected.at.end", junk);
            } else {
                String bad = at.badOptions();
                if (!bad.isEmpty()) {
                    err("err.unknown.option", bad);
                }
            }
        }

        String next() {
            String s = at.next();
            if (s == null) {
                checkOptionsAndRemainingInput();
            }
            return s;
        }

        /**
         * Check that the specified string is an identifier (Java identifier).
         * If null display the missing error. If it is not an identifier,
         * display the error.
         *
         * @param id the string to check, MUST be the most recently retrieved
         * token from 'at'.
         * @param err the resource error to display if not an identifier
         * @return the identifier string, or null if null or not an identifier
         */
        private String toIdentifier(String id, String err) {
            if (!valid || id == null) {
                return null;
            }
            if (at.isQuoted() ||
                    !id.codePoints().allMatch(Character::isJavaIdentifierPart)) {
                err(err, id);
                return null;
            }
            return id;
        }

        private String toModeIdentifier(String id) {
            return toIdentifier(id, "err.mode.name");
        }

        private String nextModeIdentifier() {
            return toModeIdentifier(next());
        }

        private Mode nextMode() {
            String umode = nextModeIdentifier();
            return searchForMode(umode);
        }

        private Mode searchForMode(String umode) {
            return searchForMode(umode, null);
        }

        private Mode searchForMode(String umode, String msgKey) {
            if (!valid || umode == null) {
                return null;
            }
            Mode m = modeMap.get(umode);
            if (m != null) {
                return m;
            }
            // Failing an exact match, go searching
            Mode[] matches = modeMap.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(umode))
                    .map(Entry::getValue)
                    .toArray(Mode[]::new);
            if (matches.length == 1) {
                return matches[0];
            } else {
                if (msgKey != null) {
                    msg(msgKey, "");
                }
                if (matches.length == 0) {
                    err("err.feedback.does.not.match.mode", umode);
                } else {
                    err("err.feedback.ambiguous.mode", umode);
                }

                return null;
            }
        }

        void showFeedbackModes() {
            if (!retainedMap.isEmpty()) {
                msg("msg.feedback.retained.mode.following");
                retainedMap.keySet().stream()
                        .sorted()
                        .forEach(mk -> out("   %s", mk));
            }
            msg("msg.feedback.mode.following");
            modeMap.keySet().stream()
                    .sorted()
                    .forEach(mk -> out("   %s", mk));
        }

        // Read and test if the format string is correctly
        private String nextFormat() {
            return toFormat(next());
        }

        // Test if the format string is correctly
        private String toFormat(String format) {
            if (!valid || format == null) {
                return null;
            }
            if (!at.isQuoted()) {
                err("err.feedback.must.be.quoted", format);
               return null;
            }
            return format;
        }

        // Convert to a quoted string
        private String toStringLiteral(String s) {
            StringBuilder sb = new StringBuilder();
            sb.append('"');
            final int length = s.length();
            for (int offset = 0; offset < length;) {
                final int codepoint = s.codePointAt(offset);

                switch (codepoint) {
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\"':
                        sb.append("\\\"");
                        break;
                    case '\'':
                        sb.append("\\'");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        if (codepoint < 32) {
                            sb.append(String.format("\\%o", codepoint));
                        } else {
                            sb.appendCodePoint(codepoint);
                        }
                        break;
                }

                // do something with the codepoint
                offset += Character.charCount(codepoint);

            }
            sb.append('"');
            return sb.toString();
        }

        private Selector parseSelector(String selectorText) {
            SelectorBuilder seb = new SelectorBuilder(selectorText);
            EnumSet<SelectorKind> seen = EnumSet.noneOf(SelectorKind.class);
            for (String s : selectorText.split("-")) {
                SelectorKind lastKind = null;
                for (String as : s.split(",")) {
                    if (!as.isEmpty()) {
                        SelectorInstanceWithDoc sel = Selector.selectorMap.get(as);
                        if (sel == null) {
                            err("err.feedback.not.a.valid.selector", as, s);
                            return Selector.ALWAYS;
                        }
                        SelectorKind kind = sel.kind();
                        if (lastKind == null) {
                            if (seen.contains(kind)) {
                                err("err.feedback.multiple.sections", as, s);
                                return Selector.ALWAYS;
                            }
                        } else if (kind != lastKind) {
                            err("err.feedback.different.selector.kinds", as, s);
                            return Selector.ALWAYS;
                        }
                        seb.add(sel);
                        seen.add(kind);
                        lastKind = kind;
                    }
                }
            }
            return seb.toSelector();
         }
    }
}
