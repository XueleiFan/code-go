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

package co.ivi.code.eval;

import co.ivi.code.Evaluator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class Command {
    public final String command;
    public final String helpKey;
    private final BiFunction<Evaluator, String, Boolean> run;
    public final CommandKind kind;

    // NORMAL Commands
    public Command(String command, BiFunction<Evaluator, String, Boolean> run) {
        this(command, run, CommandKind.NORMAL);
    }

    // Special kinds of Commands
    public Command(String command, BiFunction<Evaluator, String, Boolean> run, CommandKind kind) {
        this(command, "help." + command.substring(1),
                run, kind);
    }

    // Documentation pseudo-commands
    public Command(String command, String helpKey, CommandKind kind) {
        this(command, helpKey, (evaluator, argument) -> {
                throw new IllegalStateException();
             },
             kind);
    }

    public Command(String command, String helpKey,
            BiFunction<Evaluator, String, Boolean> run, CommandKind kind) {
        this.command = command;
        this.helpKey = helpKey;
        this.run = run;
        this.kind = kind;
    }

    public boolean apply(Evaluator ev, String args) {
        return run.apply(ev, args);
    }

    public enum CommandKind {
        NORMAL(true, true),
        REPLAY(true, true),
        HELP_SUBJECT(false, false);

        public final boolean isRealCommand;
        public final boolean showInHelp;

        CommandKind(boolean isRealCommand, boolean showInHelp) {
            this.isRealCommand = isRealCommand;
            this.showInHelp = showInHelp;
        }
    }

    public static final class Commands {
        private static final Map<String, Command> commands = new LinkedHashMap<>();

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
        static {
            register(new Command("/list",
                    Evaluator::cmdList));
            register(new Command("/drop",
                    Evaluator::cmdDrop, CommandKind.REPLAY));
            register(new Command("/vars",
                    Evaluator::cmdVars));
            register(new Command("/methods",
                    Evaluator::cmdMethods));
            register(new Command("/types",
                    Evaluator::cmdTypes));
            register(new Command("/imports",
                    Evaluator::cmdImports));
            register(new Command("/exit",
                    Evaluator::cmdExit));
            register(new Command("/env",
                    Evaluator::cmdEnv));
            register(new Command("/reset",
                    Evaluator::cmdReset));
            register(new Command("/help",
                    Evaluator::cmdHelp));
            register(new Command("/set",
                    Evaluator::cmdSet));
            register(new Command("/?", "help.quest",
                    Evaluator::cmdHelp, CommandKind.NORMAL));
            register(new Command("intro", "help.intro",
                    CommandKind.HELP_SUBJECT));
            register(new Command("id", "help.id",
                    CommandKind.HELP_SUBJECT));
            register(new Command("context", "help.context",
                    CommandKind.HELP_SUBJECT));
        }

        public static Stream<Command> stream() {
            return commands.values().stream();
        }

        public static Command[] findCommand(String cmd, Predicate<Command> filter) {
            Command exact = commands.get(cmd);
            if (exact != null) {
                return new Command[]{exact};
            }

            return commands.values()
                    .stream()
                    .filter(filter)
                    .filter(command -> command.command.startsWith(cmd))
                    .toArray(Command[]::new);
        }

        private static void register(Command cm) {
            commands.put(cm.command, cm);
        }
    }
}
