/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Selector is the representation of the selector in a "/set format" command.  This class, among other things, provides
 * the translation between the various forms that a selector may take: textual (as in the command), group of EnumSets,
 * or bits.
 *
 * @author Robert Field
 * @since 16
 */
public class Selector {
    static final Selector ALWAYS = new Selector(FormatCase.ALL, FormatAction.ALL, FormatWhen.ALL,
            FormatResolve.ALL, FormatUnresolved.ALL, FormatErrors.ALL);

    // Mapping selector enum names to enums
    static final Map<String, SelectorInstanceWithDoc> selectorMap = new HashMap<>();

    private long bits = -1L;
    private String text = null;
    private EnumSet<FormatCase> cc = null;
    private EnumSet<FormatAction> ca;
    private EnumSet<FormatWhen> cw;
    private EnumSet<FormatResolve> cr;
    private EnumSet<FormatUnresolved> cu;
    private EnumSet<FormatErrors> ce;

    Selector(long bits) {
        this.bits = bits;
    }

    Selector(FormatCase fc, FormatAction fa, FormatWhen fw,
             FormatResolve fr, FormatUnresolved fu, FormatErrors fe) {
        this(EnumSet.of(fc), EnumSet.of(fa), EnumSet.of(fw),
                EnumSet.of(fr), EnumSet.of(fu), EnumSet.of(fe));
    }

    Selector(String text, EnumSet<FormatCase> cc, EnumSet<FormatAction> ca, EnumSet<FormatWhen> cw,
             EnumSet<FormatResolve> cr, EnumSet<FormatUnresolved> cu, EnumSet<FormatErrors> ce) {
        this(cc, ca, cw, cr, cu, ce);
        this.text = text;
    }

    Selector(EnumSet<FormatCase> cc, EnumSet<FormatAction> ca, EnumSet<FormatWhen> cw,
             EnumSet<FormatResolve> cr, EnumSet<FormatUnresolved> cu, EnumSet<FormatErrors> ce) {
        this.cc = cc;
        this.ca = ca;
        this.cw = cw;
        this.cr = cr;
        this.cu = cu;
        this.ce = ce;
    }

    long asBits() {
        if (bits < 0) {
            long res = 0L;
            for (FormatCase fc : cc)
                res |= 1 << fc.ordinal();
            res <<= FormatAction.COUNT;
            for (FormatAction fa : ca)
                res |= 1 << fa.ordinal();
            res <<= FormatWhen.COUNT;
            for (FormatWhen fw : cw)
                res |= 1 << fw.ordinal();
            res <<= FormatResolve.COUNT;
            for (FormatResolve fr : cr)
                res |= 1 << fr.ordinal();
            res <<= FormatUnresolved.COUNT;
            for (FormatUnresolved fu : cu)
                res |= 1 << fu.ordinal();
            res <<= FormatErrors.COUNT;
            for (FormatErrors fe : ce)
                res |= 1 << fe.ordinal();
            bits = res;
        }
        return bits;
    }

    /**
     * The string representation.
     *
     * @return the original string form, if known, otherwise reconstructed.
     */
    @Override
    public String toString() {
        if (text == null) {
            unpackEnumSets();
            StringBuilder sb = new StringBuilder();
            selectorToString(sb, cc, FormatCase.ALL);
            selectorToString(sb, ca, FormatAction.ALL);
            selectorToString(sb, cw, FormatWhen.ALL);
            selectorToString(sb, cr, FormatResolve.ALL);
            selectorToString(sb, cu, FormatUnresolved.ALL);
            selectorToString(sb, ce, FormatErrors.ALL);
            this.text = sb.toString();
        }
        return text;
    }

    private <E extends Enum<E>> void selectorToString(StringBuilder sb, EnumSet<E> c, EnumSet<E> all) {
        if (!c.equals(all)) {
            sb.append(c.stream()
                    .map(v -> v.name().toLowerCase(Locale.US))
                    .collect(new Collector<CharSequence, StringJoiner, String>() {
                        @Override
                        public BiConsumer<StringJoiner, CharSequence> accumulator() {
                            return StringJoiner::add;
                        }

                        @Override
                        public Supplier<StringJoiner> supplier() {
                            return () -> new StringJoiner(",", (sb.isEmpty())? "" : "-", "")
                                    .setEmptyValue("");
                        }

                        @Override
                        public BinaryOperator<StringJoiner> combiner() {
                            return StringJoiner::merge;
                        }

                        @Override
                        public Function<StringJoiner, String> finisher() {
                            return StringJoiner::toString;
                        }

                        @Override
                        public Set<Characteristics> characteristics() {
                            return Collections.emptySet();
                        }
                    }));
        }
    }

