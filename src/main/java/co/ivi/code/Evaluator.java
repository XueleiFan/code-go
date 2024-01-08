/*
 * Copyright (c) 2024, Xuelei Fan. All rights reserved.
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package co.ivi.code;

import jdk.jshell.*;
import joptsimple.*;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import co.ivi.code.eval.Messenger;
import co.ivi.code.eval.Command;
import co.ivi.code.eval.Feedback;
import co.ivi.code.eval.Selector;
import co.ivi.code.eval.ArgTokenizer;

public class Evaluator implements Closeable {

    public static final Pattern LineBreakPattern = Pattern.compile("\\R");
    public static final Pattern ID = Pattern.compile("[se]?\\d+([-\\s].*)?");
    public static final String[] SET_SUBCOMMANDS = new String[] {
            "format", "truncation", "feedback", "mode",
            "prompt", "editor", "start", "indent"
        };
    public static final int OUTPUT_WIDTH = 72;
    public static final int DEFAULT_INDENT = 4;
    public static final String FEEDBACK_KEY = "FEEDBACK";
    public static final String MODE_KEY = "MODE";

    private JShell jShell;
    private SourceCodeAnalysis analysis;
    private final ByteArrayOutputStream evalMessage = new ByteArrayOutputStream();
    private Messenger messenger;
    private final Feedback feedback = new Feedback();

    private final Options options = new Options();

    private final Map<String, String> prefs = new HashMap<>();

    private final Consumer<Evaluator> shutdownConsumer;

    Evaluator(Consumer<Evaluator> shutdownConsumer) {
        this.shutdownConsumer = shutdownConsumer;

        PrintStream ops = new PrintStream(evalMessage);
        this.jShell = JShell.builder()
                .out(ops)
                .err(ops)
                .build();
        this.jShell.onShutdown(this::onShutdown);
        this.analysis = jShell.sourceCodeAnalysis();
        this.messenger = new Messenger(ops);
    }

    public Evaluator start() {
        try {
            initFeedback();
        } finally {
            // Discard evaluation output message
            evalMessage.reset();
        }

        return this;
    }

    EvaluationResult evaluate(String source) {
        try {
            return processInput(source);
        } catch (Exception ex) {
            messenger.msg("err.unexpected.exception", ex);
            return new EvaluationResult(false, evalMessage.toString());
        } finally {
            evalMessage.reset();
        }
    }

    private EvaluationResult processInput(String source) {
        // Lambda expression bellow needs a final variable.
        final boolean[] evalStatus = {true};
        try (BufferedReader reader =
                     new BufferedReader(new StringReader(source))) {
            StringBuilder stringBuilder = new StringBuilder();

            // Lambda expression to read and process lines
            reader.lines().forEach(line -> {
                // Handle each line accordingly
                if (!line.isEmpty()) {    // ignore empty lines
                    if (isCommand(line)) {    // process command lines
                        if (!stringBuilder.isEmpty()) {
                            evalStatus[0] &= processCode(this, stringBuilder);
                            stringBuilder.setLength(0);
                        }
                        evalStatus[0] &= processCommand(this, line);
                    } else {    // process source code lines
                        // join for code section processing later
                        stringBuilder.append(line).append("\n");
                    }
                }
            });

            // Process the final source code section.
            if (!stringBuilder.isEmpty()) {
                evalStatus[0] &= processCode(this, stringBuilder);
            }
        } catch (IOException ioe) {
            evalStatus[0] &= processException(this, ioe);
        }

        return new EvaluationResult(evalStatus[0], evalMessage.toString());
    }

    private void initFeedback() {
        // Execute the feedback initialization code in the resource file
        startUpRun(Messenger.ResourceKeys.resource("startup.feedback"));

        // These predefined modes are read-only
        feedback.markModesReadOnly();

        // Restore user defined modes retained
        // on previous run with /set mode -retain
        String encoded = prefs.get(MODE_KEY);
        if (encoded != null && !encoded.isEmpty()) {
            if (!feedback.restoreEncodedModes(messenger, encoded)) {
                // Catastrophic corruption -- remove the retained modes
                prefs.remove(MODE_KEY);
            }
        }

        String fb = prefs.get(FEEDBACK_KEY);
        if (fb != null) {
            // Restore the feedback mode to use that was retained
            // on a previous run with /set feedback -retain
            setFeedback(new ArgTokenizer("previous retain feedback", "-retain " + fb));
        }
    }

    private void startUpRun(String commandSource) {
        try (BufferedReader reader =
                     new BufferedReader(new StringReader(commandSource))) {
            reader.lines().forEach(line -> {
                if (!line.isEmpty()) {    // ignore empty lines
                    if (isCommand(line)) {    // process command lines
                        processCommand(this, line);
                    }   // ignore non-command lines
                }
            });
        } catch (IOException ioe) {
            messenger.msg("err.startup.unexpected.exception", ioe);
            processException(this, ioe);
        }
    }

    private boolean isCommand(String line) {
        return line.startsWith("/") &&
                !line.startsWith("//") &&
                !line.startsWith("/*");
    }

    // Supported command
    // -- help/?
    // -- /!
    // -- JShell.snippets -- list
    // -- JShell.drop
    // -- JShell.imports
    // -- JShell.methods
    // -- JShell.types
    // -- JShell.variables
    // -- env
    // -- reset
    // -- set
    //    -- feedback
    //    -- format
    //    -- mode
    //    -- prompt
    // -- exit
    private static boolean processCommand(Evaluator ev, String input) {
        String cmd;
        String arg;
        int idx = input.indexOf(' ');
        if (idx > 0) {
            arg = input.substring(idx + 1).trim();
            cmd = input.substring(0, idx);
        } else {
            cmd = input;
            arg = "";
        }

        // find the command as a "real command", not a pseudo-command or doc subject
        Command[] candidates = Command.Commands.findCommand(cmd, c -> c.kind.isRealCommand);
        if (candidates.length != 1) {
            if (candidates.length == 0) {
                ev.messenger.msg("err.invalid.command", cmd);
            } else {
                // command is too short (ambiguous), show the possibly matches
                ev.messenger.msg("err.command.ambiguous", cmd,
                        Arrays.stream(candidates).map(c -> c.command).collect(Collectors.joining(", ")));
            }
            ev.messenger.msg("msg.help.for.help");
        } else {
            return candidates[0].apply(ev, arg);
        }

        return false;
    }

    private boolean processCode(
            Evaluator ev,
            StringBuilder stringBuilder) {
        boolean opStatus = true;
        SourceCodeAnalysis.CompletionInfo ci;
        for (ci = analysis.analyzeCompletion(stringBuilder.toString());
             ci.completeness().isComplete();
             ci = analysis.analyzeCompletion(ci.remaining())) {
            // Process snippet by snippet
            for (SnippetEvent event : ev.jShell.eval(ci.source())) {
                List<Diag> diagnostics = ev.jShell.diagnostics(event.snippet()).toList();
                opStatus &= processEvent(ev, event, diagnostics);
            }
        }

        // incomplete source code
        if (ci.completeness() != SourceCodeAnalysis.Completeness.EMPTY) {
            opStatus &= processIncomplete(ev.messenger, ci.remaining());
        }

        return opStatus;
    }

    private boolean processIncomplete(Messenger messenger, String source) {
        messenger.ops().println("Incomplete input:");
        messenger.ops().println(source);

        return false;
    }

    private static boolean processEvent(
            Evaluator ev,
            SnippetEvent se, List<Diag> diagnostics) {
        processDiagnostics(ev.messenger, se.snippet().source(), diagnostics);

        if (se.causeSnippet() == null) {    // main event
            if (se.status() != Snippet.Status.REJECTED) {
                if (se.exception() != null) {
                    return processException(ev, se.exception());
                } else {
                    new DisplayEvent(ev, se, Selector.FormatWhen.PRIMARY, se.value(), diagnostics).displayDeclarationAndValue();
                    return true;
                }
            } else {
                // the snippet was rejected.
                ev.messenger.msg("err.failed");
                return false;
            }
        } else {    // update
            Snippet sn = se.snippet();
            if (sn instanceof DeclarationSnippet) {
                new DisplayEvent(ev, se, Selector.FormatWhen.UPDATE, se.value(), errorsOnly(diagnostics))
                        .displayDeclarationAndValue();
            }

            return false;
        }
    }

    private static void processDiagnostics(
            Messenger messenger, String source, List<Diag> diagnostics) {
        for (Diag d : diagnostics) {
            messenger.msg(d.isError() ? "msg.error" : "msg.warning");
            processDiagnostic(messenger, source, d);
        }
    }

    private static void processDiagnostic(
            Messenger messenger, String source, Diag diag) {
        for (String line : diag.getMessage(Locale.ENGLISH).split("\\r?\\n")) {
            if (!line.trim().startsWith("location:")) {
                messenger.ops().println(line);
            }
        }

        int pstart = (int) diag.getStartPosition();
        int pend = (int) diag.getEndPosition();
        if (pstart < 0 || pend < 0) {
            pstart = 0;
            pend = source.length();
        }
        Matcher m = LineBreakPattern.matcher(source);
        int pstartl = 0;
        int pendl = -2;
        while (m.find(pstartl)) {
            pendl = m.start();
            if (pendl >= pstart) {
                break;
            } else {
                pstartl = m.end();
            }
        }
        if (pendl < pstartl) {
            pendl = source.length();
        }
        messenger.ops().println(source.substring(pstartl, pendl));

        StringBuilder sb = new StringBuilder();
        int start = pstart - pstartl;
        sb.append(" ".repeat(Math.max(0, start)));
        sb.append('^');
        boolean multiline = pend > pendl;
        int end = (multiline ? pendl : pend) - pstartl - 1;
        if (end > start) {
            sb.append("-".repeat(Math.max(0, end - (start + 1))));
            if (multiline) {
                sb.append("-...");
            } else {
                sb.append('^');
            }
        }
        messenger.ops().println(sb);
    }

    private static Stream<String> processDiagnostic(
            String source, List<Diag> diagnostics) {
        OutputStream os = new ByteArrayOutputStream();
        for (Diag diag : diagnostics) {
            processDiagnostic(new Messenger(new PrintStream(os)), source, diag);
        }

        BufferedReader bufferedReader =
                new BufferedReader(new StringReader(os.toString()));
        return bufferedReader.lines();
    }

    private static boolean processException(Evaluator ev, Exception ex) {
        if (ex instanceof EvalException evex) {
            Throwable cause = evex.getCause();
            if (cause instanceof UnresolvedReferenceException) {
                return ev.displayException(cause, null);
            }
        }

        return ev.displayException(ex, null);
    }

    private boolean displayException(Throwable exception, StackTraceElement[] caused) {
        if (exception instanceof EvalException evex) {
            // User exception
            return displayEvalException(evex, caused);
        } else if (exception instanceof UnresolvedReferenceException srex) {
            // Reference to an undefined snippet
            return displayUnresolvedException(srex);
        } else {
            // Should never occur
            messenger.out("Unexpected execution exception: %s", exception);
            return true;
        }
    }

    private boolean displayUnresolvedException(UnresolvedReferenceException ex) {
        // Display the resolution issue
        printSnippetStatus(ex.getSnippet(), false);
        return false;
    }

    void printSnippetStatus(DeclarationSnippet sn, boolean resolve) {
        List<Diag> otherErrors = errorsOnly(jShell.diagnostics(sn).toList());
        new DisplayEvent(this, sn, jShell.status(sn), resolve, otherErrors)
                .displayDeclarationAndValue();
    }

    private boolean displayEvalException(EvalException ex, StackTraceElement[] caused) {
        // The message for the user exception is configured based on the
        // existence of an exception message and if this is a recursive
        // invocation for a chained exception.
        String msg = ex.getMessage();
        String key = "err.exception" +
                (caused == null? ".thrown" : ".cause") +
                (msg == null? "" : ".message");
        messenger.msg(key, ex.getExceptionClassName(), msg);
        // The caused trace is sent to truncate duplicate elements in the cause trace
        printStackTrace(ex.getStackTrace(), caused);
        JShellException cause = ex.getCause();
        if (cause != null) {
            // Display the cause (recursively)
            displayException(cause, ex.getStackTrace());
        }
        return true;
    }

    void printStackTrace(StackTraceElement[] stes, StackTraceElement[] caused) {
        int overlap = 0;
        if (caused != null) {
            int maxOverlap = Math.min(stes.length, caused.length);
            while (overlap < maxOverlap
                    && stes[stes.length - (overlap + 1)].equals(caused[caused.length - (overlap + 1)])) {
                ++overlap;
            }
        }
        for (int i = 0; i < stes.length - overlap; ++i) {
            StackTraceElement ste = stes[i];
            StringBuilder sb = getStringBuilder(ste);
            String fileName = ste.getFileName();
            int lineNumber = ste.getLineNumber();
            String loc = ste.isNativeMethod()
                    ? Messenger.ResourceKeys.resource("msg.native.method")
                    : fileName == null
                    ? Messenger.ResourceKeys.resource("msg.unknown.source")
                    : lineNumber >= 0
                    ? fileName + ":" + lineNumber
                    : fileName;
            messenger.out("      at %s(%s)", sb, loc);

        }
        if (overlap != 0) {
            messenger.out("      ...");
        }
    }

    private static StringBuilder getStringBuilder(StackTraceElement ste) {
        StringBuilder sb = new StringBuilder();
        String cn = ste.getClassName();
        if (!cn.isEmpty()) {
            int dot = cn.lastIndexOf('.');
            if (dot > 0) {
                sb.append(cn.substring(dot + 1));
            } else {
                sb.append(cn);
            }
            sb.append(".");
        }
        if (!ste.getMethodName().isEmpty()) {
            sb.append(ste.getMethodName());
            sb.append(" ");
        }
        return sb;
    }

    @Override
    public void close() {
        shutdownConsumer.accept(this);
        jShell.close();
    }

    private static class DisplayEvent {
        private final Evaluator ev;
        private final Snippet sn;
        private final Selector.FormatAction action;
        private final Selector.FormatWhen update;
        private final String value;
        private final Stream<String> errorLines;
        private final Selector.FormatResolve resolution;
        private final String unresolved;
        private final Selector.FormatUnresolved unrcnt;
        private final Selector.FormatErrors errcnt;
        private final boolean resolve;

        DisplayEvent(Evaluator ev, SnippetEvent ste,
                Selector.FormatWhen update, String value, List<Diag> errors) {
            this(ev, ste.snippet(), ste.status(), false,
                    toAction(ste.status(), ste.previousStatus(), ste.isSignatureChange()),
                    update, value, errors);
        }

        DisplayEvent(Evaluator ev,
                Snippet sn, Snippet.Status status, boolean resolve, List<Diag> errors) {
            this(ev, sn, status, resolve,
                    Selector.FormatAction.USED,
                    Selector.FormatWhen.UPDATE, null, errors);
        }

        private DisplayEvent(Evaluator ev,
                Snippet sn, Snippet.Status status, boolean resolve,
                Selector.FormatAction action,
                Selector.FormatWhen update, String value, List<Diag> errors) {
            this.ev = ev;
            this.sn = sn;
            this.resolve =resolve;
            this.action = action;
            this.update = update;
            this.value = value;
            if (resolve) {
                this.errorLines = processDiagnostic(sn.source(), errors)
                        .map(string -> DEFAULT_INDENT + string);
            } else {
                this.errorLines = processDiagnostic(sn.source(), errors);
            }

            long unresolvedCount;
            if (sn instanceof DeclarationSnippet && (status == Snippet.Status.RECOVERABLE_DEFINED || status == Snippet.Status.RECOVERABLE_NOT_DEFINED)) {
                resolution = (status == Snippet.Status.RECOVERABLE_NOT_DEFINED)
                        ? Selector.FormatResolve.NOT_DEFINED
                        : Selector.FormatResolve.DEFINED;
                unresolved = unresolved((DeclarationSnippet) sn);
                unresolvedCount = ev.jShell.unresolvedDependencies((DeclarationSnippet) sn).count();
            } else {
                resolution = Selector.FormatResolve.OK;
                unresolved = "";
                unresolvedCount = 0;
            }
            unrcnt = unresolvedCount == 0
                    ? Selector.FormatUnresolved.UNRESOLVED0
                    : unresolvedCount == 1
                    ? Selector.FormatUnresolved.UNRESOLVED1
                    : Selector.FormatUnresolved.UNRESOLVED2;
            errcnt = errors.isEmpty()
                    ? Selector.FormatErrors.NO_ERROR
                    : errors.size() == 1
                    ? Selector.FormatErrors.ONE_ERROR
                    : Selector.FormatErrors.MORE_ERROR;
        }

        private String unresolved(DeclarationSnippet key) {
            List<String> unr = ev.jShell.unresolvedDependencies(key).toList();
            StringBuilder sb = new StringBuilder();
            int fromLast = unr.size();
            if (fromLast > 0) {
                sb.append(" ");
            }
            for (String u : unr) {
                --fromLast;
                sb.append(u);
                if (fromLast == 1) {
                    sb.append(", and ");
                } else if (fromLast != 0) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }

        private void custom(Selector.FormatCase fcase, String name) {
            custom(fcase, name, null);
        }

        private void custom(Selector.FormatCase fcase, String name, String type) {
            if (resolve) {
                String resolutionErrors = ev.feedback.format("resolve", fcase, action, update,
                        resolution, unrcnt, errcnt,
                        name, type, value, unresolved, errorLines);
                if (!resolutionErrors.trim().isEmpty()) {
                    ev.messenger.log("    %s", resolutionErrors);
                }
            // } else if (interactive()) {
            } else {
                String display = ev.feedback.format(fcase, action, update,
                        resolution, unrcnt, errcnt,
                        name, type, value, unresolved, errorLines);
                ev.messenger.ops().print(display);
            }
        }

        @SuppressWarnings("fallthrough")
        private void displayDeclarationAndValue() {
            switch (sn.subKind()) {
                case CLASS_SUBKIND ->
                    custom(Selector.FormatCase.CLASS, ((TypeDeclSnippet) sn).name());
                case INTERFACE_SUBKIND ->
                    custom(Selector.FormatCase.INTERFACE, ((TypeDeclSnippet) sn).name());
                case ENUM_SUBKIND ->
                    custom(Selector.FormatCase.ENUM, ((TypeDeclSnippet) sn).name());
                case ANNOTATION_TYPE_SUBKIND ->
                    custom(Selector.FormatCase.ANNOTATION, ((TypeDeclSnippet) sn).name());
                case RECORD_SUBKIND ->
                    custom(Selector.FormatCase.RECORD, ((TypeDeclSnippet) sn).name());
                case METHOD_SUBKIND ->
                    custom(Selector.FormatCase.METHOD, ((MethodSnippet) sn).name(), ((MethodSnippet) sn).parameterTypes());
                case VAR_DECLARATION_SUBKIND -> {
                    VarSnippet vk = (VarSnippet) sn;
                    custom(Selector.FormatCase.VARDECL, vk.name(), vk.typeName());
                }
                case VAR_DECLARATION_WITH_INITIALIZER_SUBKIND -> {
                    VarSnippet vk = (VarSnippet) sn;
                    custom(Selector.FormatCase.VARINIT, vk.name(), vk.typeName());
                }
                case TEMP_VAR_EXPRESSION_SUBKIND -> {
                    VarSnippet vk = (VarSnippet) sn;
                    custom(Selector.FormatCase.EXPRESSION, vk.name(), vk.typeName());
                }
                case OTHER_EXPRESSION_SUBKIND ->
                    ev.messenger.log("Unexpected expression form -- value is: %s", (value));
                case VAR_VALUE_SUBKIND -> {
                    ExpressionSnippet ek = (ExpressionSnippet) sn;
                    custom(Selector.FormatCase.VARVALUE, ek.name(), ek.typeName());
                }
                case ASSIGNMENT_SUBKIND -> {
                    ExpressionSnippet ek = (ExpressionSnippet) sn;
                    custom(Selector.FormatCase.ASSIGNMENT, ek.name(), ek.typeName());
                }
                case SINGLE_TYPE_IMPORT_SUBKIND,
                    TYPE_IMPORT_ON_DEMAND_SUBKIND,
                    SINGLE_STATIC_IMPORT_SUBKIND,
                    STATIC_IMPORT_ON_DEMAND_SUBKIND ->
                    custom(Selector.FormatCase.IMPORT, ((ImportSnippet) sn).name());
                case STATEMENT_SUBKIND ->
                    custom(Selector.FormatCase.STATEMENT, null);
            }
        }
    }

    private static Selector.FormatAction toAction(Snippet.Status status,
            Snippet.Status previousStatus, boolean isSignatureChange) {
        return switch (status) {
            case VALID,
                RECOVERABLE_DEFINED,
                RECOVERABLE_NOT_DEFINED -> {
                if (previousStatus.isActive()) {
                    yield isSignatureChange
                            ? Selector.FormatAction.REPLACED
                            : Selector.FormatAction.MODIFIED;
                } else {
                    yield Selector.FormatAction.ADDED;
                }
            }
            case OVERWRITTEN ->
                 Selector.FormatAction.OVERWROTE;
            case DROPPED ->
                 Selector.FormatAction.DROPPED;
            default ->
                // Unexpected status, should not occur.
                 Selector.FormatAction.DROPPED;
        };
    }

    public static boolean cmdList(Evaluator ev, String arg) {
        Stream<Snippet> stream = argsOptionsToSnippets(ev,
                ev.jShell::snippets, ev::isActive, arg, "/list");
        if (stream == null) {
            return false;
        }

        // prevent double newline on empty list
        boolean[] hasOutput = new boolean[1];
        stream.forEachOrdered(sn -> {
            if (!hasOutput[0]) {
                ev.messenger.ops().println();
                hasOutput[0] = true;
            }
            ev.messenger.ops().printf("%4s : %s\n",
                    sn.id(), sn.source().replace("\n", "\n       "));
        });

        return true;
    }

    private boolean isActive(Snippet sn) {
        return jShell.status(sn).isActive();
    }

    private static <T extends Snippet> Stream<T> argsOptionsToSnippets(
            Evaluator ev, Supplier<Stream<T>> snippetSupplier,
            Predicate<Snippet> defFilter,
            String rawArgs, String cmd) {
        ArgTokenizer at = new ArgTokenizer(cmd, rawArgs.trim());
        at.allowedOptions("-all", "-start");
        return argsOptionsToSnippets(ev, snippetSupplier, defFilter, at);
    }

    private static <T extends Snippet> Stream<T> argsOptionsToSnippets(
            Evaluator ev,
            Supplier<Stream<T>> snippetSupplier,
            Predicate<Snippet> defFilter,
            ArgTokenizer at) {
        List<String> args = new ArrayList<>();
        String s;
        while ((s = at.next()) != null) {
            args.add(s);
        }
        if (ev.argHasBadOptionsOrRemaining(at)) {
            return null;
        }
        if (at.optionCount() > 0 && !args.isEmpty()) {
            ev.messenger.msg("err.may.not.specify.options.and.snippets", at.whole());
            return null;
        }
        if (at.optionCount() > 1) {
            ev.messenger.msg("err.conflicting.options", at.whole());
            return null;
        }

        if (at.isAllowedOption("-all") && at.hasOption("-all")) {
            // all snippets including start-up, failed, and overwritten
            return snippetSupplier.get();
        }

        if (args.isEmpty()) {
            // Default is all active user snippets
            return snippetSupplier.get().filter(defFilter);
        }

        return new ArgToSnippets<>(ev, snippetSupplier).argsToSnippets(args);
    }

    private static class ArgToSnippets<T extends Snippet> {
        Evaluator ev;

        // the supplier of snippet streams
        private final Supplier<Stream<T>> snippetSupplier;

        // these two are parallel, and lazily filled if a range is encountered
        List<T> allSnippets;
        String[] allIds = null;

        /**
         * @param snippetSupplier the base list of possible snippets
         */
        ArgToSnippets(Evaluator ev, Supplier<Stream<T>> snippetSupplier) {
            this.ev = ev;
            this.snippetSupplier = snippetSupplier;
        }

        /**
         * Convert user arguments to a Stream of snippets referenced by those
         * arguments.
         *
         * @param args the user's argument to the command, maybe be the empty
         * list
         * @return a Stream of referenced snippets or null if no matches to
         * specific arg
         */
        Stream<T> argsToSnippets(List<String> args) {
            Stream<T> result = null;
            for (String arg : args) {
                // Find the best match
                Stream<T> st = argToSnippets(arg);
                if (st == null) {
                    return null;
                } else {
                    result = (result == null)
                            ? st
                            : Stream.concat(result, st);
                }
            }
            return result;
        }

        /**
         * Convert a user argument to a Stream of snippets referenced by the
         * argument.
         *
         * @param arg the user's argument to the command
         * @return a Stream of referenced snippets or null if no matches to
         * specific arg
         */
        Stream<T> argToSnippets(String arg) {
            if (arg.contains("-")) {
                return range(arg);
            }
            // Find the best match
            Stream<T> st = layeredSnippetSearch(snippetSupplier, arg);
            if (st == null) {
                badSnippetErrorMsg(arg);
                return null;
            } else {
                return st;
            }
        }

        /**
         * Look for inappropriate snippets to give the best error message.
         *
         * @param arg the bad snippet arg
         */
        void badSnippetErrorMsg(String arg) {
            Stream<Snippet> est = layeredSnippetSearch(ev.jShell::snippets, arg);
            if (est == null) {
                if (ID.matcher(arg).matches()) {
                    ev.messenger.msg("err.no.snippet.with.id", arg);
                } else {
                    ev.messenger.msg("err.no.such.snippets", arg);
                }
            } else {
                est.findFirst().ifPresent(snippet ->
                        ev.messenger.msg("err.the.snippet.cannot.be.used.with.this.command",
                        arg, snippet.source()));
            }
        }

        /**
         * Search through the snippets for the best match to the id/name.
         *
         * @param <R> the snippet type
         * @param aSnippetSupplier the supplier of snippet streams
         * @param arg the arg to match
         * @return a Stream of referenced snippets or null if no matches to
         * specific arg
         */
        <R extends Snippet> Stream<R> layeredSnippetSearch(
                Supplier<Stream<R>> aSnippetSupplier, String arg) {
            return nonEmptyStream(
                    // the stream supplier
                    aSnippetSupplier,
                    // look for active user declarations matching the name
                    sn -> ev.isActive(sn) && matchingDeclaration(sn, arg),
                    // else, look for any declarations matching the name
                    sn -> matchingDeclaration(sn, arg),
                    // else, look for an id of this name
                    sn -> sn.id().equals(arg)
            );
        }

        /**
         * Given an id1-id2 range specifier, return a stream of snippets within
         * our context
         *
         * @param arg the range arg
         * @return a Stream of referenced snippets or null if no matches to
         * specific arg
         */
        Stream<T> range(String arg) {
            int dash = arg.indexOf('-');
            String iid = arg.substring(0, dash);
            String tid = arg.substring(dash + 1);
            int iidx = snippetIndex(iid);
            if (iidx < 0) {
                return null;
            }
            int tidx = snippetIndex(tid);
            if (tidx < 0) {
                return null;
            }
            if (tidx < iidx) {
                ev.messenger.msg("err.end.snippet.range.less.than.start", iid, tid);
                return null;
            }
            return allSnippets.subList(iidx, tidx+1).stream();
        }

        /**
         * Lazily initialize the id mapping -- needed only for id ranges.
         */
        void initIdMapping() {
            if (allIds == null) {
                allSnippets = snippetSupplier.get()
                        .sorted((a, b) -> order(a) - order(b))
                        .toList();
                allIds = allSnippets.stream()
                        .map(Snippet::id)
                        .toArray(String[]::new);
            }
        }

        /**
         * Return all the snippet ids -- within the context, and in order.
         *
         * @return the snippet ids
         */
        String[] allIds() {
            initIdMapping();
            return allIds;
        }

        /**
         * Establish an order on snippet ids.  All startup snippets are first,
         * all error snippets are last -- within that is by snippet number.
         *
         * @param id the id string
         * @return an ordering int
         */
        int order(String id) {
            try {
                return switch (id.charAt(0)) {
                    case 's' -> Integer.parseInt(id.substring(1));
                    case 'e' -> 0x40000000 + Integer.parseInt(id.substring(1));
                    default -> 0x20000000 + Integer.parseInt(id);
                };
            } catch (Exception ex) {
                return 0x60000000;
            }
        }

        /**
         * Establish an order on snippets, based on its snippet id. All startup
         * snippets are first, all error snippets are last -- within that is by
         * snippet number.
         *
         * @param sn the id string
         * @return an ordering int
         */
        int order(Snippet sn) {
            return order(sn.id());
        }

        /**
         * Find the index into the parallel allSnippets and allIds structures.
         *
         * @param s the snippet id name
         * @return the index, or, if not found, report the error and return a
         * negative number
         */
        int snippetIndex(String s) {
            int idx = Arrays.binarySearch(allIds(), 0, allIds().length, s,
                    (a, b) -> order(a) - order(b));
            if (idx < 0) {
                // the id is not in the snippet domain, find the right error to report
                if (!ID.matcher(s).matches()) {
                    ev.messenger.msg("err.range.requires.id", s);
                } else {
                    badSnippetErrorMsg(s);
                }
            }
            return idx;
        }
    }

    private static boolean matchingDeclaration(Snippet sn, String name) {
        return sn instanceof DeclarationSnippet dsn && dsn.name().equals(name);
    }

    private interface SnippetPredicate<T extends Snippet> extends Predicate<T> {
        // blank
    }

    @SafeVarargs
    private static <T extends Snippet> Stream<T> nonEmptyStream(
            Supplier<Stream<T>> supplier,
            SnippetPredicate<T>... filters) {
        for (SnippetPredicate<T> filter : filters) {
            Iterator<T> iterator = supplier.get().filter(filter).iterator();
            if (iterator.hasNext()) {
                return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, 0), false);
            }
        }
        return null;
    }

    private boolean argHasBadOptionsOrRemaining(ArgTokenizer at) {
        String junk = at.remainder();
        if (!junk.isEmpty()) {
            messenger.msg("err.unexpected.at.end", junk, at.whole());
            return true;
        } else {
            String bad = at.badOptions();
            if (!bad.isEmpty()) {
                messenger.msg("err.unknown.option", bad, at.whole());
                return true;
            }
        }
        return false;
    }

    public static boolean cmdDrop(Evaluator ev, String rawArgs) {
        ArgTokenizer at = new ArgTokenizer("/drop", rawArgs.trim());
        at.allowedOptions();
        List<String> args = new ArrayList<>();
        String s;
        while ((s = at.next()) != null) {
            args.add(s);
        }
        if (ev.argHasBadOptionsOrRemaining(at)) {
            return false;
        }
        if (args.isEmpty()) {
            ev.messenger.msg("err.drop.arg");
            return false;
        }

        Stream<Snippet> stream = new ArgToSnippets<>(ev, ev::activeSnippets).argsToSnippets(args);
        if (stream == null) {
            // Snippet not found. Error already printed
            ev.messenger.msg("msg.see.classes.etc");
            return false;
        }
        stream.forEach(sn -> ev.jShell.drop(sn).forEach(ev::processEvent));

        return true;
    }

    private void processEvent(SnippetEvent event) {
        List<Diag> diagnostics = jShell.diagnostics(event.snippet()).toList();
        processEvent(this, event, diagnostics);
    }

    Stream<Snippet> activeSnippets() {
        return jShell.snippets()
                .filter(sn -> jShell.status(sn).isActive());
    }

    public static boolean cmdVars(Evaluator ev, String arg) {
        Stream<VarSnippet> stream = argsOptionsToSnippets(ev, ev::varSnippets,
                ev::isActive, arg, "/vars");
        if (stream == null) {
            return false;
        }
        stream.forEachOrdered(vk -> {
            String val = ev.jShell.status(vk) == Snippet.Status.VALID
                    ? ev.feedback.truncateVarValue(ev.jShell.varValue(vk))
                    : Messenger.ResourceKeys.resource("msg.vars.not.active");
            String varName = vk.name();
            ev.messenger.out("  %s %s = %s\n", vk.typeName(), varName.isEmpty() ? "_" : varName, val);
        });

        return true;
    }

    Stream<VarSnippet> varSnippets() {
        return jShell.snippets()
                .filter(sn -> sn.kind() == Snippet.Kind.VAR)
                .map(sn -> (VarSnippet) sn);
    }

    public static boolean cmdMethods(Evaluator ev, String arg) {
        Stream<MethodSnippet> stream = argsOptionsToSnippets(ev, ev::allMethodSnippets,
                ev::isActive, arg, "/methods");
        if (stream == null) {
            return false;
        }
        stream.forEachOrdered(meth -> {
            String note = ev.jShell.status(meth) == Snippet.Status.VALID
                    ? ""
                    : Messenger.ResourceKeys.resource("msg.methods.not.active");

            String sig = meth.signature();
            int i = sig.lastIndexOf(")") + 1;
            if (i <= 0) {
                ev.messenger.out("  %s%s\n", meth.name(), note);
            } else {
                ev.messenger.out("  %s %s%s%s\n", sig.substring(i), meth.name(), sig.substring(0, i), note);
            }
            ev.printSnippetStatus(meth, true);
        });

        return true;
    }

    private static List<Diag> errorsOnly(List<Diag> diagnostics) {
        return diagnostics.stream()
                .filter(Diag::isError)
                .toList();
    }

    private Stream<MethodSnippet> allMethodSnippets() {
        return jShell.snippets()
                .filter(sn -> sn.kind() == Snippet.Kind.METHOD)
                .map(sn -> (MethodSnippet) sn);
    }

    public static boolean cmdTypes(Evaluator ev, String arg) {
        Stream<TypeDeclSnippet> stream = argsOptionsToSnippets(ev, ev::allTypeSnippets,
                ev::isActive, arg, "/types");
        if (stream == null) {
            return false;
        }
        stream.forEachOrdered(ck -> {
            String note = ev.jShell.status(ck) == Snippet.Status.VALID
                    ? ""
                    : Messenger.ResourceKeys.resource("msg.types.not.active");

            String kind = switch (ck.subKind()) {
                case INTERFACE_SUBKIND -> "interface";
                case CLASS_SUBKIND -> "class";
                case ENUM_SUBKIND -> "enum";
                case ANNOTATION_TYPE_SUBKIND -> "@interface";
                case RECORD_SUBKIND -> "record";
                default -> {
                    assert false : "Wrong kind" + ck.subKind();
                    yield "class";
                }
            };
            ev.messenger.log("  %s %s%s\n", kind, ck.name(), note);
            ev.printSnippetStatus(ck, true);
        });

        return true;
    }

    Stream<TypeDeclSnippet> allTypeSnippets() {
        return jShell.snippets()
                .filter(sn -> sn.kind() == Snippet.Kind.TYPE_DECL)
                .map(sn -> (TypeDeclSnippet) sn);
    }

    public static boolean cmdHelp(Evaluator ev, String arg) {
        ArgTokenizer at = new ArgTokenizer("/help", arg);
        String subject = at.next();
        if (subject != null) {
            // check if the requested subject is a help subject or
            // a command, with or without slash
            Command[] matches = Command.Commands.stream()
                    .filter(c -> c.command.startsWith(subject)
                            || c.command.substring(1).startsWith(subject))
                    .toArray(Command[]::new);
            if (matches.length == 1) {
                String cmd = matches[0].command;
                if (cmd.equals("/set")) {
                    // Print the help doc for the specified sub-command
                    String which = subCommand(ev.messenger, cmd, at);
                    if (which == null) {
                        return false;
                    }
                    if (!which.equals("_blank")) {
                        printHelp(ev.messenger, "/set " + which, "help.set." + which);
                        return true;
                    }
                }
            }
            if (matches.length > 0) {
                for (Command c : matches) {
                    printHelp(ev.messenger, c.command, c.helpKey);
                }
                return true;
            } else {
                // failing everything else, check if this is the start of
                // a /set sub-command name
                String[] subs = Arrays.stream(SET_SUBCOMMANDS)
                        .filter(s -> s.startsWith(subject))
                        .toArray(String[]::new);
                if (subs.length > 0) {
                    for (String sub : subs) {
                        printHelp(ev.messenger, "/set " + sub, "help.set." + sub);
                    }
                    return true;
                }
                ev.messenger.msg("err.help.arg", arg);
            }
        }
        ev.messenger.msg("msg.help.begin");
        hardPairs(ev,
                Command.Commands.stream()
                        .filter(cmd -> cmd.kind.showInHelp),
                cmd -> cmd.command + " " + Messenger.ResourceKeys.resource(cmd.helpKey + ".args"),
                cmd -> Messenger.ResourceKeys.resource(cmd.helpKey + ".summary")
        );
        ev.messenger.msg("msg.help.subject");
        hardPairs(ev,
                Command.Commands.stream()
                        .filter(cmd -> cmd.kind == Command.CommandKind.HELP_SUBJECT),
                cmd -> cmd.command,
                cmd -> Messenger.ResourceKeys.resource(cmd.helpKey + ".summary")
        );

        return true;
    }

    private static String subCommand(
            Messenger messenger, String cmd, ArgTokenizer at) {
        at.allowedOptions("-retain");
        String sub = at.next();
        if (sub == null) {
            // No sub-command was given
            return at.hasOption("-retain")
                    ? "_retain"
                    : "_blank";
        }
        String[] matches = Arrays.stream(Evaluator.SET_SUBCOMMANDS)
                .filter(s -> s.startsWith(sub))
                .toArray(String[]::new);
        if (matches.length == 0) {
            // There are no matching sub-commands
            messenger.msg("err.arg", cmd, sub);
            messenger.msg("msg.use.one.of", String.join(", ", Evaluator.SET_SUBCOMMANDS));
            return null;
        }
        if (matches.length > 1) {
            // More than one sub-command matches the initial characters provided
            messenger.msg("err.sub.ambiguous", cmd, sub);
            messenger.msg("msg.use.one.of", String.join(", ", matches));
            return null;
        }
        return matches[0];
    }

    private static void printHelp(Messenger messenger, String name, String key) {
        int len = name.length();
        String centered = "%" + ((OUTPUT_WIDTH + len) / 2) + "s";
        // messenger.out("\n");
        messenger.out(centered, name);
        // messenger.out("\n");
        messenger.out(centered, Stream.generate(() -> "=").limit(len).collect(Collectors.joining()));
        // messenger.out("\n");
        messenger.out(Messenger.ResourceKeys.resource(key));
    }

    private static <T> void hardPairs(
            Evaluator ev,
            Stream<T> stream, Function<T, String> a, Function<T, String> b) {
        Map<String, String> a2b = stream.collect(Collectors.toMap(a, b,
                (m1, m2) -> m1,
                LinkedHashMap::new));
        for (Map.Entry<String, String> e : a2b.entrySet()) {
            ev.messenger.out("%s", e.getKey());
            // ev.messenger.ops().printf(ev.prefix(e.getValue(), ev.feedback.getPre() + "\t", ev.feedback.getPost()));
            String value = e.getValue();
            if (value != null) {    // add intent and new line
                ev.messenger.ops().println("    " + value + "\n");
            }
        }
    }

    public static boolean cmdImports(Evaluator ev, String arg) {
        ev.jShell.imports().forEach(ik ->
                ev.messenger.out("  import %s%s",
                        ik.isStatic() ? "static " : "", ik.fullname()));

        return true;
    }

    public static boolean cmdExit(Evaluator ev, String arg) {
        ev.messenger.msg("msg.goodbye");
        ev.close();
        return true;
    }

    public static boolean cmdEnv(Evaluator ev, String args) {
        if (args.trim().isEmpty()) {
            // No arguments, display current settings (as option flags)
            StringBuilder sb = new StringBuilder();
            for (String a : ev.options.shownOptions()) {
                sb.append(
                        a.startsWith("-")
                                ? !sb.isEmpty()
                                ? "\n   "
                                :   "   "
                                : " ");
                sb.append(a);
            }
            if (!sb.isEmpty()) {
                ev.messenger.out(sb.toString());
            }
            return false;
        }

        if (ev.hasIllegalArgs(args, new OptionParserBase(ev))) {
            return false;
        }

        ev.messenger.msg("msg.set.restore");
        return ev.reset();
    }

    private boolean hasIllegalArgs(String rawArgs, OptionParserBase ap) {
        String[] args = Arrays.stream(rawArgs.split("\\s+"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        Options opts = ap.parse(args);
        if (opts == null) {
            return true;
        }
        if (!ap.nonOptions().isEmpty()) {
            messenger.msg("err.unexpected.at.end", ap.nonOptions(), rawArgs);
            return true;
        }

        // update the options field
        opts.optMap.forEach((key, value) -> {
            if (key.onlyOne) {
                // Only one allowed, override last
                options.optMap.put(key, value);
            } else {
                // Additive
                options.addAll(key, value);
            }
        });

        return false;
    }

    private boolean reset() {
        closeState();

        PrintStream ops = new PrintStream(evalMessage);
        this.jShell = JShell.builder()
                .out(ops)
                .err(ops)
                .remoteVMOptions(options.remoteVmOptions())
                .compilerOptions(options.compilerOptions())
                .build();
        this.jShell.onShutdown(this::onShutdown);
        this.analysis = jShell.sourceCodeAnalysis();
        this.messenger = new Messenger(ops);

        return true;
    }

    private void onShutdown(JShell deadState) {
        if (deadState == jShell) {
            messenger.msg("msg.terminated");
        }
    }

    private void closeState() {
        if (jShell != null) {
            try {
                evalMessage.reset();
            } finally {
                jShell.close();
            }

            jShell = null;
        }
    }

    // compiler/runtime init option values
    private static class Options {
        private final Map<OptionKind, List<String>> optMap;

        // New blank Options
        Options() {
            optMap = new HashMap<>();
        }

        private String[] selectOptions(Predicate<Map.Entry<OptionKind, List<String>>> pred) {
            return optMap.entrySet().stream()
                    .filter(pred)
                    .flatMap(e -> e.getValue().stream())
                    .toArray(String[]::new);
        }

        String[] remoteVmOptions() {
            return selectOptions(e -> e.getKey().toRemoteVm);
        }

        String[] compilerOptions() {
            return selectOptions(e -> e.getKey().toCompiler);
        }

        String[] shownOptions() {
            return selectOptions(e -> e.getKey().showOption);
        }

        void addAll(OptionKind kind, Collection<String> vals) {
            optMap.computeIfAbsent(kind, k -> new ArrayList<>())
                    .addAll(vals);
        }
    }

    private static class OptionParserBase {
        final OptionParser parser = new OptionParser();
        private final OptionSpec<String> argAddModules = parser.accepts("add-modules").withRequiredArg();
        private final OptionSpec<String> argAddExports = parser.accepts("add-exports").withRequiredArg();
        private final OptionSpecBuilder argEnablePreview = parser.accepts("enable-preview");
        private final OptionSpecBuilder argEnableNativeAccess = parser.accepts("enable-native-access");
        private final NonOptionArgumentSpec<String> argNonOptions = parser.nonOptions();

        private final Options opts = new Options();
        private List<String> nonOptions;
        private boolean failed = false;
        private final Evaluator ev;

        OptionParserBase(Evaluator ev) {
            this.ev = ev;
        }

        List<String> nonOptions() {
            return nonOptions;
        }

        void msg(String key, Object... args) {
            if (ev != null && ev.messenger != null) {
                ev.messenger.msg(key, args);
            }
        }

        Options parse(String[] args) throws OptionException {
            try {
                OptionSet oset = parser.parse(args);
                nonOptions = oset.valuesOf(argNonOptions);
                return parse(oset);
            } catch (OptionException ex) {
                if (ex.options().isEmpty()) {
                    msg("err.opt.invalid", String.join(", ", args));
                } else {
                    boolean isKnown = parser.recognizedOptions().containsKey(ex.options().getFirst());
                    msg(isKnown
                                    ? "err.opt.arg"
                                    : "err.opt.unknown",
                            String.join(", ", ex.options()));
                }

                return null;
            }
        }

        Options parse(OptionSet options) {
            addOptions(OptionKind.ADD_MODULES, options.valuesOf(argAddModules));
            addOptions(OptionKind.ADD_EXPORTS, options.valuesOf(argAddExports).stream()
                    .map(mp -> mp.contains("=") ? mp : mp + "=ALL-UNNAMED")
                    .toList()
            );
            if (options.has(argEnablePreview)) {
                opts.addAll(OptionKind.ENABLE_PREVIEW, List.of(
                        OptionKind.ENABLE_PREVIEW.optionFlag));
                opts.addAll(OptionKind.SOURCE_RELEASE, List.of(
                        OptionKind.SOURCE_RELEASE.optionFlag,
                        System.getProperty("java.specification.version")));
            }
            if (options.has(argEnableNativeAccess)) {
                opts.addAll(OptionKind.ENABLE_NATIVE_ACCESS, List.of(
                        OptionKind.ENABLE_NATIVE_ACCESS.optionFlag, "ALL-UNNAMED"));
            }

            return failed ? null : opts;
        }

        void addOptions(OptionKind kind, Collection<String> vals) {
            if (!vals.isEmpty()) {
                if (kind.onlyOne && vals.size() > 1) {
                    msg("err.opt.one", kind.optionFlag);
                    failed = true;
                    return;
                }
                if (kind.passFlag) {
                    vals = vals.stream()
                            .flatMap(mp -> Stream.of(kind.optionFlag, mp))
                            .toList();
                }
                opts.addAll(kind, vals);
            }
        }
    }

    private enum OptionKind {
        ADD_MODULES("--add-modules", false),
        ADD_EXPORTS("--add-exports", false),
        ENABLE_PREVIEW("--enable-preview", true),
        SOURCE_RELEASE("-source", true, true, true, false, false),  // virtual option, generated by --enable-preview
        ENABLE_NATIVE_ACCESS("--enable-native-access", true, true, false, true, true),
        TO_COMPILER("-C", false, false, true, false, false),
        TO_REMOTE_VM("-R", false, false, false, true, false),
        ;
        final String optionFlag;
        final boolean onlyOne;
        final boolean passFlag;
        final boolean toCompiler;
        final boolean toRemoteVm;
        final boolean showOption;

        OptionKind(String optionFlag, boolean onlyOne) {
            this(optionFlag, onlyOne, true, true, true, true);
        }

        OptionKind(String optionFlag, boolean onlyOne, boolean passFlag, boolean toCompiler, boolean toRemoteVm, boolean showOption) {
            this.optionFlag = optionFlag;
            this.onlyOne = onlyOne;
            this.passFlag = passFlag;
            this.toCompiler = toCompiler;
            this.toRemoteVm = toRemoteVm;
            this.showOption = showOption;
        }
    }

    public static boolean cmdReset(Evaluator ev, String args) {
        if (ev.hasIllegalArgs(args, new OptionParserBase(ev))) {
            return false;
        }

        return ev.reset();
    }

    public static boolean cmdSet(Evaluator ev, String arg) {
        String cmd = "/set";
        ArgTokenizer at = new ArgTokenizer(cmd, arg.trim());
        String which = subCommand(ev.messenger, cmd, at);
        if (which == null) {
            return false;
        }
        return switch (which) {
            case "_retain" -> {
                ev.messenger.msg("err.setting.to.retain.must.be.specified", at.whole());
                yield false;
            }
            case "_blank" -> {
                ev.setFeedback(at); // no args so shows feedback setting
                ev.messenger.msg("msg.set.show.mode.settings");
                yield true;
            }
            case "format" ->
                ev.feedback.setFormat(ev.messenger, at);
            case "truncation" ->
                ev.feedback.setTruncation(ev.messenger, at);
            case "feedback" ->
                ev.setFeedback(at);
            case "mode" ->
                ev.feedback.setMode(ev.messenger, at,
                        retained -> ev.prefs.put(MODE_KEY, retained));
            default -> {
                ev.messenger.msg("err.arg", cmd, at.val());
                yield false;
            }
        };
    }

    private boolean setFeedback(ArgTokenizer at) {
        return feedback.setFeedback(messenger, at,
                fb -> prefs.put(FEEDBACK_KEY, fb));
    }
}

