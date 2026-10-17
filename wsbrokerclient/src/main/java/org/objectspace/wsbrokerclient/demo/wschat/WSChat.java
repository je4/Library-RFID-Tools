package org.objectspace.wsbrokerclient.demo.wschat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Main launcher for the native GUI WSChat demo application.
 * Replaces the web interface with a modern FlatLaf desktop GUI.
 */
public class WSChat {

    private static final Logger log = LoggerFactory.getLogger(WSChat.class);

    public static void main(String[] args) {
        String configPath = "wschat.xml";
        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            configPath = args[0].trim();
        }

        try {
            log.info("Starting WSChat Native GUI application with config '{}'...", configPath);

            // 1. Load XML configuration
            WSChatConfig config = WSChatConfig.load(configPath);

            // 2. Setup FlatLaf look and feel
            WSChatModernFrame.setupTheme(config);

            // 3. Initialize MessageStore & WSChatClient
            MessageStore store = new MessageStore(config.getName(), config.getChannels(), 500);
            WSChatClient chatClient = new WSChatClient(config, store);

            // 4. Register JVM shutdown hook for clean disconnection
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down WSChat application...");
                try {
                    chatClient.close();
                } catch (Exception e) {
                    log.warn("Error during shutdown cleanup: {}", e.getMessage());
                }
            }, "wschat-shutdown-hook"));

            // 5. Construct and show GUI on Swing Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                try {
                    WSChatModernFrame frame = new WSChatModernFrame(config, store, chatClient);
                    frame.setVisible(true);
                    log.info("WSChat GUI window successfully displayed.");
                } catch (Exception e) {
                    log.error("Failed to initialize WSChat GUI window: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(null, "Error starting WSChat GUI: " + e.getMessage(),
                            "Initialization Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            // 6. Connect to WSBroker in background
            chatClient.start();

        } catch (Exception e) {
            log.error("Fatal startup error in WSChat: {}", e.getMessage(), e);
            System.err.println("Fatal startup error in WSChat: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