    /**
     * Takes the bit representation, and uses it to set the EnumSet representation.
     */
    private class BitUnpacker {

        long b = bits;

        <E extends Enum<E> & SelectorInstanceWithDoc> EnumSet<E> unpackEnumbits(Class<E> k, E[] values) {
            EnumSet<E> c = EnumSet.noneOf(k);
            for (int i = 0; i < values.length; ++i) {
                if ((b & (1L << i)) != 0) {
                    c.add(values[i]);
                }
            }
            b >>>= values.length;
            return c;
        }

        void unpack() {
            // inverseof the order they were packed
            ce = unpackEnumbits(FormatErrors.class, FormatErrors.values());
            cu = unpackEnumbits(FormatUnresolved.class, FormatUnresolved.values());
            cr = unpackEnumbits(FormatResolve.class, FormatResolve.values());
            cw = unpackEnumbits(FormatWhen.class, FormatWhen.values());
            ca = unpackEnumbits(FormatAction.class, FormatAction.values());
            cc = unpackEnumbits(FormatCase.class, FormatCase.values());
        }
    }

    private void unpackEnumSets() {
        if (cc == null) {
            new BitUnpacker().unpack();
        }
    }

    /**
     * Does the provided selector include all settings in ours?
     *
     * @param os the provided selector
     * @return is it included in
     */
    boolean includedIn(Selector os) {
        return (asBits() & ~os.asBits()) == 0;
    }

