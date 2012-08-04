
Provides Java bindings for various native APIs.

* Get and set UNIX file mode.
* Get PID of current process.
* Determine if stdout/stderr are attached to a terminal.
* Query the terminal size.
* Switch between bold and normal mode on the terminal.
* Change foreground color on the terminal.

Currently only ported to OS X (10.7.4) and Linux (Ubuntu 12.04).

#### TODO

* Split out separate native library for terminal handling.
* String names for errno values.
* Split into multiple projects.
* Handle multiple architectures.
* IBM JVM.
