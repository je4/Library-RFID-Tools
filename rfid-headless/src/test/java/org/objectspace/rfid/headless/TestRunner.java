package org.objectspace.rfid.headless;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.lang.reflect.Method;

public class TestRunner {
    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        Class<?>[] testClasses = new Class<?>[] {
            HeadlessConfigHelperTest.class,
            HeadlessReaderCallbackTest.class,
            HeadlessReaderScannerTest.class,
            HeadlessReaderTest.class,
            WsBrokerPayloadParserTest.class,
            HeadlessReaderRegistryTest.class,
            RfidWriteServiceTest.class,
            HeadlessWsBrokerDispatcherTest.class
        };

        System.out.println("=== Starting RFID-Headless Test Runner ===");
        for (Class<?> clazz : testClasses) {
            System.out.println("Running test class: " + clazz.getName());
            Object instance;
            try {
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("Could not instantiate test class: " + clazz.getName());
                failed++;
                continue;
            }

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Test.class)) {
                    try {
                        method.invoke(instance);
                        System.out.println("  [PASS] " + method.getName());
                        passed++;
                    } catch (Throwable t) {
                        System.err.println("  [FAIL] " + method.getName() + " -> " + t.getCause());
                        if (t.getCause() != null) {
                            t.getCause().printStackTrace(System.err);
                        }
                        failed++;
                    }
                } else if (method.isAnnotationPresent(ParameterizedTest.class)) {
                    if (method.isAnnotationPresent(CsvSource.class)) {
                        CsvSource csv = method.getAnnotation(CsvSource.class);
                        for (String val : csv.value()) {
                            String[] parts = val.split(",");
                            String arg1 = parts[0].trim().replaceAll("^'|'$", "");
                            int arg2 = Integer.parseInt(parts[1].trim());
                            try {
                                method.invoke(instance, arg1, arg2);
                                System.out.println("  [PASS] " + method.getName() + " (" + val + ")");
                                passed++;
                            } catch (Throwable t) {
                                System.err.println("  [FAIL] " + method.getName() + " (" + val + ") -> " + t.getCause());
                                failed++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println(String.format("=== Test Summary: %d passed, %d failed ===", passed, failed));
        if (failed > 0) {
            System.exit(1);
        }
    }
}
