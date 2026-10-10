package org.objectspace.rfid;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

public class TestRunner {
    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        Class<?>[] testClasses = new Class<?>[] {
            FinnishDataModelTest.class,
            org.objectspace.rfid.webservice.WebserviceTest.class,
            org.objectspace.rfid.feig.saveconfig.SaveConfigTest.class
        };

        System.out.println("=== Starting RFID-Core Test Runner ===");
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
                        if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(java.io.File.class)) {
                            java.io.File tempDir = java.nio.file.Files.createTempDirectory("test-rfid-core").toFile();
                            method.invoke(instance, tempDir);
                        } else {
                            method.invoke(instance);
                        }
                        System.out.println("  [PASS] " + method.getName());
                        passed++;
                    } catch (Throwable t) {
                        System.err.println("  [FAIL] " + method.getName() + " -> " + t.getCause());
                        if (t.getCause() != null) {
                            t.getCause().printStackTrace(System.err);
                        }
                        failed++;
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