    /**
     * Does this selector include all the settings in the provided selector?
     *
     * @param os the provided selector
     * @return is it covered
     */
    boolean covers(Selector os) {
        return (asBits() & os.asBits()) == os.asBits();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Selector selector)) {
            return false;
        }
        return asBits() == selector.asBits();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(asBits());
    }

    /**
     * Representation of any single value in the Format* enums.
     */
    interface SelectorInstanceWithDoc {
        SelectorKind kind();
    }

    public enum SelectorKind {
        CASE(FormatCase.class),
        ACTION(FormatAction.class),
        WHEN(FormatWhen.class),
        RESOLVE(FormatResolve.class),
        UNRESOLVED(FormatUnresolved.class),
        ERRORS(FormatErrors.class);

        final EnumSet<? extends SelectorInstanceWithDoc> all;
        final Class<? extends SelectorInstanceWithDoc> k;

        <E extends Enum<E> & SelectorInstanceWithDoc>
        SelectorKind(Class<E> k) {
            this.all = EnumSet.allOf(FormatCase.class);
            this.k = k;
        }
    }

    /**
     * The event cases
     */
    public enum FormatCase implements SelectorInstanceWithDoc {
        IMPORT,      // import declaration
        CLASS,       // class declaration
        INTERFACE,   // interface declaration
        ENUM,        // enum declaration
        ANNOTATION,  // annotation interface declaration
        RECORD,      // record declaration
        METHOD,      // method declaration -- note: {type}==parameter-types
        VARDECL,     // variable declaration without init
        VARINIT,     // variable declaration with init
        EXPRESSION,  // expression -- note: {name}==scratch-variable-name
        VARVALUE,    // variable value expression
        ASSIGNMENT,  // assign variable
        STATEMENT;   // statement

        static final EnumSet<FormatCase> ALL = EnumSet.allOf(FormatCase.class);

        @Override
        public SelectorKind kind() {
            return SelectorKind.CASE;
        }
    }

    /**
     * The event actions
     */
    public enum FormatAction implements SelectorInstanceWithDoc {
        ADDED,     // snippet has been added
        MODIFIED,  // an existing snippet has been modified
        REPLACED,  // an existing snippet has been replaced with a new snippet
        OVERWROTE, // an existing snippet has been overwritten
        DROPPED,   // snippet has been dropped
        USED;      // snippet was used when it cannot be

        static final EnumSet<FormatAction> ALL = EnumSet.allOf(FormatAction.class);
        static final int COUNT = ALL.size();

        @Override
        public SelectorKind kind() {
            return SelectorKind.ACTION;
        }
    }

    /**
     * When the event occurs: primary or update
     */
    public enum FormatWhen implements SelectorInstanceWithDoc {
        PRIMARY,   // the entered snippet
        UPDATE;    // an update to a dependent snippet

        static final EnumSet<FormatWhen> ALL = EnumSet.allOf(FormatWhen.class);
        static final int COUNT = ALL.size();

        @Override
        public SelectorKind kind() {
            return SelectorKind.WHEN;
        }
    }

    /**
     * Resolution problems
     */
    public enum FormatResolve implements SelectorInstanceWithDoc {
        OK,          // resolved correctly
        DEFINED,     // defined despite recoverable unresolved references
        NOT_DEFINED; // not defined because of recoverable unresolved references

        static final EnumSet<FormatResolve> ALL = EnumSet.allOf(FormatResolve.class);
        static final int COUNT = ALL.size();

        @Override
        public SelectorKind kind() {
            return SelectorKind.RESOLVE;
        }
    }

    /**
     * Count of unresolved references
     */
    public enum FormatUnresolved implements SelectorInstanceWithDoc {
        UNRESOLVED0, // no names are unresolved
        UNRESOLVED1, // one name is unresolved
        UNRESOLVED2; // two or more names are unresolved

        static final EnumSet<FormatUnresolved> ALL = EnumSet.allOf(FormatUnresolved.class);
        static final int COUNT = ALL.size();

        @Override
        public SelectorKind kind() {
            return SelectorKind.UNRESOLVED;
        }
    }

    /**
     * Count of unresolved references
     */
    public enum FormatErrors implements SelectorInstanceWithDoc {
        NO_ERROR,   // no errors
        ONE_ERROR,  // one error
        MORE_ERROR; // two or more errors

        static final EnumSet<FormatErrors> ALL = EnumSet.allOf(FormatErrors.class);
        static final int COUNT = ALL.size();

        @Override
        public SelectorKind kind() {
            return SelectorKind.ERRORS;
        }
    }


    static {
        // map all selector value names to values
        for (FormatCase e : FormatCase.ALL)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatAction e : FormatAction.ALL)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatResolve e : FormatResolve.ALL)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatUnresolved e : FormatUnresolved.ALL)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatErrors e : FormatErrors.ALL)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatWhen e : FormatWhen.ALL)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
    }

    /**
     * Builds a selector from adds.
     */
    static class SelectorBuilder {
        final String selectorText;
        private final SelectorCollector<FormatCase> fcase = new SelectorCollector<>(FormatCase.class);
        private final SelectorCollector<FormatAction> faction = new SelectorCollector<>(FormatAction.class);
        private final SelectorCollector<FormatWhen> fwhen = new SelectorCollector<>(FormatWhen.class);
        private final SelectorCollector<FormatResolve> fresolve = new SelectorCollector<>(FormatResolve.class);
        private final SelectorCollector<FormatUnresolved> funresolved = new SelectorCollector<>(FormatUnresolved.class);
        private final SelectorCollector<FormatErrors> ferrors = new SelectorCollector<>(FormatErrors.class);

        private static class SelectorCollector<E extends Enum<E> & SelectorInstanceWithDoc> {
            final EnumSet<E> all;
            EnumSet<E> set;

            SelectorCollector(Class<E> k) {
                this.all = EnumSet.allOf(k);
                this.set = EnumSet.noneOf(k);
            }

            void add(E e) {
                set.add(e);
            }

            EnumSet<E> get() {
                return set.isEmpty()
                        ? all
                        : set;
            }
        }

        SelectorBuilder(String selectorText) {
            this.selectorText = selectorText;
        }

        void add(SelectorInstanceWithDoc v) {
            switch (v.kind()) {
                case CASE -> fcase.add((FormatCase) v);
                case ACTION -> faction.add((FormatAction) v);
                case WHEN -> fwhen.add((FormatWhen) v);
                case RESOLVE -> fresolve.add((FormatResolve) v);
                case UNRESOLVED -> funresolved.add((FormatUnresolved) v);
                case ERRORS -> ferrors.add((FormatErrors) v);
            }
        }

        Selector toSelector() {
            return new Selector(selectorText,
                    fcase.get(), faction.get(), fwhen.get(), fresolve.get(), funresolved.get(), ferrors.get());
        }
    }
}
