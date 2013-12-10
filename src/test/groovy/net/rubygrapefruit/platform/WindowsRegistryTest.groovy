package net.rubygrapefruit.platform

import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({!Platform.current().windows})
class WindowsRegistryTest extends Specification {
    def windowsRegistry = Native.get(WindowsRegistry)

    def "can read string value"() {
        expect:
        def currentVersion = windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows NT\CurrentVersion/, "CurrentVersion")
        currentVersion.matches("\\d+\\.\\d+")
        def path = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_CURRENT_USER, "Volatile Environment", "APPDATA"))
        path.directory
    }

    def "cannot read value that does not exist"() {
        when:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows NT\CurrentVersion/, "Unknown")

        then:
        def e = thrown(MissingRegistryEntryException)
        e.message == /Could not get value 'Unknown' of registry key 'HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion' as it does not exist./
    }

    def "cannot read value of key that does not exist"() {
        when:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Unknown/, "Value")

        then:
        def e = thrown(MissingRegistryEntryException)
        e.message == /Could not get value 'Value' of registry key 'HKEY_LOCAL_MACHINE\SOFTWARE\Unknown' as it does not exist./
    }

    def "can read subkeys"() {
        expect:
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft/).flatten().contains("Windows NT")
    }

    def "cannot read subkeys of key that does not exist"() {
        when:
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Unknown/)

        then:
        def e = thrown(MissingRegistryEntryException)
        e.message == /Could not list the subkeys of registry key 'HKEY_LOCAL_MACHINE\SOFTWARE\Unknown' as it does not exist./
    }
}
