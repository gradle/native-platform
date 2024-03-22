package net.rubygrapefruit.platform.rust

import net.rubygrapefruit.platform.file.FileEventsRust
import spock.lang.Specification

class RunSomeRust extends Specification {
    def "do some stuff"() {
        FileEventsRust.init(null)
        expect:
        RustJniCall.hello("Some String") == "Hello, Some String"
    }

}
