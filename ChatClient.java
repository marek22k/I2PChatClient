import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.NoRouteToHostException;

import net.i2p.I2PException;
import net.i2p.client.streaming.I2PSocket;
import net.i2p.client.streaming.I2PServerSocket;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.I2PSocketManagerFactory;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import java.net.SocketTimeoutException;
import net.i2p.util.I2PThread;
import net.i2p.client.I2PSession;



public class ChatClient extends JFrame
{
	private I2PSocket socket;

	private BufferedWriter bw;
	private BufferedReader br;
	
	private ChatServer server;
	private static final String name = "Simple I2P Chat";
	private String nick;
	private boolean isConnected, isServer;
	private Thread listenThread;
	private JTabbedPane tabs;
	private JPanel cPanel, sPanel, sbPanel, bPanel, tPanel;
	private JLabel nickLabel;
	private JScrollPane sp, stsp;
	private JTextArea ta, st;
	private JTextField tf;
	private JButton connectButton, sendButton, serverButton, saveLogButton;
	
	public static void main(String[] args)
	{	new ChatClient(name);	}
	
	public ChatClient(String title)
	{
		super(title);
		
		isConnected = false;
		isServer = false;
		
		tabs = new JTabbedPane();
		cPanel = new JPanel();
		sPanel = new JPanel();
		sbPanel = new JPanel();
		bPanel = new JPanel();
		tPanel = new JPanel();
		nickLabel = new JLabel();
		st = new JTextArea(); /* server log area */
		st.setEditable(false);
		stsp = new JScrollPane(st);
		ta = new JTextArea(); /* text area with all messages */
		ta.setEditable(false);
		sp = new JScrollPane(ta);

		tf = new JTextField(); /* message input field */
		connectButton = new JButton("Connect!");
		sendButton = new JButton("Send");
		serverButton = new JButton("Start Server!");
		saveLogButton = new JButton("Save Server Log");
		
		tf.addActionListener( new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{	writeLine();	}
		} );
		connectButton.addActionListener( new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if(!isConnected)
					connect();
			}
		} );
		sendButton.addActionListener( new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{	writeLine();	}
		} );
		serverButton.addActionListener( new ServerButtonListener() );
		saveLogButton.addActionListener( new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String text = st.getText();
				if(!text.equals(""))
					saveLog(text);
			}
		} );
		
		bPanel.add(sendButton);
		bPanel.add(connectButton);
		tPanel.setLayout(new BorderLayout());
		tPanel.add(nickLabel, BorderLayout.NORTH);
		tPanel.add(sp, BorderLayout.CENTER);
		tPanel.add(tf, BorderLayout.SOUTH);
		cPanel.setLayout(new BorderLayout());
		cPanel.add(bPanel, BorderLayout.SOUTH);
		cPanel.add(tPanel, BorderLayout.CENTER);
		sbPanel.add(serverButton);
		sbPanel.add(saveLogButton);
		sPanel.setLayout(new BorderLayout());
		sPanel.add(sbPanel, BorderLayout.NORTH);
		sPanel.add(stsp, BorderLayout.CENTER);
		
		tabs.addTab("Client", null, cPanel, "Client Tab");
		tabs.addTab("Server", null, sPanel, "Server Tab");
		add(tabs);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(420,420);
		setVisible(true);
	}
	
	private void connect()
	{
		I2PSocketManager manager = I2PSocketManagerFactory.createManager();
		String destStr = input("Please Enter a Destination:");
		if(destStr == null || destStr.trim().equals(""))
			return;
		Destination dest = null;
		try
		{	dest = new Destination(destStr);	}
		catch (DataFormatException ex)
		{
			err("Destination string incorrectly formatted.");
			return;
		}
		socket = null;
		try
		{	socket = manager.connect(dest);	}
		catch (I2PException ex)
		{	err("General I2P exception occurred!");	}
		catch (ConnectException ex)
		{	err("Failed to connect!");	}
		catch (NoRouteToHostException ex)
		{	err("Couldn't find host!");	}
		catch (InterruptedIOException ex)
		{	err("Sending/receiving was interrupted!");	}
		try
		{
			//Read from server
			br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			//Write to server
			bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
		}
		catch (IOException ex)
		{	err("Error occurred while getting streams!");	}
		
		isConnected = true;
		message("Connected!");
		while( !goodNick() )
			nick = input("Please enter your nickname:");
		nick = nick.trim();
		nickLabel.setText( String.format("Nick: %s", nick) );
		
		listenThread = new Thread( new Runnable()
		{
			public void run()
			{
				while(isConnected)
					readLine();
			}
		} );
		listenThread.start();
	}

	private void closeConn()
	{
		try
		{	socket.close();	}
		catch (IOException ex)
		{	err("Error occurred while closing I2PSocket!");	}
		nick = null;
		nickLabel.setText("");
		isConnected = false;
	}
	
	private void writeLine()
	{
		String line = tf.getText();
		tf.setText("");
		if(line == null || line.trim().equals("") || !isConnected) return;
		line = String.format("%s: %s%n", nick, line);
		try
		{
			bw.write(line);
			bw.flush();
		}
		catch(IOException ex)
		{	err( String.format("Error occurred during send.%nClosing connection.") );
			closeConn();	}
	}
	
	private void readLine()
	{
		try
		{
			String s = null;
			while ((s = br.readLine()) != null)
			{
				ta.append( String.format("%s%n",s) );
                ta.setCaretPosition(ta.getDocument().getLength());
		    }
		}
		catch(IOException ex)
		{	err( String.format("Error occurred during read.%nClosing connection.") );
			closeConn();	}
	}
	
	private boolean goodNick()
	{
		if(nick == null || nick.trim().equals(""))
			return false;
		return nick.matches("\\w+");
	}
	
	private void saveLog(String log)
	{
		String fn, ext="";
		
		JFileChooser jfc = new JFileChooser();
		jfc.addChoosableFileFilter( new FileNameExtensionFilter("Text files only.","txt") );
		int result = jfc.showSaveDialog(this);
		if(result == JFileChooser.CANCEL_OPTION)
			return;
		File logFile = jfc.getSelectedFile();
		fn = logFile.getName();
		int i = fn.lastIndexOf(".");
		if(i>=0)
			ext = fn.substring(i);
		if(!ext.equals(".txt"))
			fn += ".txt";
		logFile = new File(logFile.getParent(),fn);
		
		try
		{
			BufferedWriter out = new BufferedWriter(
											new OutputStreamWriter(
												new FileOutputStream( logFile )));
			out.write(log);
			out.close();
		}
		catch(FileNotFoundException fnfe)
		{	err("Cannot write to disk!");	}
		catch(IOException ioe)
		{	err("Error occured while saving!");	}
	}
	
	private void message(Object obj)
	{	JOptionPane.showMessageDialog(this,obj,name,1);	}
	private String input(String str)
	{	return JOptionPane.showInputDialog(this,str,name,3);	}
	private void err(String str)
	{	JOptionPane.showMessageDialog(this,str,name,0);	}

	
	class ServerButtonListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			if(isServer) return;
			try
			{	server = new ChatServer(st);	}
			catch(NullPointerException ex)
			{
				err("I2P Router may not be running.");
				return;
			}
			isServer = true;
			serverButton.setText("Server Running.");
			
			JTextArea ta = new JTextArea(1, 23);
			ta.setText( server.getDest() );
			JScrollPane sp = new JScrollPane(ta);
			message(sp); // Print the base64 string
		}	
	}
	

}



