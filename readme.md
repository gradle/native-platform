
# Native-platform: Java bindings for various native APIs

A collection of cross-platform Java APIs for various native APIs. Currently supports OS X, Linux and Windows on Intel
architectures.

These APIs support Java 5 and later. Some of these APIs overlap with APIs available in later Java versions.

## Available bindings

### System information

* Get kernel name and version.
* Get machine architecture.

### Processes

* Get the PID of the current process.
* Get and set the process working directory.
* Get and set the process environment variables.

### Terminal and console

These bindings work for both the UNIX terminal and the Windows console:

* Determine if stdout/stderr are attached to a terminal.
* Query the terminal size.
* Switch between bold and normal mode on the terminal.
* Change foreground color on the terminal.
* Move terminal cursor up, down, left, right, start of line.
* Clear to end of line.

### File systems

* Get and set UNIX file mode.
* Create and read symbolic links.
* Determine file type.
* List the available file systems on the machine.
* Query file system mount point.
* Query file system type.
* Query file system device name.
* Query whether a file system is local or remote.

### Windows

* Query registry value.
* Query the subkeys and values of a registry key.

## Supported platforms

Currently ported to OS X, Linux and Windows. Support for Solaris and FreeBSD is a work in progress. Tested on:

* OS X 10.9.1 (x86_64), 10.6.7 (i386)
* Ubunutu 13.10 (amd64), 12.10 (amd64), 8.04.4 (i386, amd64)
* Windows 8.1 (x64), 7 (x64), XP (x86, x64)

## Using

Include `native-platform.jar` and `native-platform-${os}-${arch}.jar` in your classpath. From Gradle, you can do
this:

    repositories {
        maven { url "http://repo.gradle.org/gradle/libs-releases-local" }
    }

    dependencies {
        compile "net.rubygrapefruit:native-platform:0.5"
    }

