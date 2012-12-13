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

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        OptionParser optionParser = new OptionParser();
        optionParser.accepts("cache-dir", "The directory to cache native libraries in").withRequiredArg();

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

        System.out.println();
        System.out.println("* OS: " + System.getProperty("os.name") + ' ' + System.getProperty("os.version") + ' ' + System.getProperty("os.arch"));
        System.out.println("* JVM: " + System.getProperty("java.vm.vendor") + ' ' + System.getProperty("java.version"));
        System.out.println("* Encoding: " + System.getProperty("file.encoding"));

        SystemInfo systemInfo = Native.get(SystemInfo.class);
        System.out.println("* Kernel: " + systemInfo.getKernelName() + ' ' + systemInfo.getKernelVersion() + ' ' + systemInfo.getMachineArchitecture());

        Process process = Native.get(Process.class);
        System.out.println("* PID: " + process.getProcessId());

        FileSystems fileSystems = Native.get(FileSystems.class);
        System.out.println("* File systems: ");
        for (FileSystem fileSystem : fileSystems.getFileSystems()) {
            System.out.println("    * " + fileSystem.getMountPoint() + ' ' + fileSystem.getFileSystemType() + ' ' + fileSystem.getDeviceName() + (fileSystem.isRemote() ? " remote" : " local"));
        }

        Terminals terminals = Native.get(Terminals.class);
        boolean stdoutIsTerminal = terminals.isTerminal(Terminals.Output.Stdout);
        boolean stderrIsTerminal = terminals.isTerminal(Terminals.Output.Stderr);
        System.out.println("* Stdout: " + (stdoutIsTerminal ? "terminal" : "not a terminal"));
        System.out.println("* Stderr: " + (stderrIsTerminal ? "terminal" : "not a terminal"));
        if (stdoutIsTerminal) {
            Terminal terminal = terminals.getTerminal(Terminals.Output.Stdout);
            TerminalSize terminalSize = terminal.getTerminalSize();
            System.out.println("* Terminal implementation: " + terminal);
            System.out.println("* Terminal size: " + terminalSize.getCols() + " cols x " + terminalSize.getRows() + " rows");
            System.out.println("* Text attributes: " + (terminal.supportsTextAttributes() ? "yes" : "no"));
            System.out.println("* Color: " + (terminal.supportsColor() ? "yes" : "no"));
            System.out.println("* Cursor motion: " + (terminal.supportsCursorMotion() ? "yes" : "no"));
            System.out.println();
            System.out.println("TEXT ATTRIBUTES");
            System.out.print("[normal] ");
            terminal.bold();
            System.out.print("[bold]");
            terminal.normal();
            System.out.println(" [normal]");
            System.out.println();

            System.out.println("COLORS");
            for (Terminal.Color color : Terminal.Color.values()) {
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
                terminal.cursorDown(1);
                System.out.print("[3]");
                terminal.cursorDown(1);
                terminal.cursorStartOfLine();
                terminal.foreground(Terminal.Color.Blue).bold();
                System.out.print("done");
                terminal.clearToEndOfLine();
                System.out.println("!");
                System.out.println();
            }
        } else if (stderrIsTerminal) {
            Terminal terminal = terminals.getTerminal(Terminals.Output.Stderr);
            System.err.print("* this is ");
            terminal.bold().foreground(Terminal.Color.Red);
            System.err.print("red");
            terminal.reset();
            System.err.print(" text on ");
            terminal.bold();
            System.err.print("stderr");
            terminal.reset();
            System.err.println(".");
        }
    }
}
