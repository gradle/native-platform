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

        def path
        try {
            path = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_CURRENT_USER, "Volatile Environment", "APPDATA"))
        } catch (MissingRegistryEntryException e) {
            // Happens when not logged in
            return
        }

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

    def "can list subkeys of a key"() {
        expect:
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft/).flatten().contains("Windows NT")
    }

    def "cannot list subkeys of key that does not exist"() {
        when:
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Unknown/)

        then:
        def e = thrown(MissingRegistryEntryException)
        e.message == /Could not list the subkeys of registry key 'HKEY_LOCAL_MACHINE\SOFTWARE\Unknown' as it does not exist./
    }

    def "cannot list values of a key"() {
        expect:
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows NT\CurrentVersion/).flatten().contains("CurrentVersion")
    }

    def "cannot list values of key that does not exist"() {
        when:
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Unknown/)

        then:
        def e = thrown(MissingRegistryEntryException)
        e.message == /Could not list the values of registry key 'HKEY_LOCAL_MACHINE\SOFTWARE\Unknown' as it does not exist./
    }
}
