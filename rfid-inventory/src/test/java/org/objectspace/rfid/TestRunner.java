/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * Based on HAWK RFID Library Tools:
 * Copyright 2015 Center for Information, Media and Technology (ZIMT),
 * HAWK University of Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
 *
 * This file is part of RFID Library Tools.
 * 
 * RFID Library Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Diese Datei ist Teil von RFID Library Tools.
 *  
 * RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
 * der GNU General Public License, wie von der Free Software Foundation,
 * Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
 * veröffentlichten Version, weiterverbreiten und/oder modifizieren.
 * 
 * Dieses Programm wird in der Hoffnung, dass es nützlich sein wird, aber
 * OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
 * Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
 * Siehe die GNU General Public License für weitere Details.
 * 
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 * Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

public class TestRunner {
    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        Class<?>[] testClasses = new Class<?>[] {
            org.objectspace.rfid.library.inventory.InventoryViewTest.class,
            org.objectspace.rfid.library.inventory.InventorySplashScreenTest.class,
            org.objectspace.rfid.library.inventory.InventoryCsvExportTest.class,
            org.objectspace.rfid.library.inventory.InventoryItemEntryTest.class,
            org.objectspace.rfid.library.inventory.InventoryCallbackWebserviceTest.class
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
                        if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(java.io.File.class)) {
                            java.io.File tempDir = java.nio.file.Files.createTempDirectory("test-rfid").toFile();
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
