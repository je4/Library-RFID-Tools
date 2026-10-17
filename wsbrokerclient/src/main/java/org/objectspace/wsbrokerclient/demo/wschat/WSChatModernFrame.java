package org.objectspace.wsbrokerclient.demo.wschat;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.objectspace.certmanager.loader.CertLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern Swing + FlatLaf implementation of the WSChat native user interface.
 * Matches the design language of RFID Inventory Modern Frame with KPI metrics cards,
 * responsive layout, live search filtering, real-time event logs, and mTLS security status.
 */
public class WSChatModernFrame extends JFrame implements MessageStore.StoreListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(WSChatModernFrame.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final WSChatConfig config;
    private final MessageStore store;
    private final WSChatClient chatClient;

    // UI Header & Status Badges
    private JLabel lblHeaderTitle;
    private JLabel lblHeaderSubtitle;
    private JLabel lblStatusBadge;
    private JLabel lblTlsBadge;
    private JLabel lblNtpBadge;

    // KPI Metrics Cards
    private JLabel lblKpiStatusVal;
    private JLabel lblKpiActiveChannelVal;
    private JLabel lblKpiTotalMessagesVal;
    private JLabel lblKpiChannelsCountVal;

    // Channel List
    private DefaultListModel<String> channelListModel;
    private JList<String> channelList;
    private JTextField txtNewChannel;
    private JButton btnJoinChannel;

    // Chat Message Area
    private JLabel lblActiveChannelHeader;
    private JTextField txtSearch;
    private DefaultListModel<ChatMessage> messageListModel;
    private JList<ChatMessage> messageList;
    private JTextField txtMessageInput;
    private JButton btnSend;

    // Right Details / Log Pane
    private JTabbedPane rightTabbedPane;
    private JTextArea txtSecurityInfo;
    private JTextArea txtLiveLog;

    // Status Bar
    private JLabel lblStatusBar;
    private JLabel lblStatusBarTime;

    private String searchFilter = "";

    public WSChatModernFrame(WSChatConfig config, MessageStore store, WSChatClient chatClient) {
        super("WSChat - WSBroker Native Client - info-age GmbH, Basel");
        this.config = config != null ? config : new WSChatConfig();
        this.store = store != null ? store : new MessageStore(this.config.getName(), this.config.getChannels(), 500);
        this.chatClient = chatClient;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });

        this.store.addListener(this);

        initWindowProperties();
        initComponents();
        setupLogListener();
        updateAllData();
    }

    /**
     * Initializes FlatLaf Look and Feel based on config theme ("system", "dark", "light").
     */
    public static void setupTheme(WSChatConfig config) {
        String theme = config != null ? config.getTheme().trim().toLowerCase() : "system";
        try {
            if ("dark".equals(theme)) {
                FlatDarkLaf.setup();
            } else if ("light".equals(theme)) {
                FlatLightLaf.setup();
            } else {
                if (isSystemDarkMode()) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
            }
        } catch (Exception e) {
            log.error("Could not initialize FlatLaf theme: {}", e.getMessage());
        }
    }

    public static boolean isSystemDarkMode() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return isWindowsDarkMode();
        } else if (os.contains("mac")) {
            return isMacDarkMode();
        }
        return false;
    }

    private static boolean isWindowsDarkMode() {
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true)
                    .start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        line = line.trim();
                        if (line.endsWith("0x0") || line.endsWith("0")) {
                            return true; // Dark Mode
                        } else if (line.endsWith("0x1") || line.endsWith("1")) {
                            return false; // Light Mode
                        }
                    }
                }
            }
            process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isMacDarkMode() {
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.trim().equalsIgnoreCase("Dark")) {
                    return true;
                }
            }
            process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {}
        return false;
    }

    private void initWindowProperties() {
        setLocation(config.getPosX(), config.getPosY());
        setSize(config.getWidth(), config.getHeight());
        setMinimumSize(new Dimension(850, 550));
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(UIManager.getColor("Panel.background"));

        // 1. Header Bar
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Center Container (KPI Banner + Content Split)
        JPanel centerContainer = new JPanel(new BorderLayout(0, 0));
        centerContainer.add(createKpiBanner(), BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        contentPanel.add(createMainSplitPane(), BorderLayout.CENTER);

        centerContainer.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(centerContainer, BorderLayout.CENTER);

        // 3. Status Bar
        mainPanel.add(createStatusBarPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    /**
     * Modern Header Bar with Title, Subtitle, Status Pills, and Quick Actions.
     */
    private JPanel createHeaderPanel() {
        boolean isDark = isDarkLaf();
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(isDark ? new Color(15, 23, 42) : new Color(241, 245, 249));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, isDark ? new Color(51, 65, 85) : new Color(226, 232, 240)),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // Left: Title & Subtitle
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        lblHeaderTitle = new JLabel("WSChat - WSBroker Native Client");
        lblHeaderTitle.setFont(lblHeaderTitle.getFont().deriveFont(Font.BOLD, 17f));
        lblHeaderTitle.setForeground(isDark ? new Color(248, 250, 252) : new Color(15, 23, 42));

        lblHeaderSubtitle = new JLabel(String.format("Client: %s | Broker: %s", config.getName(), config.getWsbroker()));
        lblHeaderSubtitle.setFont(lblHeaderSubtitle.getFont().deriveFont(Font.PLAIN, 12f));
        lblHeaderSubtitle.setForeground(isDark ? new Color(148, 163, 184) : new Color(100, 116, 139));

        leftPanel.add(lblHeaderTitle);
        leftPanel.add(Box.createVerticalStrut(2));
        leftPanel.add(lblHeaderSubtitle);

        header.add(leftPanel, BorderLayout.WEST);

        // Right: Status Badges and Quick Actions
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        lblStatusBadge = createBadgeLabel("Disconnected", new Color(239, 68, 68), Color.WHITE);
        String tlsType = config.getClientTls() != null && config.getClientTls().getType() != null
                ? config.getClientTls().getType().name() : "DEV";
        lblTlsBadge = createBadgeLabel("mTLS: " + tlsType, new Color(59, 130, 246), Color.WHITE);
        lblNtpBadge = createBadgeLabel("NTP: --", new Color(107, 114, 128), Color.WHITE);

        JButton btnNtpSync = new JButton("NTP Sync");
        btnNtpSync.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        btnNtpSync.setToolTipText("Query time synchronization and latency over WebSocket NTP RFC 5905");
        btnNtpSync.addActionListener(e -> performNtpSync());

        JButton btnThemeToggle = new JButton(isDark ? "Light Mode" : "Dark Mode");
        btnThemeToggle.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        btnThemeToggle.addActionListener(e -> toggleTheme(btnThemeToggle));

        rightPanel.add(lblStatusBadge);
        rightPanel.add(lblTlsBadge);
        rightPanel.add(lblNtpBadge);
        rightPanel.add(btnNtpSync);
        rightPanel.add(btnThemeToggle);

        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    /**
     * 4 KPI Metric Cards (Connection, Active Channel, Total Messages, Subscribed Channels).
     */
    private JPanel createKpiBanner() {
        JPanel banner = new JPanel(new GridLayout(1, 4, 10, 0));
        banner.setBorder(new EmptyBorder(10, 12, 4, 12));
        banner.setOpaque(false);

        lblKpiStatusVal = new JLabel("Disconnected");
        banner.add(createKpiCard("CONNECTION STATUS", lblKpiStatusVal, new Color(239, 68, 68)));

        lblKpiActiveChannelVal = new JLabel("# " + store.getActiveChannel());
        banner.add(createKpiCard("ACTIVE CHANNEL", lblKpiActiveChannelVal, new Color(14, 165, 233)));

        lblKpiTotalMessagesVal = new JLabel("0");
        banner.add(createKpiCard("TOTAL MESSAGES", lblKpiTotalMessagesVal, new Color(34, 197, 94)));

        lblKpiChannelsCountVal = new JLabel(String.valueOf(store.getChannels().size()));
        banner.add(createKpiCard("CHANNELS SUBSCRIBED", lblKpiChannelsCountVal, new Color(168, 85, 247)));

        return banner;
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        boolean isDark = isDarkLaf();
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(isDark ? new Color(30, 41, 59) : Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(isDark ? new Color(51, 65, 85) : new Color(226, 232, 240), 1),
                        new EmptyBorder(8, 12, 8, 12)
                )
        ));
        card.putClientProperty(FlatClientProperties.STYLE, "arc: 8");

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 10f));
        lblTitle.setForeground(isDark ? new Color(148, 163, 184) : new Color(100, 116, 139));

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 16f));
        valueLabel.setForeground(isDark ? new Color(241, 245, 249) : new Color(15, 23, 42));

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    /**
     * Main SplitPane: Left Channels List | Center Chat View | Right Inspector / Log.
     */
    private JSplitPane createMainSplitPane() {
        // 1. Left Channel List Sidebar
        JPanel pnlChannels = createChannelsSidebar();

        // 2. Center Chat Panel
        JPanel pnlChat = createChatPanel();

        // 3. Right Inspector & Log Panel
        JPanel pnlInspector = createInspectorPanel();

        // Left split: Channels (left) vs Chat (center)
        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pnlChannels, pnlChat);
        leftSplit.setDividerLocation(220);
        leftSplit.setContinuousLayout(true);
        leftSplit.setBorder(null);

        // Main split: [Channels + Chat] (left) vs Inspector (right)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, pnlInspector);
        mainSplit.setDividerLocation(780);
        mainSplit.setResizeWeight(0.7);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(null);

        return mainSplit;
    }

    /**
     * Channels Sidebar with JList and Dynamic Join Channel Input.
     */
    private JPanel createChannelsSidebar() {
        boolean isDark = isDarkLaf();
        JPanel pnl = new JPanel(new BorderLayout(0, 8));
        pnl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isDark ? new Color(51, 65, 85) : new Color(226, 232, 240), 1),
                new EmptyBorder(8, 8, 8, 8)
        ));
        pnl.putClientProperty(FlatClientProperties.STYLE, "arc: 8");

        JLabel lblTitle = new JLabel("CHANNELS");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 11f));
        lblTitle.setForeground(isDark ? new Color(148, 163, 184) : new Color(100, 116, 139));
        pnl.add(lblTitle, BorderLayout.NORTH);

        channelListModel = new DefaultListModel<>();
        for (String ch : store.getChannels()) {
            channelListModel.addElement(ch);
        }

        channelList = new JList<>(channelListModel);
        channelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        channelList.setSelectedValue(store.getActiveChannel(), true);
        channelList.setCellRenderer(new ChannelListCellRenderer());
        channelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = channelList.getSelectedValue();
                if (selected != null && !selected.isBlank()) {
                    store.setActiveChannel(selected);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(channelList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        pnl.add(scroll, BorderLayout.CENTER);

        // Join channel bottom panel
        JPanel joinPanel = new JPanel(new BorderLayout(4, 0));
        joinPanel.setOpaque(false);

        txtNewChannel = new JTextField();
        txtNewChannel.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Join channel...");
        txtNewChannel.addActionListener(e -> performJoinChannel());

        btnJoinChannel = new JButton("+");
        btnJoinChannel.setToolTipText("Subscribe to channel");
        btnJoinChannel.addActionListener(e -> performJoinChannel());

        joinPanel.add(txtNewChannel, BorderLayout.CENTER);
        joinPanel.add(btnJoinChannel, BorderLayout.EAST);
        pnl.add(joinPanel, BorderLayout.SOUTH);

        return pnl;
    }

    /**
     * Center Chat Panel with header, message stream, search filter, and message input.
     */
    private JPanel createChatPanel() {
        boolean isDark = isDarkLaf();
        JPanel pnl = new JPanel(new BorderLayout(0, 8));
        pnl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isDark ? new Color(51, 65, 85) : new Color(226, 232, 240), 1),
                new EmptyBorder(8, 8, 8, 8)
        ));
        pnl.putClientProperty(FlatClientProperties.STYLE, "arc: 8");

        // Top Header of chat: Active channel & Search Filter
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        lblActiveChannelHeader = new JLabel("# " + store.getActiveChannel());
        lblActiveChannelHeader.setFont(lblActiveChannelHeader.getFont().deriveFont(Font.BOLD, 15f));
        lblActiveChannelHeader.setForeground(isDark ? new Color(241, 245, 249) : new Color(15, 23, 42));

        txtSearch = new JTextField(12);
        txtSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Filter messages...");
        txtSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateFilter(); }
            private void updateFilter() {
                searchFilter = txtSearch.getText().trim().toLowerCase();
                refreshMessagesList();
            }
        });

        topBar.add(lblActiveChannelHeader, BorderLayout.WEST);
        topBar.add(txtSearch, BorderLayout.EAST);
        pnl.add(topBar, BorderLayout.NORTH);

        // Messages Viewport
        messageListModel = new DefaultListModel<>();
        messageList = new JList<>(messageListModel);
        messageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        messageList.setCellRenderer(new MessageListCellRenderer());

        JScrollPane scroll = new JScrollPane(messageList);
        scroll.setBorder(BorderFactory.createLineBorder(isDark ? new Color(51, 65, 85) : new Color(226, 232, 240), 1));
        pnl.add(scroll, BorderLayout.CENTER);

        // Bottom Message Input Panel
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setOpaque(false);

        txtMessageInput = new JTextField();
        txtMessageInput.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Type a message... (Press Enter to send)");
        txtMessageInput.addActionListener(e -> performSendMessage());

        btnSend = new JButton("Send");
        btnSend.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        btnSend.setBackground(new Color(14, 165, 233));
        btnSend.setForeground(Color.WHITE);
        btnSend.addActionListener(e -> performSendMessage());

        inputPanel.add(txtMessageInput, BorderLayout.CENTER);
        inputPanel.add(btnSend, BorderLayout.EAST);
        pnl.add(inputPanel, BorderLayout.SOUTH);

        return pnl;
    }

    /**
     * Right Inspector Panel: Tabbed Security / Cert details and Live Event Console.
     */
    private JPanel createInspectorPanel() {
        boolean isDark = isDarkLaf();
        JPanel pnl = new JPanel(new BorderLayout(0, 0));
        pnl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isDark ? new Color(51, 65, 85) : new Color(226, 232, 240), 1),
                new EmptyBorder(4, 4, 4, 4)
        ));
        pnl.putClientProperty(FlatClientProperties.STYLE, "arc: 8");

        rightTabbedPane = new JTabbedPane();

        // Tab 1: Live Event Log Console
        JPanel pnlLog = new JPanel(new BorderLayout(0, 4));
        txtLiveLog = new JTextArea();
        txtLiveLog.setEditable(false);
        txtLiveLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scrollLog = new JScrollPane(txtLiveLog);
        scrollLog.setBorder(null);

        JButton btnClearLog = new JButton("Clear Console");
        btnClearLog.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        btnClearLog.addActionListener(e -> txtLiveLog.setText(""));

        pnlLog.add(scrollLog, BorderLayout.CENTER);
        pnlLog.add(btnClearLog, BorderLayout.SOUTH);

        // Tab 2: Security & Connection Inspector
        txtSecurityInfo = new JTextArea();
        txtSecurityInfo.setEditable(false);
        txtSecurityInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtSecurityInfo.setBorder(new EmptyBorder(8, 8, 8, 8));
        updateSecurityInfoText();
        JScrollPane scrollSec = new JScrollPane(txtSecurityInfo);
        scrollSec.setBorder(null);

        rightTabbedPane.addTab("Live Log", pnlLog);
        rightTabbedPane.addTab("Security & Info", scrollSec);

        pnl.add(rightTabbedPane, BorderLayout.CENTER);
        return pnl;
    }

    /**
     * Modern Bottom Status Bar.
     */
    private JPanel createStatusBarPanel() {
        boolean isDark = isDarkLaf();
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(isDark ? new Color(15, 23, 42) : new Color(241, 245, 249));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, isDark ? new Color(51, 65, 85) : new Color(226, 232, 240)),
                new EmptyBorder(6, 16, 6, 16)
        ));

        lblStatusBar = new JLabel("Ready");
        lblStatusBar.setFont(lblStatusBar.getFont().deriveFont(Font.PLAIN, 11f));
        lblStatusBar.setForeground(isDark ? new Color(148, 163, 184) : new Color(100, 116, 139));

        lblStatusBarTime = new JLabel("Start: " + java.time.LocalTime.now().format(TIME_FORMATTER));
        lblStatusBarTime.setFont(lblStatusBarTime.getFont().deriveFont(Font.PLAIN, 11f));
        lblStatusBarTime.setForeground(isDark ? new Color(148, 163, 184) : new Color(100, 116, 139));

        bar.add(lblStatusBar, BorderLayout.WEST);
        bar.add(lblStatusBarTime, BorderLayout.EAST);
        return bar;
    }

    private JLabel createBadgeLabel(String text, Color bg, Color fg) {
        JLabel lbl = new JLabel(text);
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        lbl.setForeground(fg);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        lbl.setBorder(new EmptyBorder(3, 8, 3, 8));
        lbl.putClientProperty(FlatClientProperties.STYLE, "arc: 12");
        return lbl;
    }

    private void setupLogListener() {
        if (chatClient != null) {
            chatClient.addLogListener(msg -> SwingUtilities.invokeLater(() -> {
                if (txtLiveLog != null) {
                    txtLiveLog.append(msg + "\n");
                    txtLiveLog.setCaretPosition(txtLiveLog.getDocument().getLength());
                }
                if (lblStatusBar != null) {
                    lblStatusBar.setText(msg);
                }
            }));
        }
    }

    // --- Actions ---

    private void performSendMessage() {
        String text = txtMessageInput.getText().trim();
        if (text.isEmpty()) return;

        String activeCh = store.getActiveChannel();
        if (activeCh.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select or join a channel first.", "No Active Channel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        txtMessageInput.setText("");
        if (chatClient != null) {
            chatClient.sendMessage(activeCh, text).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Failed to send message: " + ex.getMessage(), "Send Error", JOptionPane.ERROR_MESSAGE);
                });
                return null;
            });
        }
    }

    private void performJoinChannel() {
        String channel = txtNewChannel.getText().trim();
        if (channel.isEmpty()) return;

        txtNewChannel.setText("");
        if (chatClient != null) {
            chatClient.attachChannel(channel);
        } else {
            store.addChannel(channel);
        }
        store.setActiveChannel(channel);
    }

    private void performNtpSync() {
        if (chatClient != null) {
            lblNtpBadge.setText("NTP: Syncing...");
            lblNtpBadge.setBackground(new Color(234, 179, 8));
            chatClient.queryNtp().thenAccept(resp -> SwingUtilities.invokeLater(() -> {
                lblNtpBadge.setText(String.format("NTP: %.1fms (RTT %.1fms)", resp.getOffsetMillis(), resp.getRoundTripDelayMillis()));
                lblNtpBadge.setBackground(new Color(34, 197, 94));
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    lblNtpBadge.setText("NTP: Error");
                    lblNtpBadge.setBackground(new Color(239, 68, 68));
                });
                return null;
            });
        }
    }

    private void toggleTheme(JButton btnToggle) {
        try {
            if (isDarkLaf()) {
                FlatLightLaf.setup();
                btnToggle.setText("Dark Mode");
            } else {
                FlatDarkLaf.setup();
                btnToggle.setText("Light Mode");
            }
            SwingUtilities.updateComponentTreeUI(this);
            updateSecurityInfoText();
        } catch (Exception e) {
            log.error("Failed to switch theme: {}", e.getMessage());
        }
    }

    private static boolean isDarkLaf() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
        return luminance < 0.5;
    }

    // --- MessageStore.StoreListener callbacks ---

    @Override
    public void onMessageAdded(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            if (message != null && message.getChannel() != null && message.getChannel().equalsIgnoreCase(store.getActiveChannel())) {
                if (matchesFilter(message)) {
                    messageListModel.addElement(message);
                    messageList.ensureIndexIsVisible(messageListModel.getSize() - 1);
                }
            }
            updateKpiMetrics();
        });
    }

    @Override
    public void onChannelAdded(String channel) {
        SwingUtilities.invokeLater(() -> {
            if (!channelListModel.contains(channel)) {
                channelListModel.addElement(channel);
            }
            updateKpiMetrics();
        });
    }

    @Override
    public void onActiveChannelChanged(String activeChannel) {
        SwingUtilities.invokeLater(() -> {
            lblActiveChannelHeader.setText("# " + activeChannel);
            lblKpiActiveChannelVal.setText("# " + activeChannel);
            channelList.setSelectedValue(activeChannel, true);
            refreshMessagesList();
        });
    }

    @Override
    public void onStatusChanged(String status) {
        SwingUtilities.invokeLater(() -> {
            lblKpiStatusVal.setText(status);
            lblStatusBadge.setText(status);
            if ("Connected".equalsIgnoreCase(status)) {
                lblStatusBadge.setBackground(new Color(34, 197, 94));
                lblKpiStatusVal.setForeground(new Color(34, 197, 94));
            } else if ("Reconnecting".equalsIgnoreCase(status) || "Connecting".equalsIgnoreCase(status)) {
                lblStatusBadge.setBackground(new Color(234, 179, 8));
                lblKpiStatusVal.setForeground(new Color(234, 179, 8));
            } else {
                lblStatusBadge.setBackground(new Color(239, 68, 68));
                lblKpiStatusVal.setForeground(new Color(239, 68, 68));
            }
            updateSecurityInfoText();
        });
    }

    @Override
    public void onStoreReset() {
        SwingUtilities.invokeLater(this::refreshMessagesList);
    }

    private void updateAllData() {
        onStatusChanged(store.getStatus());
        onActiveChannelChanged(store.getActiveChannel());
        updateKpiMetrics();
    }

    private void updateKpiMetrics() {
        MessageStore.StoreStats stats = store.getStats();
        lblKpiTotalMessagesVal.setText(String.valueOf(stats.totalMessages()));
        lblKpiChannelsCountVal.setText(String.valueOf(stats.channelCount()));
    }

    private void refreshMessagesList() {
        messageListModel.clear();
        String activeCh = store.getActiveChannel();
        List<ChatMessage> list = store.getMessages(activeCh);
        for (ChatMessage msg : list) {
            if (matchesFilter(msg)) {
                messageListModel.addElement(msg);
            }
        }
        if (!messageListModel.isEmpty()) {
            messageList.ensureIndexIsVisible(messageListModel.getSize() - 1);
        }
    }

    private boolean matchesFilter(ChatMessage msg) {
        if (searchFilter.isEmpty()) return true;
        if (msg.getContent() != null && msg.getContent().toLowerCase().contains(searchFilter)) return true;
        if (msg.getSender() != null && msg.getSender().toLowerCase().contains(searchFilter)) return true;
        return false;
    }

    private void updateSecurityInfoText() {
        if (txtSecurityInfo == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== WSBroker Client Information ===\n");
        sb.append("Client Identity: ").append(config.getName()).append("\n");
        sb.append("Broker Endpoint: ").append(config.getWsbroker()).append("\n");
        sb.append("Config File:     ").append(config.getConfigFilePath() != null ? config.getConfigFilePath() : "wschat.xml").append("\n\n");

        sb.append("=== Dynamic mTLS Certificate Status ===\n");
        if (config.getClientTls() != null) {
            sb.append("Loader Type:     ").append(config.getClientTls().getType()).append("\n");
            sb.append("Protocol:        ").append(config.getClientTls().getTlsMinVersion() != null ? config.getClientTls().getTlsMinVersion() : "TLSv1.3").append("\n");
            if (chatClient != null && chatClient.getCertLoader() != null) {
                CertLoader cl = chatClient.getCertLoader();
                sb.append("Loader Status:   Active & Hot-Reloading Enabled\n");
                sb.append("Active KeyPair:  ").append(cl.getCurrentCertificate() != null ? "Loaded & Valid" : "Pending").append("\n");
                sb.append("Trust Manager:   ").append(cl.getTrustManager() != null ? "Configured" : "System Pool").append("\n");
            }
        } else {
            sb.append("mTLS:            Disabled / Plain TLS\n");
        }

        sb.append("\n=== Channels & PubSub ===\n");
        sb.append("Configured:      ").append(String.join(", ", config.getChannels())).append("\n");
        sb.append("Current Active:  #").append(store.getActiveChannel()).append("\n");
        sb.append("Total Messages:  ").append(store.getStats().totalMessages()).append("\n");

        txtSecurityInfo.setText(sb.toString());
        txtSecurityInfo.setCaretPosition(0);
    }

    private void handleExit() {
        int res = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit WSChat?", "Exit Confirmation", JOptionPane.YES_NO_OPTION);
        if (res == JOptionPane.YES_OPTION) {
            if (chatClient != null) {
                chatClient.close();
            }
            dispose();
            System.exit(0);
        }
    }

    // --- Custom Renderers ---

    private class ChannelListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String ch = (String) value;
            int count = store.getMessages(ch).size();
            lbl.setText(String.format("  # %-12s (%d)", ch, count));
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
            lbl.setBorder(new EmptyBorder(6, 4, 6, 4));
            if (isSelected) {
                lbl.setBackground(new Color(14, 165, 233));
                lbl.setForeground(Color.WHITE);
            }
            return lbl;
        }
    }

    private class MessageListCellRenderer extends JPanel implements ListCellRenderer<ChatMessage> {
        private final JLabel lblSender = new JLabel();
        private final JLabel lblTime = new JLabel();
        private final JTextArea txtContent = new JTextArea();

        public MessageListCellRenderer() {
            setLayout(new BorderLayout(4, 2));
            setBorder(new EmptyBorder(6, 8, 6, 8));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            lblSender.setFont(lblSender.getFont().deriveFont(Font.BOLD, 12f));
            lblTime.setFont(lblTime.getFont().deriveFont(Font.PLAIN, 10f));
            lblTime.setForeground(new Color(148, 163, 184));
            top.add(lblSender, BorderLayout.WEST);
            top.add(lblTime, BorderLayout.EAST);

            txtContent.setEditable(false);
            txtContent.setLineWrap(true);
            txtContent.setWrapStyleWord(true);
            txtContent.setOpaque(false);
            txtContent.setFont(txtContent.getFont().deriveFont(Font.PLAIN, 12f));

            add(top, BorderLayout.NORTH);
            add(txtContent, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ChatMessage> list, ChatMessage msg, int index, boolean isSelected, boolean cellHasFocus) {
            boolean isDark = isDarkLaf();
            if (msg.isSelf()) {
                lblSender.setText("You (" + msg.getSender() + ")");
                lblSender.setForeground(new Color(14, 165, 233));
                setBackground(isDark ? new Color(15, 30, 48) : new Color(239, 246, 255));
            } else {
                lblSender.setText("@" + msg.getSender());
                lblSender.setForeground(new Color(168, 85, 247));
                setBackground(isDark ? new Color(30, 41, 59) : new Color(248, 250, 252));
            }
            lblTime.setText(msg.getFormattedTime());
            txtContent.setText(msg.getContent());
            txtContent.setForeground(isDark ? new Color(241, 245, 249) : new Color(15, 23, 42));

            putClientProperty(FlatClientProperties.STYLE, "arc: 8");
            return this;
        }
    }
}
