package pacman;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;

import javax.swing.*;

import myLib.CharacterState;
import myLib.GameState;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class App {

	int serverPort = 6666;
	Socket socket, dataSocket;
	ServerSocket ss;
	DataInputStream in;
	DataOutputStream out, dout;
	ObjectInputStream doin;
	DataInputStream din;
	String[] roomList;
	String myRoom = "";
	GameState gameState;
	SocketReader processor;
	Thread thread;
	Timer painter;

	private JFrame gameWindow;
	private JFrame mainWindow;
	protected JMenuItem mntmDisconnect;
	protected JMenuItem mntmConnectTo;
	@SuppressWarnings("rawtypes")
	protected JList list;
	private JButton btnNewRoom;
	private JButton btnEnter;
	private JButton btnSpectate;
	private JButton btnLeaveRoom;
	private JButton btnRefresh;
	private DrawingArea drawingArea;
	public int[][] board;

	class DrawingArea extends JPanel {

		private static final long serialVersionUID = -2004830881976534775L;
		public static final int cellSize = 18;
		BufferedImage image, imgBoard;
		Graphics2D g2dBoard, g2dImage;
		Point startPoint = null;
		Point endPoint = null;

		public DrawingArea() {
			setBackground(Color.WHITE);
		}

		public void paintComponent(Graphics g) {
			super.paintComponent(g);

			if (image != null) {
				g.drawImage(image, 0, 0, null);
			}
		}

		public void PaintState() {
			
			if (gameState != null){
				board = gameState.board;
				PaintBoard();
				imgBoard.copyData(image.getRaster());
				for (CharacterState ch : gameState.cs) {
					g2dImage.setColor(Color.DARK_GRAY);
					switch (ch.id) {
					case 1:
						g2dImage.setColor(Color.RED);
						break;
					case 2:
						g2dImage.setColor(Color.GREEN);
						break;
					case 3:
						g2dImage.setColor(Color.BLUE);
						break;
					case 4:
						g2dImage.setColor(Color.ORANGE);
						break;
					default:
						break;
					}
					int x = ch.cell.x * cellSize;
					int y = ch.cell.y * cellSize;
					if (Math.abs(ch.direction) > 1)
						x += (ch.dist * cellSize / 2);
					else
						y += (ch.dist * cellSize / 2);
					g2dImage.fillOval(y, x, cellSize, cellSize);
				}
			}
			repaint();
		}

		public void PaintBoard() {
			image = new BufferedImage(board[0].length * cellSize, board.length
					* cellSize, BufferedImage.TYPE_INT_ARGB);
			imgBoard = new BufferedImage(board[0].length * cellSize,
					board.length * cellSize, BufferedImage.TYPE_INT_ARGB);
			g2dBoard = (Graphics2D) imgBoard.getGraphics();
			g2dImage = (Graphics2D) image.getGraphics();
			for (int row = 0; row < board.length; row++) {
				for (int col = 0; col < board[0].length; col++) {
					if (board[row][col] == -1) {
						g2dBoard.setColor(Color.lightGray);
					} else if (board[row][col] == 5) {
						g2dBoard.setColor(Color.cyan);
					}
					else {
						g2dBoard.setColor(Color.white);
					}
					g2dBoard.fillRect(col * cellSize, row * cellSize, cellSize,
							cellSize);
				}
			}
			imgBoard.copyData(image.getRaster());
			repaint();
		}
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					App window = new App();
					window.mainWindow.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public App() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {

		MainWindowInit();

		GameWindowInit();

	}

	private void GameWindowInit() {
		gameWindow = new JFrame();
		gameWindow.setResizable(false);
		gameWindow.setBounds(100, 100, DrawingArea.cellSize * 28 + 6,
				DrawingArea.cellSize * 31 + 50);
		gameWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		gameWindow.addKeyListener(new KeyAdapter() {

			public void keyPressed(KeyEvent e) {
				String key = KeyEvent.getKeyText(e.getKeyCode());
				try {
					dout.writeUTF(key);
				} catch (IOException e1) {
				}
				System.out.println(key);
			}

		});
		gameWindow.getContentPane().setLayout(new BorderLayout(0, 0));

		drawingArea = new DrawingArea();
		gameWindow.getContentPane().add(drawingArea, BorderLayout.CENTER);

		JMenuBar menuBar = new JMenuBar();
		gameWindow.setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				new WindowEvent(gameWindow, WindowEvent.WINDOW_CLOSING);
			}
		});

		gameWindow.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent ev) {
				gameWindow.setVisible(false);
				mainWindow.setVisible(true);
				LeaveRoom();
			}
		});
	}

	private void Send(String str) {
		try {
			out.writeUTF(str);
		} catch (IOException e) {
		}
	}

	private String recv() {
		String result = "";
		try {
			result = in.readUTF();
		} catch (IOException e) {
		}
		return result;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void RefreshRoomList() {
		try {
			out.writeUTF("RefreshRoomList");
			String line = recv();
			DefaultListModel model = new DefaultListModel();
			if (!line.equals("empty")) {
				roomList = line.split(":");
				for (String string : roomList) {
					if (string.substring(0, string.length() - 6).equals(myRoom))
						string = ">>" + string + "<<";
					model.addElement(string);
				}
				list.setModel(model);
			} else {
				roomList = null;
				list.setModel(model);

			}
		} catch (IOException e) {
		}
	}

	private void LeaveRoom() {
		myRoom = "";
		Send("LeaveRoom");
		if (painter != null) painter.cancel();
		RefreshRoomList();
		btnEnter.setEnabled(true);
		btnSpectate.setEnabled(true);
		btnNewRoom.setEnabled(true);
		btnLeaveRoom.setEnabled(false);
	}

	private void GetRoom(String name) {
		myRoom = name;
		RefreshRoomList();
		btnEnter.setEnabled(false);
		btnSpectate.setEnabled(false);
		btnNewRoom.setEnabled(false);
		btnLeaveRoom.setEnabled(true);
	}

	@SuppressWarnings({ "unchecked", "rawtypes", "serial" })
	private void MainWindowInit() {
		mainWindow = new JFrame("Games list");
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainWindow.setBounds(100, 100, 560, 352);

		JMenuBar menuBar = new JMenuBar();
		mainWindow.setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JPanel panel = new JPanel();
		mainWindow.getContentPane().add(panel, BorderLayout.CENTER);
		panel.setLayout(new BorderLayout(0, 0));

		list = new JList();
		list.setModel(new AbstractListModel() {
			String[] values = new String[] {};

			public int getSize() {
				return values.length;
			}

			public Object getElementAt(int index) {
				return values[index];
			}
		});
		panel.add(list, BorderLayout.CENTER);

		JPanel panel_1 = new JPanel();
		mainWindow.getContentPane().add(panel_1, BorderLayout.SOUTH);
		FlowLayout fl_panel_1 = new FlowLayout(FlowLayout.LEFT, 5, 5);
		fl_panel_1.setAlignOnBaseline(true);
		panel_1.setLayout(fl_panel_1);

		btnNewRoom = new JButton("New room");
		btnNewRoom.setEnabled(false);
		btnNewRoom.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean failed;
				String roomName;
				do {
					failed = false;
					roomName = JOptionPane
							.showInputDialog(
									"Print number of players and name of new room (�:name)",
									"1:New room");
					if (roomName != null)
						try {
							if (roomList != null)
								for (String str : roomList) {
									String roomNameName = roomName.substring(2);
									String strName = str.substring(0,
											str.length() - 6);
									if (roomNameName.equals(strName)) {
										failed = true;
									}
								}
						} catch (Exception e) {
							failed = true;
						}
				} while (failed);
				if (roomName != null) {
					Send("CreateRoom:" + roomName);
					String answer = recv();
					RefreshRoomList();
					if (answer.equals("success")) {
						GetRoom(roomName.substring(2));
					}
				}
			}
		});
		panel_1.add(btnNewRoom);

		btnEnter = new JButton("Enter");
		btnEnter.setEnabled(false);
		btnEnter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int idx = list.getSelectedIndex();
				if (idx >= 0) {
					String name = roomList[idx].substring(0,
							roomList[idx].length() - 6);
					Send("EnterRoom:" + name);
					String answer = recv();
					if (answer.equals("success")) {
						GetRoom(name);
					}
				}
			}
		});
		btnEnter.setAlignmentY(Component.TOP_ALIGNMENT);
		panel_1.add(btnEnter);

		btnSpectate = new JButton("Spectate");
		btnSpectate.setEnabled(false);
		btnSpectate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int idx = list.getSelectedIndex();
				if (idx >= 0) {
					String name = roomList[idx].substring(0,
							roomList[idx].length() - 6);
					Send("SpectateRoom:" + name);
					String answer = recv();
					if (answer.equals("success")) {
						GetRoom(name);
					}
				}
			}
		});
		btnSpectate.setAlignmentY(Component.TOP_ALIGNMENT);
		panel_1.add(btnSpectate);

		btnLeaveRoom = new JButton("Leave room");
		btnLeaveRoom.setEnabled(false);
		btnLeaveRoom.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				LeaveRoom();
			}
		});
		panel_1.add(btnLeaveRoom);

		btnRefresh = new JButton("Refresh list");
		btnRefresh.setEnabled(false);
		btnRefresh.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (socket != null)
					RefreshRoomList();
			}
		});
		panel_1.add(btnRefresh);

		mntmConnectTo = new JMenuItem("Connect to..");
		mntmConnectTo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean failed;
				do {
					failed = false;
					String address = JOptionPane.showInputDialog(
							"Print IP to connect with", "127.0.0.1");
					if (address != null)
						try {
							InetAddress ipAddress = InetAddress
									.getByName(address);
							socket = new Socket(ipAddress, serverPort);

							ss = new ServerSocket(socket.getLocalPort() + 1);
							dataSocket = ss.accept();

							InputStream sin = socket.getInputStream();
							OutputStream sout = socket.getOutputStream();

							in = new DataInputStream(sin);
							out = new DataOutputStream(sout);

							doin = new ObjectInputStream(dataSocket
									.getInputStream());
							din = new DataInputStream(dataSocket
									.getInputStream());
							dout = new DataOutputStream(dataSocket
									.getOutputStream());

							processor = new SocketReader();
							thread = new Thread(processor);
							thread.setDaemon(true);
							thread.start();

							RefreshRoomList();

							mntmDisconnect.setEnabled(true);
							mntmConnectTo.setEnabled(false);

							btnEnter.setEnabled(true);
							btnSpectate.setEnabled(true);
							btnNewRoom.setEnabled(true);
							btnRefresh.setEnabled(true);

						} catch (Exception e) {
							failed = true;
							try {
								if (!socket.isClosed())
									socket.close();
							} catch (Exception e0) {
							}
							try {
								if (!dataSocket.isClosed())
									dataSocket.close();
							} catch (Exception e0) {
							}
							try {
								if (!ss.isClosed())
									ss.close();
							} catch (Exception e0) {
							}
						}
				} while (failed);
			}
		});
		mnFile.add(mntmConnectTo);

		JMenuItem mntmExit = new JMenuItem("Exit");
		mntmExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				System.exit(0);
			}
		});

		mntmDisconnect = new JMenuItem("Disconnect");
		mntmDisconnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Disconnect();
			}
		});
		mntmDisconnect.setEnabled(false);
		mnFile.add(mntmDisconnect);
		mnFile.add(mntmExit);
	}

	private void Disconnect() {
		try {
			if (!socket.isClosed())
				socket.close();
			if (!dataSocket.isClosed())
				dataSocket.close();
			if (!ss.isClosed())
				ss.close();
			mntmDisconnect.setEnabled(false);
			mntmConnectTo.setEnabled(true);
			myRoom = "";
			btnEnter.setEnabled(false);
			btnSpectate.setEnabled(false);
			btnLeaveRoom.setEnabled(false);
			btnNewRoom.setEnabled(false);
			btnRefresh.setEnabled(false);
		} catch (IOException e) {
		}
	}
	
	private class SocketReader implements Runnable {

		@SuppressWarnings("unchecked")
		public void run() {

			while (!dataSocket.isClosed()) {
				try {
					String line = "";
					dataSocket.setSoTimeout(1000);

					do
						line = din.readUTF();
					while (!line.equals("StartGame"));

					board = (int[][]) doin.readObject();

					drawingArea.PaintBoard();
					StartGame();
					while (true){
						gameState = (GameState) doin.readObject();
					}

				} catch (Exception e) {
					gameState = null;
					if (!e.getMessage().equals("Read timed out")) Disconnect();
				}

			}
		}
	}

	public void StartGame() {
		mainWindow.setVisible(false);
		gameWindow.setVisible(true);
		painter = new java.util.Timer();
		painter.schedule(new TimerTask() {
			public void run() {
				drawingArea.PaintState();
			}
		}, 0, 50);
	}
}
