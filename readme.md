
# Native-platform: Java bindings for various native APIs

A collection of cross-platform Java APIs for various native APIs. Supports OS X, Linux, Solaris and Windows.

These APIs support Java 5 and later. Some of these APIs overlap with APIs available in later Java versions.

## Available bindings

### Generic

* Get and set UNIX file mode.
* Get PID of current process.
* Get kernel name and version.
* Get machine architecture.

### Terminal and console

These bindings work for both the UNIX terminal and Windows console:

* Determine if stdout/stderr are attached to a terminal.
* Query the terminal size.
* Switch between bold and normal mode on the terminal.
* Change foreground color on the terminal.
* Move terminal cursor up, down, left, right, start of line.
* Clear to end of line.

### File systems

* List the available file systems on the machine
* Query file system mount point.
* Query file system type.
* Query file system device name.
* Query whether a file system is local or remote.

## Supported platforms

Currently ported to OS X, Linux, Solaris and Windows. Tested on:

* OS X 10.7.4, 10.8 (i386 and x86_64)
* Ubunutu 12.04 (amd64)
* Solaris 11 (x86)
* Windows 7 (amd64)

## Using

Include `native-platform.jar` and `native-platform-jni.jar` in your classpath.

    import net.rubygrapefruit.platform.Native;
    import net.rubygrapefruit.platform.TerminalAccess;
    import static net.rubygrapefruit.platform.TerminalAccess.Output.*;

    TerminalAccess terminalAccess = Native.get(TerminalAccess.class);

    // check if terminal
    terminalAccess.isTerminal(Stdout);

    // use terminal
    terminalAccess.getTerminal(Stdout).bold();
    System.out.println("bold text");


## Building

You will need a very recent snapshot of [Gradle](http://www.gradle.org/).

### Ubuntu

The g++ compiler is required to build the native library. You will need to `g++` package for this. Generally this is already installed.

You need to install the `libncurses5-dev` package to pick up the ncurses header files. Also worth installing the `ncurses-doc` package too.

### Windows

You need to install Visual studio, and build from a Visual studio command prompt.

### OS X

The g++ compiler is required to build the native library. You will need to install the XCode tools for this.

### Solaris

For Solaris 11, you need to install the `development/gcc-45` and `system/header` packages.

## Running

Run `gradle install` to install into `build/install/native-platform`. Or `gradle distZip` to create an application distribtion
in `build/distributions/native-platform.zip`.

You can run `$INSTALL_DIR/bin/native-platform` to run the test application.

## TODO

### Fixes

* Build 32 bit and 64 bit libraries.
* Windows: flush System.out or System.err on attribute change.
* Solaris: fix unicode file name handling.

### Improvements

* Handle multiple platforms in self-extracting jar.
* Support for cygwin terminal
* Use TERM=xtermc instead of TERM=xterm on Solaris.
* Add diagnostics for terminal.
* Split out separate native library for terminal handling.
* Version each native interface separately.
* String names for errno values.
* Split into multiple projects.
* Test on IBM JVM.
* Convert to c.
* Thread safety.
* Improve error message when unsupported capability is used.
* Initial release.
* Use fully decomposed form for unicode file names on hfs+ filesystems.

