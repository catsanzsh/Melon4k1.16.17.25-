import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.*;

public class Melon {
    // UI palette
    private static final Color BG = new Color(0x2e2e2e);
    private static final Color FG = new Color(0xffffff);
    private static final Color ACCENT = new Color(0x5fbf00);
    private static final Color ENTRY_BG = new Color(0x454545);

    // Embedded FontAwesome (Base64) - trimmed example
    private static final String FA_BASE64 = "AAEAAAALAIAAAwAwT1Mv..."; // placeholder

    private JFrame frame;
    private JRadioButton offlineRadio, msRadio;
    private JTextField usernameField;
    private JButton msButton, launchButton;
    private JComboBox<String> versionBox;
    private JSlider ramSlider;
    private JLabel ramLabel;

    private String loginType = "offline";
    private String msName, msId;

    private final Properties config = new Properties();
    private static Logger logger;

    public static void main(String[] args) {
        setupLogging();
        logger.info("Starting Melon...");
        SwingUtilities.invokeLater(Melon::new);
    }

    public Melon() {
        loadConfig();
        int maxRam = detectMaxRam();
        String initUser = config.getProperty("offline_username", "");
        loginType = config.getProperty("login_type", "offline");
        int initRam = Integer.parseInt(config.getProperty("ram", String.valueOf(Math.min(4, maxRam))));
        String initVer = config.getProperty("game_type", "Vanilla");
        buildUI(initUser, initRam, maxRam, initVer);
    }

