/**
 * 
 */
package in.adarshr.screenshotcaptor;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * @author Adarsh
 *
 */
public class ScreenShotCaptor extends JFrame implements ActionListener, KeyListener{
	private static final long serialVersionUID = 516957436464978811L;
	private JButton picButton;
	private JTextField fileNameText;
	private String defaultFileNameText;
	Properties properties;
	
	public ScreenShotCaptor() {
		properties = new Properties();
		try(InputStream inputStream = new  FileInputStream("ScreenShotCaptor.properties")) {			
			properties.load(inputStream);
			defaultFileNameText = properties.getProperty("TextFieldDefaultString");
			UIManager.setLookAndFeel(properties.getProperty("LookAndFeel"));
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException | IOException e) {
			e.printStackTrace();
		} 
	
		this.getContentPane().setLayout(new FlowLayout());
		this.setVisible(Boolean.TRUE);
		this.setLayout(null);
		
		//Frame
		Dimension frameDimension = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(frameDimension.width/2 - this.getSize().width/2, 
				frameDimension.height/2 - this.getSize().height/2);
		this.setSize(218,  110); //215, 78
		
		//ScreenCapture button
		String btnName = properties.getProperty("ButtonName");
		picButton = new JButton(btnName);
		picButton.setBounds(0, 0, 200, 40);
		picButton.setBackground(Color.GREEN);
		picButton.setVisible(Boolean.TRUE);
		picButton.addActionListener(this);
		this.getContentPane().add(picButton);
		
		//FileNameText
		fileNameText = new JTextField(defaultFileNameText);
		fileNameText.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				fileNameText.setText("");
				super.mouseClicked(e);
			}
		});
		fileNameText.setBounds(0,40,200,22);
		fileNameText.setVisible(Boolean.TRUE);
		fileNameText.addActionListener(this);
		fileNameText.setBackground(Color.LIGHT_GRAY);
		fileNameText.setForeground(Color.BLUE);
		this.getContentPane().add(fileNameText);
		
		//Disable Maximize Button
		this.setResizable(Boolean.FALSE);
		
		//Tile
		String title = properties.getProperty("Title");
		this.setTitle(title);
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ScreenShotCaptor();
	}
	
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == picButton || e.getSource() == fileNameText) {
			//Capture Image
			doCapture();
			
			//Show the screen again
			this.setVisible(true);
			fileNameText.setText("");
		}
	}
	
	public void doCapture(){
		String fileName = null;
		SimpleDateFormat formatter;
		if(fileNameText.getText() != null && !defaultFileNameText.equalsIgnoreCase(fileNameText.getText())) {
			fileName = fileNameText.getText();
		}
		this.setVisible(false);
		try {
			Calendar now = Calendar.getInstance();
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			Dimension dimension = toolkit.getScreenSize();
			Rectangle rectangle = new Rectangle(0, 0, dimension.width, dimension.height);
			Robot robot = new Robot();
			BufferedImage bufferedImage = robot.createScreenCapture(rectangle);

			String location = getLocation(properties.getProperty("Location"));
			String format = properties.getProperty("Format", "png");
			String prefix = getPrefix(properties.getProperty("Prefix"));
			String fileTime = properties.getProperty("TimeFormat", "yyyyMMdd_hhmmssSSS_a");
			
			formatter = new SimpleDateFormat(fileTime);
			String fileNameFormat = prefix + formatter.format(now.getTime());
			File file = new File(location + ((fileName == null || fileName.length() == 0) ? fileNameFormat:fileName) 
					+ "." + format);
			ImageIO.write(bufferedImage, format, file);
		} catch (AWTException | IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * File name prefix
	 * 
	 * @param prefix
	 * @return
	 */
	private String getPrefix(String prefix) {
		return prefix == null ? "IMG_":prefix;
	}
	
	/**
	 * File name location
	 * 
	 * @param location
	 * @return
	 */
	private String getLocation(String location) {
		if(location == null) {
			Path currentRelativePath = Paths.get("");
			return currentRelativePath.toAbsolutePath().toString()+"\\";
		}
		return location;
	}
	
	@Override
	public void keyTyped(KeyEvent e) {
		//NOSONAR
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if(e.getKeyCode()==KeyEvent.VK_ENTER){
			picButton.doClick();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		//NOSONAR
	}
}
