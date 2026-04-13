/**
 *
 */
package in.adarshr.screenshotcaptor;

import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * @author Adarsh
 *
 */
public class ScreenShotCaptor extends JFrame implements ActionListener {
	private static final long serialVersionUID = 516957436464978811L;
	private static final double RTF_IMAGE_WIDTH_INCHES = 6.0;
	private static final int TWIPS_PER_INCH = 1440;

	private final JButton picButton;
	private final JTextField fileNameText;
	private final JRadioButton fullScreenMode;
	private final JRadioButton regionMode;
	private final JCheckBox saveImageCheck;
	private final JCheckBox saveRtfCheck;
	private final JButton browseButton;
	private final JButton importButton;
	private final JLabel rtfPathLabel;
	private File rtfTarget;
	private File lastImportDir;
	/** After capture, restore the filename field when a custom base name was used; otherwise clear it. */
	private String pendingFileNameAfterCapture;
	private String defaultFileNameText = "File Name";
	Properties properties;

	public ScreenShotCaptor() {
		properties = new Properties();
		try(InputStream inputStream = getClass().getResourceAsStream("ScreenShotCaptor.properties")) {
			if (inputStream != null) {
				properties.load(inputStream);
				defaultFileNameText = properties.getProperty("TextFieldDefaultString", defaultFileNameText);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		applyLookAndFeel(properties.getProperty("LookAndFeel"));
		applyFontDefaults();

		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setResizable(false);
		this.setTitle(properties.getProperty("Title", "ScreenShot Captor"));

		String btnName = properties.getProperty("ButtonName", "Take Screenshot");
		picButton = new JButton(btnName);
		picButton.setFont(picButton.getFont().deriveFont(Font.BOLD, 14f));
		picButton.setFocusPainted(false);
		picButton.addActionListener(this);
		picButton.setToolTipText("Take a screenshot using the selected mode");

		fileNameText = new JTextField(defaultFileNameText);
		fileNameText.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (defaultFileNameText.equals(fileNameText.getText())) {
					fileNameText.setText("");
				}
				super.mouseClicked(e);
			}
		});
		fileNameText.addActionListener(this);
		fileNameText.setToolTipText("Optional custom filename. Leave blank for an auto-generated timestamp.");

		fullScreenMode = new JRadioButton("Full screen", true);
		fullScreenMode.setToolTipText("Capture every connected display");
		regionMode = new JRadioButton("Region");
		regionMode.setToolTipText("Drag to select a rectangle. Esc cancels.");
		ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(fullScreenMode);
		modeGroup.add(regionMode);

		saveImageCheck = new JCheckBox("Image to disk", true);
		saveImageCheck.setToolTipText("Write the screenshot to the configured Location and Format");
		saveImageCheck.addItemListener(e -> updateCaptureEnabled());

