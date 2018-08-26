/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.rubygrapefruit.platform.test;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.Process;
import net.rubygrapefruit.platform.file.*;
import net.rubygrapefruit.platform.memory.Memory;
import net.rubygrapefruit.platform.memory.MemoryInfo;
import net.rubygrapefruit.platform.prompts.Prompter;
import net.rubygrapefruit.platform.terminal.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        OptionParser optionParser = new OptionParser();
        optionParser.accepts("cache-dir", "The directory to cache native libraries in").withRequiredArg();
        optionParser.accepts("ansi", "Force the use of ANSI escape sequences for terminal output");
        optionParser.accepts("stat", "Display details about the specified file or directory").withRequiredArg();
        optionParser.accepts("ls", "Display contents of the specified directory").withRequiredArg();
        optionParser.accepts("watch", "Watches for changes to the specified file or directory").withRequiredArg();
        optionParser.accepts("machine", "Display details about the current machine");
        optionParser.accepts("terminal", "Display details about the terminal");
        optionParser.accepts("input", "Reads input from the terminal");
        optionParser.accepts("prompts", "Display sample prompts");

        OptionSet result = null;
        try {
            result = optionParser.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage());
            System.err.println();
            optionParser.printHelpOn(System.err);
            System.exit(1);
        }

        if (result.has("cache-dir")) {
            Native.init(new File(result.valueOf("cache-dir").toString()));
        }

        boolean ansi = result.has("ansi");

        if (result.has("stat")) {
            stat((String) result.valueOf("stat"));
            return;
        }

        if (result.has("ls")) {
            ls((String) result.valueOf("ls"));
            return;
        }

        if (result.has("watch")) {
            watch((String) result.valueOf("watch"));
            return;
        }

        if (result.has("machine")) {
            machine();
            return;
        }

        if (result.has("input")) {
            input();
            return;
        }

        Prompter prompter = new Prompter(terminals(ansi));

        if (result.has("prompts")) {
            prompts(prompter);
            return;
        }

        if (!prompter.isInteractive()) {
            terminal(ansi);
            return;
        }

        while (true) {
            Integer selected = prompter.select("Select test to run", Arrays.asList("Show terminal details", "Show machine details", "Test input handling", "Example prompts", "Exit"), 4);
            if (selected == null) {
                System.out.println();
                return;
            }

            switch (selected) {
                case 0:
                    terminal(ansi);
                    break;
                case 1:
                    machine();
                    break;
                case 2:
                    input();
                    break;
                case 3:
                    prompts(prompter);
                    break;
                default:
                    System.out.println();
                    return;
            }
        }
    }

    private static void prompts(Prompter prompter) {
        Integer selected = prompter.select("Select an option", Arrays.asList("Option 1", "Option 2", "Option 3"), 2);
        System.out.println("You selected item: " + selected);

        String text = prompter.enterText("Enter some text", "default");
        System.out.println("You entered: " + text);

        String password = prompter.enterPassword("Enter a password");
        System.out.println("You entered: " + password);

        Boolean answer = prompter.askYesNo("A yes/no question", true);
        System.out.println("You answered: " + answer);

        System.out.println();
    }

    private static void terminal(boolean ansi) {
        System.out.println();
        Terminals terminals = terminals(ansi);
        boolean stdoutIsTerminal = terminals.isTerminal(Terminals.Output.Stdout);
        boolean stderrIsTerminal = terminals.isTerminal(Terminals.Output.Stderr);
        boolean stdinIsTerminal = terminals.isTerminalInput();
        System.out.println("* Stdout: " + (stdoutIsTerminal ? "terminal" : "not a terminal"));
        System.out.println("* Stderr: " + (stderrIsTerminal ? "terminal" : "not a terminal"));
        System.out.println("* Stdin: " + (stdinIsTerminal ? "terminal" : "not a terminal"));
        if (stdoutIsTerminal) {
            TerminalOutput terminal = terminals.getTerminal(Terminals.Output.Stdout);
            System.setOut(new PrintStream(terminal.getOutputStream(), true));
            TerminalSize terminalSize = terminal.getTerminalSize();
            System.out.println("* Terminal implementation: " + terminal);
            System.out.println("* Terminal size: " + terminalSize.getCols() + " cols x " + terminalSize.getRows() + " rows");
            System.out.println("* Text attributes: " + (terminal.supportsTextAttributes() ? "yes" : "no"));
            System.out.println("* Color: " + (terminal.supportsColor() ? "yes" : "no"));
            System.out.println("* Cursor motion: " + (terminal.supportsCursorMotion() ? "yes" : "no"));
            System.out.println("* Cursor visibility: " + (terminal.supportsCursorVisibility() ? "yes" : "no"));
            System.out.println();
            System.out.println("TEXT ATTRIBUTES");
            System.out.print("[normal] ");
            terminal.bold();
            System.out.print("[bold]");
            terminal.normal();
            System.out.println(" [normal]");
            System.out.println();

            System.out.println("COLORS");
            for (TerminalOutput.Color color : TerminalOutput.Color.values()) {
                terminal.foreground(color);
                terminal.bold();
                System.out.print(String.format("[%s] ", color.toString().toLowerCase()));
                terminal.normal();
                System.out.print(String.format("[%s]", color.toString().toLowerCase()));
                System.out.println();
            }
            System.out.println();

            terminal.reset();

            if (terminal.supportsCursorMotion()) {
                System.out.println("CURSOR MOVEMENT");
                System.out.println("                    ");
                System.out.println("                    ");
                System.out.print("[delete me]");

                terminal.cursorLeft(11);
                terminal.cursorUp(1);
                terminal.cursorRight(10);
                System.out.print("[4]");
                terminal.cursorUp(1);
                terminal.cursorLeft(3);
                System.out.print("[2]");
                terminal.cursorLeft(13);
                System.out.print("[1]");
                terminal.cursorLeft(3);
                terminal.cursorUp(2);
                terminal.cursorDown(3);
                System.out.print("[3]");
                terminal.cursorDown(1);
                terminal.cursorStartOfLine();
                terminal.foreground(TerminalOutput.Color.Blue).bold();
                System.out.print("done");
                terminal.clearToEndOfLine();
                System.out.println("!");
                terminal.reset();
                System.out.println();
            }

            System.out.print("Can write ");
            terminal.bold().foreground(TerminalOutput.Color.Blue).write("\u03B1\u03B2\u03B3");
            terminal.reset();
            System.out.print(" unicode ");
            terminal.foreground(TerminalOutput.Color.Green);
            System.out.println("\u2714");
            terminal.reset();
            System.out.println();
        } else if (stderrIsTerminal) {
            TerminalOutput terminal = terminals.getTerminal(Terminals.Output.Stderr);
            System.setErr(new PrintStream(terminal.getOutputStream(), true));
            System.err.print("* this is ");
            terminal.bold().foreground(TerminalOutput.Color.Red);
            System.err.print("red");
            terminal.reset();
            System.err.print(" text on ");
            terminal.bold();
            System.err.print("stderr");
            terminal.reset();
            System.err.println(".");
        }
    }

    private static Terminals terminals(boolean ansi) {
        Terminals terminals = Native.get(Terminals.class);
        if (ansi) {
            terminals = terminals.withAnsiOutput();
        }
        return terminals;
    }

    private static void input() {
        System.out.println();
        Terminals terminals = Native.get(Terminals.class);
        if (!terminals.isTerminalInput()) {
            System.out.println("* Input not attached to terminal.");
            return;
        }
        TerminalInput terminalInput = terminals.getTerminalInput();
        System.out.println("* Using default mode");
        System.out.print("Enter some text: ");
        LoggingTerminalInputListener listener = new LoggingTerminalInputListener();
        while (!listener.finished) {
            terminalInput.read(listener);
        }
        System.out.println();
        System.out.println("* Using raw mode");
        terminalInput.rawMode();
        System.out.println("Enter some text (press 'enter' to finish): ");
        listener = new LoggingTerminalInputListener();
        while (!listener.finished) {
            terminalInput.read(listener);
        }
        terminalInput.reset();
        System.out.println();
    }

    private static void machine() {
        System.out.println();
        System.out.println("* JVM: " + System.getProperty("java.vm.vendor") + ' ' + System.getProperty("java.version"));
        System.out.println("* OS (JVM): " + System.getProperty("os.name") + ' ' + System.getProperty("os.version") + ' ' + System.getProperty("os.arch"));

        SystemInfo systemInfo = Native.get(SystemInfo.class);
        System.out.println("* OS (Kernel): " + systemInfo.getKernelName() + ' ' + systemInfo.getKernelVersion() + ' ' + systemInfo.getArchitectureName() + " (" + systemInfo.getArchitecture() + ")");

        Process process = Native.get(Process.class);
        System.out.println("* PID: " + process.getProcessId());

        FileSystems fileSystems = Native.get(FileSystems.class);
        System.out.println("* File systems: ");
        for (FileSystemInfo fileSystem : fileSystems.getFileSystems()) {
            System.out.println(String.format("    * %s -> %s (type: %s %s, case sensitive: %s, case preserving: %s)",
                    fileSystem.getMountPoint(), fileSystem.getDeviceName(), fileSystem.getFileSystemType(),
                    fileSystem.isRemote() ? "remote" : "local", fileSystem.isCaseSensitive(), fileSystem.isCasePreserving()));
        }

        try {
            MemoryInfo memory = Native.get(Memory.class).getMemoryInfo();
            System.out.println("* Available memory: " + memory.getAvailablePhysicalMemory());
            System.out.println("* Total memory: " + memory.getTotalPhysicalMemory());
        } catch (NativeIntegrationUnavailableException e) {
            // ignore
        }

        System.out.println();
    }

    private static void watch(String path) {
        File file = new File(path);
        final FileWatch watch = Native.get(FileEvents.class).startWatch(file);
        try {
            System.out.println("Waiting - type ctrl-d to exit ...");
            Thread watcher = new Thread() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            watch.nextChange();
                            System.out.println("Change detected.");
                        }
                    } catch (ResourceClosedException e) {
                        // Expected
                    }
                }
            };
            watcher.start();
            while (true) {
                int ch = System.in.read();
                if (ch < 0) {
                    break;
                }
            }
            watch.close();
            watcher.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            watch.close();
        }
        System.out.println("Done");
    }

    private static void ls(String path) {
        File dir = new File(path);

        Files files = Native.get(Files.class);
        List<? extends DirEntry> entries = files.listDir(dir);
        for (DirEntry entry : entries) {
            System.out.println();
            System.out.println("* Name: " + entry.getName());
            System.out.println("* Type: " + entry.getType());
            stat(new File(dir, entry.getName()), entry);
        }
    }

    private static void stat(String path) {
        File file = new File(path);

        Files files = Native.get(Files.class);
        FileInfo stat = files.stat(file);
        System.out.println();
        System.out.println("* File: " + file);
        System.out.println("* Type: " + stat.getType());
        if (stat.getType() != FileInfo.Type.Missing) {
            if (stat instanceof PosixFileInfo) {
                stat(file, (PosixFileInfo) stat);
            } else {
                stat(file, stat);
            }
        }

        System.out.println();
    }

    private static void stat(File file, FileInfo stat) {
        System.out.println("* Size: " + stat.getSize());
        System.out.println("* Modification time: " + date(stat.getLastModifiedTime()));
    }

    private static void stat(File file, PosixFileInfo stat) {
        if (stat.getType() == PosixFileInfo.Type.Symlink) {
            System.out.println("* Symlink to: " + Native.get(PosixFiles.class).readLink(file));
        }
        System.out.println("* UID: " + stat.getUid());
        System.out.println("* GID: " + stat.getGid());
        System.out.println(String.format("* Mode: %03o", stat.getMode()));
        System.out.println("* Size: " + stat.getSize());
        System.out.println("* Modification time: " + date(stat.getLastModifiedTime()));
        System.out.println("* Block size: " + stat.getBlockSize());
    }

    private static String date(long timestamp) {
        return new SimpleDateFormat("yyyyMMdd HHmmss.SSS").format(new Date(timestamp));
    }

    private static class LoggingTerminalInputListener implements TerminalInputListener {
        boolean finished;

        @Override
        public void character(char ch) {
            System.out.println("Character: " + ch + " (" + (int) ch + ")");
        }

        @Override
        public void controlKey(Key key) {
            System.out.println("Control key: " + key);
            if (key == Key.Enter) {
                finished = true;
            }
        }

        @Override
        public void endInput() {
            System.out.println("End input");
            finished = true;
        }
    }
}