class ChatServer
{
	private ArrayList<I2PSocket> clients;
	private I2PSocket currSock;
	private I2PSocketManager manager;
	private I2PServerSocket serverSocket;
	private I2PSession session;
	private String dest;
	private JTextArea log;
	private ExecutorService threadExecutor;
	
	public ChatServer(JTextArea log)
	{
		threadExecutor = Executors.newCachedThreadPool();	
		clients = new ArrayList<I2PSocket>();
		
		manager = I2PSocketManagerFactory.createManager();
		serverSocket = manager.getServerSocket();
		session = manager.getSession();
		dest = session.getMyDestination().toBase64();
		this.log = log;
		
		log("Server: Starting up.");
		I2PThread t = new I2PThread(new ClientHandler());
		t.setName("clienthandler1");
		t.setDaemon(false);
		t.start();
	}
	
	private void post(String str)
	{
		Iterator<I2PSocket> it = clients.iterator();
		while(it.hasNext())		
		{
			I2PSocket sock = (I2PSocket)it.next();
			if(sock == null)
				continue;
			try
			{
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream())); //Send to clients
				bw.write( String.format("%s%n",str) );
				bw.flush(); // Flush to make sure everything got sent
			}
			catch(IOException ex)
			{	err("Error occurred while posting!");	}
		}
	}
	
	private void closeConns()
	{
		Iterator<I2PSocket> it = clients.iterator();
		while(it.hasNext())
		{
			I2PSocket sock = (I2PSocket) it.next();
			if(sock == null)
				continue;
			try{	sock.close();	}
			catch(IOException ex)
			{	err("Error occurred while closing connections!");	}
		}
	}
	
	private void err(String str)
	{
		log(str);
		log("Server disconnecting from clients...");
		closeConns();
	}
	
	public String getDest()
	{	return dest;	}
	
	private void log(String str)
	{	log.append( String.format("%s%n",str) );	}


	class ClientHandler implements Runnable
	{
		public void run()
		{
			while(true)
			{
				try
				{
					currSock = serverSocket.accept();
					if(currSock != null)
					{
						log("Server: Successfully accepted connection.");
						clients.add(currSock);
						startListenThread(currSock);
					}
				}
				catch (I2PException ex)
				{	log("General I2P exception!");	}
				catch (ConnectException ex)
				{	log("Error connecting!");	}
				catch (SocketTimeoutException ex)
				{	log("Timeout!");	}
			}
		}

		private void startListenThread(I2PSocket sock)
		{	threadExecutor.execute( new ListenThread(sock) );	}
		
		class ListenThread implements Runnable
		{
			I2PSocket sock;
			public ListenThread(I2PSocket sock)
			{	this.sock = sock;	}
			
			public void run()
			{
				try
				{
					BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream())); // Receive from clients
					String line = null;
					while( true )
					{
						if( (line = br.readLine()) == null ) continue;
						log("Recieved from client "+line);
						post(line);
					}
				}
				catch (IOException ex)
				{	err("General read/write-exception!");	}
			}
		} // end inner class ListenThread


	} // end inner class ClientHandler


} // end ChatServer