		saveRtfCheck = new JCheckBox("RTF document", false);
		saveRtfCheck.setToolTipText("Append the screenshot to a Rich Text document for help-doc workflows");
		saveRtfCheck.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED && rtfTarget == null) {
				if (!chooseRtfTarget()) {
					saveRtfCheck.setSelected(false);
				}
			}
			updateCaptureEnabled();
		});

		browseButton = new JButton("Browse...");
		browseButton.setFocusPainted(false);
		browseButton.setToolTipText("Pick the RTF document to append to");
		browseButton.addActionListener(e -> chooseRtfTarget());

		importButton = new JButton("Add image to RTF...");
		importButton.setFocusPainted(false);
		importButton.addActionListener(e -> importImagesToRtf());

		rtfPathLabel = new JLabel("no RTF file selected");
		rtfPathLabel.setForeground(new Color(110, 110, 110));
		rtfPathLabel.setFont(rtfPathLabel.getFont().deriveFont(Font.ITALIC, 11f));

		layoutContent();

		updateCaptureEnabled();
		this.pack();
		Dimension frameDimension = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(frameDimension.width/2 - this.getWidth()/2,
				frameDimension.height/2 - this.getHeight()/2);
		this.setVisible(true);
	}

	private void layoutContent() {
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

		picButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		picButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		root.add(picButton);

		root.add(Box.createVerticalStrut(10));

		fileNameText.setAlignmentX(Component.LEFT_ALIGNMENT);
		fileNameText.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		root.add(fileNameText);

		root.add(Box.createVerticalStrut(12));

		JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
		modePanel.setBorder(BorderFactory.createTitledBorder("Mode"));
		modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		modePanel.add(fullScreenMode);
		modePanel.add(regionMode);
		root.add(modePanel);

		root.add(Box.createVerticalStrut(8));

		JPanel outputPanel = new JPanel();
		outputPanel.setLayout(new BoxLayout(outputPanel, BoxLayout.Y_AXIS));
		outputPanel.setBorder(BorderFactory.createTitledBorder("Save to"));
		outputPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel checksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		checksRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		checksRow.add(saveImageCheck);
		checksRow.add(saveRtfCheck);
		outputPanel.add(checksRow);

		JPanel browseRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		browseRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		browseRow.add(browseButton);
		browseRow.add(rtfPathLabel);
		outputPanel.add(browseRow);

		JPanel importRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		importRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		importRow.add(importButton);
		outputPanel.add(importRow);

		root.add(outputPanel);

		this.setContentPane(root);
	}

	private void applyLookAndFeel(String requested) {
		try {
			if (requested != null && !requested.isEmpty()) {
				UIManager.setLookAndFeel(requested);
				return;
			}
			for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					UIManager.setLookAndFeel(info.getClassName());
					return;
				}
			}
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException ex) {
			ex.printStackTrace();
		}
	}

	private void applyFontDefaults() {
		Font body = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		Font bold = body.deriveFont(Font.BOLD);
		String[] keys = {
				"Button.font", "Label.font", "CheckBox.font", "RadioButton.font",
				"TextField.font", "ToolTip.font", "OptionPane.messageFont",
				"OptionPane.buttonFont", "FileChooser.listFont"
		};
		for (String key : keys) {
			UIManager.put(key, body);
		}
		UIManager.put("TitledBorder.font", bold.deriveFont(12f));
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(ScreenShotCaptor::new);
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == picButton || e.getSource() == fileNameText) {
			if (!saveImageCheck.isSelected() && !saveRtfCheck.isSelected()) {
				return;
			}
			if (regionMode.isSelected()) {
				doRegionCapture();
			} else {
				doFullScreenCapture();
			}
		}
	}

	private void updateCaptureEnabled() {
		boolean any = saveImageCheck.isSelected() || saveRtfCheck.isSelected();
		picButton.setEnabled(any);
		picButton.setToolTipText(any ? null : "Enable at least one output");
		boolean rtfReady = rtfTarget != null;
		importButton.setEnabled(rtfReady);
		importButton.setToolTipText(rtfReady
				? "Append existing image files to the RTF document"
				: "Pick an RTF document first via Browse");
	}

	private boolean chooseRtfTarget() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose RTF document");
		chooser.setFileFilter(new FileNameExtensionFilter("Rich Text Format (*.rtf)", "rtf"));
		if (rtfTarget != null) {
			chooser.setSelectedFile(rtfTarget);
		}
		int result = chooser.showSaveDialog(this);
		if (result != JFileChooser.APPROVE_OPTION) {
			return false;
		}
		File chosen = chooser.getSelectedFile();
		if (chosen == null) {
			return false;
		}
		if (!chosen.getName().toLowerCase().endsWith(".rtf")) {
			File parent = chosen.getParentFile();
			chosen = parent != null
					? new File(parent, chosen.getName() + ".rtf")
					: new File(chosen.getName() + ".rtf");
		}
		rtfTarget = chosen;
		rtfPathLabel.setText(rtfTarget.getName());
		rtfPathLabel.setToolTipText(rtfTarget.getAbsolutePath());
		updateCaptureEnabled();
		return true;
	}

	private void importImagesToRtf() {
		if (rtfTarget == null) {
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Add images to " + rtfTarget.getName());
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileFilter(new FileNameExtensionFilter(
				"Image files (*.png, *.jpg, *.jpeg, *.gif, *.bmp)",
				"png", "jpg", "jpeg", "gif", "bmp"));
		if (lastImportDir != null && lastImportDir.isDirectory()) {
			chooser.setCurrentDirectory(lastImportDir);
		}
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File[] files = chooser.getSelectedFiles();
		if (files == null || files.length == 0) {
			return;
		}
		lastImportDir = chooser.getCurrentDirectory();

		int ok = 0;
		java.util.List<String> failures = new java.util.ArrayList<>();
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		for (File file : files) {
			try {
				BufferedImage img = ImageIO.read(file);
				if (img == null) {
					failures.add(file.getName() + ": unsupported or unreadable image");
					continue;
				}
				String baseName = stripExtension(file.getName());
				String readableTime = fmt.format(new java.util.Date(file.lastModified()));
				RtfAppender.append(rtfTarget, img, baseName, readableTime);
				ok++;
			} catch (IOException ex) {
				failures.add(file.getName() + ": " + ex.getMessage());
			}
		}

		StringBuilder summary = new StringBuilder();
		summary.append(ok).append(ok == 1 ? " image appended to " : " images appended to ");
		summary.append(rtfTarget.getName()).append('.');
		if (!failures.isEmpty()) {
			summary.append("\n\n").append(failures.size()).append(" failed:");
			for (String f : failures) {
				summary.append("\n  \u2022 ").append(f);
			}
		}
		JOptionPane.showMessageDialog(this, summary.toString(),
				"Import complete",
				failures.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private Rectangle virtualScreenBounds() {
		Rectangle bounds = new Rectangle();
		for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			bounds = bounds.union(gd.getDefaultConfiguration().getBounds());
		}
		return bounds;
	}

	private void doFullScreenCapture() {
		pendingFileNameAfterCapture = null;
		this.setVisible(false);
		Timer t = new Timer(150, ev -> {
			try {
				captureRect(virtualScreenBounds());
			} finally {
				afterCapture();
			}
		});
		t.setRepeats(false);
		t.start();
	}

	private void doRegionCapture() {
		pendingFileNameAfterCapture = null;
		this.setVisible(false);
		Rectangle vBounds = virtualScreenBounds();
		RegionSelector.show(vBounds, selected -> {
			try {
				if (selected != null && selected.width > 0 && selected.height > 0) {
					Rectangle screenRect = new Rectangle(
							vBounds.x + selected.x,
							vBounds.y + selected.y,
							selected.width,
							selected.height);
					captureRect(screenRect);
				}
			} finally {
				afterCapture();
			}
		});
	}

	private void afterCapture() {
		this.setVisible(true);
		if (pendingFileNameAfterCapture != null) {
			fileNameText.setText(pendingFileNameAfterCapture);
			pendingFileNameAfterCapture = null;
		} else {
			fileNameText.setText("");
		}
	}

	private void captureRect(Rectangle rect) {
		String typed = fileNameText.getText();
		String customName = (typed != null && typed.length() > 0
				&& !defaultFileNameText.equalsIgnoreCase(typed)) ? typed : null;
		pendingFileNameAfterCapture = customName != null ? typed : null;

		try {
			BufferedImage bufferedImage = new Robot().createScreenCapture(rect);

			String format = properties.getProperty("Format", "png");
			String prefix = getPrefix(properties.getProperty("Prefix"));
			String fileTime = properties.getProperty("TimeFormat", "yyyyMMdd_HHmmssSSS");
			Calendar now = Calendar.getInstance();
			String generatedName = prefix + new SimpleDateFormat(fileTime).format(now.getTime());
			String baseName = customName == null ? generatedName : customName;

			if (saveImageCheck.isSelected()) {
				String location = getLocation(properties.getProperty("Location"));
				File file = new File(location + baseName + "." + format);
				ImageIO.write(bufferedImage, format, file);
			}

			if (saveRtfCheck.isSelected() && rtfTarget != null) {
				String readableTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now.getTime());
				try {
					RtfAppender.append(rtfTarget, bufferedImage, baseName, readableTime);
				} catch (IOException rtfEx) {
					JOptionPane.showMessageDialog(this,
							"Could not append to " + rtfTarget.getName() + ".\n"
									+ "Is it open in Word? The image is still saved to disk if that option is on.\n\n"
									+ rtfEx.getMessage(),
							"RTF write failed", JOptionPane.WARNING_MESSAGE);
				}
			}
		} catch (AWTException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Screen capture failed (AWT).\n" + e.getMessage(),
					"Capture failed", JOptionPane.ERROR_MESSAGE);
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Could not save the screenshot.\n" + e.getMessage(),
					"Save failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * File name prefix
	 * @return String
	 */
	private String getPrefix(String prefix) {
		return prefix == null ? "IMG_":prefix;
	}

	/**
	 * File name location
	 * @return result
	 */
	private String getLocation(String location) {
		if (location == null || location.isEmpty()) {
			return Paths.get("").toAbsolutePath().toString() + File.separator;
		}
		String loc = location.replace('/', File.separatorChar).replace('\\', File.separatorChar);
		if (!loc.endsWith(File.separator)) {
			loc = loc + File.separator;
		}
		return loc;
	}

	/**
	 * Appends an image (encoded as PNG) plus a two-line caption to an RTF file,
	 * creating the file if it does not yet exist.
	 */
	private static final class RtfAppender {
		private static final String SKELETON = "{\\rtf1\\ansi\\deff0\n}";

		static void append(File target, BufferedImage image, String filenameLine, String timestampLine) throws IOException {
			if (!target.exists() || target.length() == 0) {
				Files.write(target.toPath(), SKELETON.getBytes(StandardCharsets.US_ASCII));
			}

			byte[] pngBytes;
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				ImageIO.write(image, "png", baos);
				pngBytes = baos.toByteArray();
			}

			int pixelWidth = image.getWidth();
			int pixelHeight = image.getHeight();
			int targetWidthTwips = (int) Math.round(RTF_IMAGE_WIDTH_INCHES * TWIPS_PER_INCH);
			int targetHeightTwips = (int) Math.round(targetWidthTwips * (pixelHeight / (double) pixelWidth));

			StringBuilder fragment = new StringBuilder(pngBytes.length * 2 + 256);
			fragment.append("\\pard\\sa120 ").append(rtfEscape(filenameLine)).append("\\line ");
			fragment.append(rtfEscape(timestampLine)).append("\\par\n");
			fragment.append("{\\pict\\pngblip");
			fragment.append("\\picw").append(pixelWidth);
			fragment.append("\\pich").append(pixelHeight);
			fragment.append("\\picwgoal").append(targetWidthTwips);
			fragment.append("\\pichgoal").append(targetHeightTwips).append('\n');
			appendHex(fragment, pngBytes);
			fragment.append("\n}\\par\n");

			byte[] fragmentBytes = fragment.toString().getBytes(StandardCharsets.US_ASCII);

			try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
				long len = raf.length();
				long insertAt = findLastBraceOffset(raf, len);
				if (insertAt < 0) {
					throw new IOException("RTF file appears malformed (no closing brace): " + target);
				}
				raf.seek(insertAt);
				raf.write(fragmentBytes);
				raf.write('}');
				raf.setLength(insertAt + fragmentBytes.length + 1);
			}
		}

		private static long findLastBraceOffset(RandomAccessFile raf, long len) throws IOException {
			for (long pos = len - 1; pos >= 0; pos--) {
				raf.seek(pos);
				int b = raf.read();
				if (b == '}') {
					return pos;
				}
				if (b != '\n' && b != '\r' && b != ' ' && b != '\t') {
					return -1;
				}
			}
			return -1;
		}

		private static void appendHex(StringBuilder sb, byte[] bytes) {
			final char[] hex = "0123456789abcdef".toCharArray();
			int lineLen = 0;
			for (byte b : bytes) {
				sb.append(hex[(b >> 4) & 0xF]);
				sb.append(hex[b & 0xF]);
				lineLen += 2;
				if (lineLen >= 128) {
					sb.append('\n');
					lineLen = 0;
				}
			}
		}

		private static String rtfEscape(String s) {
			if (s == null) {
				return "";
			}
			StringBuilder out = new StringBuilder(s.length() + 8);
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (c == '\\' || c == '{' || c == '}') {
					out.append('\\').append(c);
				} else if (c < 0x80) {
					out.append(c);
				} else {
					out.append("\\u").append((int) (short) c).append('?');
				}
			}
			return out.toString();
		}
	}

	/**
	 * Full-virtual-screen translucent overlay that lets the user drag a rectangle.
	 * Calls back with the selected Rectangle in overlay-local coordinates, or null on cancel.
	 */
	private static final class RegionSelector {
		static void show(Rectangle virtualBounds, Consumer<Rectangle> onSelected) {
			final JWindow window = new JWindow();
			window.setBounds(virtualBounds);
			try {
				window.setBackground(new Color(0, 0, 0, 0));
			} catch (UnsupportedOperationException ex) {
				window.setOpacity(0.35f);
			}

			final Point[] start = { null };
			final Rectangle[] selection = { null };

			final JPanel panel = new JPanel(null) {
				private static final long serialVersionUID = 1L;
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setColor(new Color(0, 0, 0, 110));
					g2.fillRect(0, 0, getWidth(), getHeight());
					Rectangle s = selection[0];
					if (s != null && s.width > 0 && s.height > 0) {
						g2.setComposite(AlphaComposite.Clear);
						g2.fillRect(s.x, s.y, s.width, s.height);
						g2.setComposite(AlphaComposite.SrcOver);
						g2.setColor(Color.RED);
						g2.setStroke(new BasicStroke(2f));
						g2.drawRect(s.x, s.y, s.width, s.height);
					}
					g2.dispose();
				}
			};
			panel.setOpaque(false);
			panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

			MouseAdapter mouse = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					start[0] = e.getPoint();
					selection[0] = new Rectangle(e.getX(), e.getY(), 0, 0);
					panel.repaint();
				}
				@Override
				public void mouseDragged(MouseEvent e) {
					if (start[0] == null) {
						return;
					}
					int x = Math.min(start[0].x, e.getX());
					int y = Math.min(start[0].y, e.getY());
					int w = Math.abs(e.getX() - start[0].x);
					int h = Math.abs(e.getY() - start[0].y);
					selection[0] = new Rectangle(x, y, w, h);
					panel.repaint();
				}
				@Override
				public void mouseReleased(MouseEvent e) {
					final Rectangle result = selection[0];
					window.dispose();
					Timer t = new Timer(150, ev -> onSelected.accept(result));
					t.setRepeats(false);
					t.start();
				}
			};
			panel.addMouseListener(mouse);
			panel.addMouseMotionListener(mouse);

			panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
					KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
			panel.getActionMap().put("cancel", new AbstractAction() {
				private static final long serialVersionUID = 1L;
				@Override
				public void actionPerformed(ActionEvent e) {
					window.dispose();
					Timer t = new Timer(150, ev -> onSelected.accept(null));
					t.setRepeats(false);
					t.start();
				}
			});

			window.setContentPane(panel);
			window.setAlwaysOnTop(true);
			window.setVisible(true);
			panel.requestFocusInWindow();
		}
	}
}