    private void buildUI(String user, int initRam, int maxRam, String initVer) {
        applyDarkTheme();
        frame = new JFrame("Melon");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(800, 600);
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Melon Client");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(20,0,10,0));
        frame.add(title);

        JPanel loginP = new JPanel(new FlowLayout(FlowLayout.CENTER)); loginP.setBackground(BG);
        offlineRadio = new JRadioButton("Offline"); msRadio = new JRadioButton("Microsoft");
        offlineRadio.setBackground(BG); offlineRadio.setForeground(FG);
        msRadio.setBackground(BG); msRadio.setForeground(FG);
        offlineRadio.setActionCommand("offline"); msRadio.setActionCommand("microsoft");
        offlineRadio.addActionListener(e -> toggleLogin());
        msRadio.addActionListener(e -> toggleLogin());
        ButtonGroup bg = new ButtonGroup(); bg.add(offlineRadio); bg.add(msRadio);
        loginP.add(offlineRadio); loginP.add(msRadio); frame.add(loginP);
        if("microsoft".equals(loginType)) msRadio.setSelected(true); else offlineRadio.setSelected(true);

        frame.add(label("Username:"));
        usernameField = new JTextField(user, 16); styleTextField(usernameField); frame.add(usernameField);
        msButton = new JButton("Login with Microsoft"); styleButton(msButton); msButton.addActionListener(e -> loginMs()); frame.add(msButton);

        frame.add(label("Game Type:"));
        versionBox = new JComboBox<>(new String[]{"Vanilla","Forge","Fabric"}); versionBox.setSelectedItem(initVer);
        versionBox.setMaximumSize(new Dimension(200,30)); frame.add(versionBox);

        ramLabel = label(String.format("RAM (GB): %d", initRam)); frame.add(ramLabel);
        ramSlider = new JSlider(1, maxRam, initRam); ramSlider.setMaximumSize(new Dimension(300,50));
        ramSlider.addChangeListener(e -> ramLabel.setText("RAM (GB): " + ramSlider.getValue())); frame.add(ramSlider);

        launchButton = new JButton("Launch"); styleButton(launchButton);
        launchButton.setFont(launchButton.getFont().deriveFont(Font.BOLD));
        launchButton.addActionListener(e -> launch()); frame.add(Box.createVerticalStrut(20)); frame.add(launchButton);

        toggleLogin();
        frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { onClose(); }});
        frame.setLocationRelativeTo(null); frame.setVisible(true);

        // 60 FPS Timer example (for future animations)
        new Timer(16, e -> frame.repaint()).start();
        logger.info("UI ready.");
    }

    private void toggleLogin() {
        loginType = offlineRadio.isSelected() ? "offline" : "microsoft";
        boolean off = "offline".equals(loginType);
        usernameField.setVisible(off); ramLabel.setVisible(off);
        msButton.setVisible(!off);
    }

    private void loginMs() {
        msName = "Player"; msId = UUID.randomUUID().toString();
        JOptionPane.showMessageDialog(frame, "Microsoft login placeholder.", "MS Login", JOptionPane.INFORMATION_MESSAGE);
    }

    private void launch() {
        String user = usernameField.getText().trim();
        if("offline".equals(loginType) && !user.matches("^[A-Za-z0-9_]{3,16}$")) {
            JOptionPane.showMessageDialog(frame, "Invalid username.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        if("microsoft".equals(loginType) && msId==null) {
            JOptionPane.showMessageDialog(frame, "Please login.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        int ram = ramSlider.getValue(); if(ram>detectMaxRam()) {
            JOptionPane.showMessageDialog(frame, "RAM exceeds system.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        // save config
        if("offline".equals(loginType)) config.setProperty("offline_username", user);
        config.setProperty("login_type", loginType);
        config.setProperty("ram", String.valueOf(ram));
        config.setProperty("game_type", versionBox.getSelectedItem().toString());
        saveConfig();
        // build and start process
        try {
            String mc = getMcDir(); String ver = findVersion(versionBox.getSelectedItem().toString());
            ProcessBuilder pb = new ProcessBuilder(buildCmd(ver, mc, user, ram));
            pb.directory(new File(mc)).start();
            JOptionPane.showMessageDialog(frame, "Launching Minecraft...", "Launch", JOptionPane.INFORMATION_MESSAGE);
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Launch failed", ex);
            JOptionPane.showMessageDialog(frame, "Launch failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private java.util.List<String> buildCmd(String version, String mcDir, String user, int ram) {
        String sep = System.getProperty("path.separator");
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("java"); cmd.add("-Xmx"+ram+"G"); cmd.add("-Xms"+(ram/2>0?ram/2:1)+"G");
        String natives = mcDir + File.separator + "versions"+File.separator+version+File.separator+"natives";
        cmd.add("-Djava.library.path="+natives);
        String libs = mcDir+File.separator+"libraries"+File.separator+"*"+sep+mcDir+File.separator+"versions"+File.separator+version+File.separator+version+".jar";
        cmd.add("-cp"); cmd.add(libs);
        cmd.add("net.minecraft.client.main.Main"); cmd.add("--username"); cmd.add(user);
        cmd.add("--uuid"); cmd.add("offline".equals(loginType)?uuidOffline(user):msId);
        cmd.add("--accessToken"); cmd.add("offline".equals(loginType)?"null":"token");
        cmd.add("--version"); cmd.add(version);
        cmd.add("--gameDir"); cmd.add(mcDir);
        return cmd;
    }

    private static String findVersion(String t) {
        switch(t) { case "Forge": return "1.20.1-forge-47.2.20"; case "Fabric": return "fabric-loader-0.15.7-1.20.4"; default: return "1.20.4"; }
    }

    private static String getMcDir() {
        String os = System.getProperty("os.name").toUpperCase();
        if(os.contains("WIN")) { String ad = System.getenv("APPDATA"); if(ad!=null) return ad+"\\.minecraft"; }
        if(os.contains("MAC")) return System.getProperty("user.home")+"/Library/Application Support/minecraft";
        return System.getProperty("user.home")+"/.minecraft";
    }

    private static int detectMaxRam() {
        try { var os = ManagementFactory.getOperatingSystemMXBean();
            var m = os.getClass().getMethod("getTotalPhysicalMemorySize");
            long bytes = (Long)m.invoke(os);
            return (int)Math.max(1, bytes/1_073_741_824L);
        } catch(Exception e) { long heap = Runtime.getRuntime().maxMemory(); return (int)Math.max(1, heap*4/1_073_741_824L); }
    }

    private static String uuidOffline(String u) {
        try { MessageDigest md=MessageDigest.getInstance("MD5"); byte[] d=md.digest(("OfflinePlayer:"+u).getBytes(StandardCharsets.UTF_8)); d[6]&=0x0f; d[6]|=0x30; d[8]&=0x3f; d[8]|=0x80;
            long msb=0, lsb=0; for(int i=0;i<8;i++) msb=(msb<<8)|(d[i]&0xff); for(int i=8;i<16;i++) lsb=(lsb<<8)|(d[i]&0xff);
            return new UUID(msb,lsb).toString(); } catch(NoSuchAlgorithmException e) { return UUID.randomUUID().toString(); }
    }

    private static void setupLogging() {
        logger = Logger.getLogger("Melon");
        try { FileHandler fh = new FileHandler("melon_client.log", true); fh.setFormatter(new SimpleFormatter()); logger.addHandler(fh); logger.setUseParentHandlers(false); logger.setLevel(Level.INFO);
        } catch(Exception e) { logger.addHandler(new ConsoleHandler()); logger.log(Level.WARNING, "Log file failed, using console.", e); }
    }

    private void loadConfig() {
        try(var in = new FileInputStream("melonclient.properties")) { config.load(in); }
        catch(IOException ignored) { logger.info("No config, using defaults."); }
    }

    private void saveConfig() {
        try(var out = new FileOutputStream("melonclient.properties")) { config.store(out, "Melon config"); }
        catch(IOException e) { logger.log(Level.WARNING, "Config save failed.", e); }
    }

    private void onClose() {
        logger.info("Saving on exit...");
        if("offline".equals(loginType)) config.setProperty("offline_username", usernameField.getText().trim());
        config.setProperty("login_type", loginType);
        config.setProperty("ram", String.valueOf(ramSlider.getValue()));
        config.setProperty("game_type", versionBox.getSelectedItem().toString());
        saveConfig(); frame.dispose(); SwingUtilities.invokeLater(() -> { if(Frame.getFrames().length==0) System.exit(0); });
    }

    // UI Helpers
    private JLabel label(String t) { JLabel l = new JLabel(t); l.setForeground(FG); l.setAlignmentX(Component.CENTER_ALIGNMENT); l.setBorder(BorderFactory.createEmptyBorder(10,0,5,0)); return l; }
    private void styleButton(JButton b) { b.setBackground(ACCENT); b.setForeground(Color.BLACK); b.setAlignmentX(Component.CENTER_ALIGNMENT); }
    private void styleTextField(JTextField tf) { tf.setBackground(ENTRY_BG); tf.setForeground(FG); tf.setCaretColor(FG); tf.setMaximumSize(new Dimension(250,30)); tf.setAlignmentX(Component.CENTER_ALIGNMENT); }
    private void applyDarkTheme() {
        UIManager.put("Panel.background", BG); UIManager.put("Label.foreground", FG);
        UIManager.put("Button.background", ENTRY_BG); UIManager.put("Button.foreground", FG);
        UIManager.put("RadioButton.background", BG); UIManager.put("RadioButton.foreground", FG);
        UIManager.put("ComboBox.background", ENTRY_BG); UIManager.put("ComboBox.foreground", FG);
        UIManager.put("TextField.background", ENTRY_BG); UIManager.put("TextField.foreground", FG);
    }
}
