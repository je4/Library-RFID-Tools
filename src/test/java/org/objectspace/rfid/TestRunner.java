package org.objectspace.rfid;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

public class TestRunner {
    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        Class<?>[] testClasses = new Class<?>[] {
            FinnishDataModelTest.class
        };

        System.out.println("=== Starting Test Runner ===");
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
                }
            }
        }
        System.out.println(String.format("=== Test Summary: %d passed, %d failed ===", passed, failed));
        if (failed > 0) {
            System.exit(1);
        }
    }
}