You can also download [here](http://repo.gradle.org/gradle/libs-releases-local/net/rubygrapefruit/)

Some sample code to use the terminal:

    import net.rubygrapefruit.platform.Native;
    import net.rubygrapefruit.platform.Terminals;
    import net.rubygrapefruit.platform.Terminal;
    import static net.rubygrapefruit.platform.Terminals.Output.*;

    Terminals terminals = Native.get(Terminals.class);

    // check if terminal
    terminals.isTerminal(Stdout);

    // use terminal
    Terminal stdout = terminals.getTerminal(Stdout);
    stdout.bold();
    System.out.println("bold text");

## Changes

### 0.6

* Some fixes for Windows 7 and OS X 10.6

### 0.5

* Query the available values of a Windows registry key. Thanks to [Michael Putters](https://github.com/mputters).

### 0.4

* Get file type.
* Query Windows registry value and subkeys.
* Fixes to work on 64-bit Windows XP.

### 0.3

* Get and set process working directory.
* Get and set process environment variables.
* Launch processes.
* Fixed character set issue on Linux and Mac OS X.
* Fixes to work with 64-bit OpenJDK 7 on Mac OS X. Thanks to [Rene Gr�schke](https://github.com/breskeby).

### 0.2

* Fixes to make native library extraction multi-process safe.
* Fixes to windows terminal detection and reset.

### 0.1

* Initial release.

# Development

## Building

You will need to use the Gradle wrapper. Just run `gradlew` in the root directory.

### Ubuntu

The g++ compiler is required to build the native library. You will need to install the `g++` package for this.

You need to install the `libncurses5-dev` package to pick up the ncurses header files. Also worth installing the `ncurses-doc` package too.

#### 64-bit machines with multi-arch support

Where multi-arch support is available (e.g. recent Ubuntu releases), you can build the i386 and amd64 versions of the library on the
same machine.

You need to install the `gcc-multilib` and `g++-multilib` packages to pick up i386 support.

You need to install the `lib32ncurses5-dev` package to pick up the ncurses i386 version.

### Windows

You need to install Visual studio 2010 or later, plus the Windows SDK to allow you to build both x86 and x64 binaries.

### OS X

The g++ compiler is required to build the native library. You will need to install the XCode command-line tools for this.

### Solaris

For Solaris 11, you need to install the `development/gcc-45` and `system/header` packages.

## Running

Run `gradle installApp` to install the test application into `test-app/build/install/native-platform-test`. Or
`gradle distZip` to create an application distribution in `test-app/build/distributions/native-platform-test-$version.zip`.

You can run `$INSTALL_DIR/bin/native-platform-test` to run the test application.

# Releasing

1. Check the version number in `build.gradle`.
2. Create a tag.
3. Build each variant:
    1. Checkout tag.
    2. `./gradlew clean :test :uploadJni -Prelease -PartifactoryUserName=<> -PartifactoryPassword=<>`
4. Build Java library and test app:
    1. Checkout tag.
    2. `./gradlew clean :test :uploadArchives testApp:uploadArchives -Prelease -PartifactoryUserName=<> -PartifactoryPassword=<>`
5. Checkout master
7. Increment version number in `build.gradle` and this readme.
8. Push tag and changes.

## Testing

* Test on IBM JVM.
* Test on Java 5, 6, 7.
* Test on Windows 7, Windows XP

## TODO

### Fixes

* All: `Process.getPid()` should return a long
* All: fail subsequent calls to `Native.get()` when `Native.initialize()` fails.
* Posix: allow terminal to be detected when ncurses cannot be loaded
* Windows: fix detection of shared drive under VMWare fusion and Windows XP
* Windows: restore std handles after launching child process
* Linux: detect remote filesystems.
* Solaris: fix unicode file name handling.
* Solaris: fail for unsupported architecture.
* Solaris: build 32 bit and 64 bit libraries.
* Freebsd: finish port.
* Freebsd: fail for unsupported architecture.
* Freebsd: build 32 bit and 64 bit libraries.

### Improvements

* All: fall back to WrapperProcessLauncher + DefaultProcessLauncher for all platforms, regardless of whether a
  native integration is available or not.
* All: change the terminal API to handle the fact that stdout/stderr/stdin can all be attached to the same or to
  different terminals.
* All: have `Terminal` extend `Appendable` and `Flushable`
* All: add a method to `Terminal` that returns a `PrintStream` that can be used to write to the terminal, regardless of what
  `System.out` or `System.err` point to.
* Windows: use `wchar_to_java()` for system and file system info.
* All: test network file systems
* Windows: test mount points
* All: cache class, method and field lookups
* Unix: change `readLink()` implementation so that it does not need to NULL terminate the encoded content
* All: don't use `NewStringUTF()` anywhere
* Mac: change `java_to_char()` to convert java string directly to utf-8 char string.
* Mac: change `char_to_java()` to assume utf-8 encoding and convert directly to java string.
* Linux: change `char_to_java()` to use `iconv()` to convert from C char string to UTF-16 then to java string.
* Windows: support for cygwin terminal
* Solaris: use `TERM=xtermc` instead of `TERM=xterm`.
* All: add diagnostics for terminal.
* All: version each native interface separately.
* All: string names for errno values.
* All: split into multiple projects.
* Mac: use fully decomposed form for unicode file names on hfs+ filesystems.
* All: extend FileSystem to deal with removable media.
* Unix: add a Terminal implementation that uses ANSI control codes. Use this when TERM != 'dumb' and
  libncurses cannot be loaded.
* All: add a method to Terminal that indicates whether the cursor wraps to the next line when a character is written
  to the rightmost character position.
* All: check for null parameters.

### Ideas

* Publish to bintray.
* Expose meta-data about an NTFS volume:
    * Does the volume support 8.3 file names: Query [FILE_FS_PERSISTENT_VOLUME_INFORMATION](http://msdn.microsoft.com/en-us/library/windows/hardware/ff540280.aspx)
      using [DeviceIoControl()](http://msdn.microsoft.com/en-us/library/aa363216.aspx)
* Expose native desktop notification services:
    * OS X message center
    * Growl
    * Snarl
    * dnotify
* Locate various system directories (eg program files on windows).
* Expose platform-specific HTTP proxy configuration. Query registry on windows to determine IE settings.
* Expose native named semaphores, mutexes and condition variables (CreateMutex, CreateSemaphore, CreateEvent, semget, sem_open, etc).
* Expose information about network interfaces.
* Fire events when filesystems or network interfaces change in some way.
* Fire events when terminal size changes.
* Fire events when files change.
* Expose system keystores and authentication services.
* Expose a mechanism for generating a temporary directory.
