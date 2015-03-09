import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

public class Main{
  public static void main(String[] args) {	  
        
		String usrfileName = "setting.txt";
		Attributes attr = new Attributes();  
		File usrFile = new File(usrfileName);
		try {
			Scanner readSettings = new Scanner(usrFile);
			String nextLine;
			while(readSettings.hasNextLine())
			{
				nextLine = readSettings.nextLine();
				String[] split = nextLine.split("\\s+");
				if(split[0].charAt(0)=='#')
					continue;
				else
				{
					//Elimiante the name from the nameValuePair
					String value = nextLine.substring(nextLine.indexOf(split[0].charAt(0))+split[0].length());
					attr.putValue(split[0], value.substring(value.indexOf(split[1].charAt(0))));
				}
					
			}		
			readSettings.close();
		} catch (FileNotFoundException e) {
			System.out.println("Missing Setting File \"Setting.txt\"");
			e.printStackTrace();
		}	
        char[][] board= new char[24][81];
        AtomicInteger x = new AtomicInteger();
        AtomicInteger y = new AtomicInteger();
        AtomicBoolean dirty = new AtomicBoolean();
        JFrame demo = new JFrame();
        demo.setSize(1440, 850);
        demo.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabPlacement(JTabbedPane.LEFT);
        JPanel runScriptTab = new JPanel();
        JTextPane bbsView = new JTextPane();
        JTextPane logView = new JTextPane();
        JScrollPane scrollPane = new JScrollPane(logView);
        JEditorPane scriptEditor = new JEditorPane();
        JSplitPane bbsAndLog = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bbsView, scrollPane);        
        runScriptTab.add(BorderLayout.CENTER, scriptEditor);
        runScriptTab.add(BorderLayout.EAST, bbsAndLog);
        tabbedPane.addTab("Edit", new JPanel());
        tabbedPane.setMnemonicAt(0, KeyEvent.VK_1);
        tabbedPane.addTab("Run", runScriptTab);
        tabbedPane.setMnemonicAt(1, KeyEvent.VK_1);
        tabbedPane.setSelectedIndex(1);
        demo.getContentPane().add(BorderLayout.CENTER, tabbedPane);
        demo.setVisible(true);
        bbsAndLog.setPreferredSize(new Dimension(750, 800));
        scriptEditor.setPreferredSize(new Dimension(600, 800));
        scrollPane.setPreferredSize(new Dimension(750, 300));
        bbsView.setMinimumSize(new Dimension(750, 500));        
        try {
        	InetAddress address = InetAddress.getByName(attr.getValue(new Attributes.Name("site")));
            int port = Integer.parseInt(attr.getValue(new Attributes.Name("port")));
            try {
                Socket socket = new Socket(address, port);                
                try {
                	new Thread(new OutputParser(logView, socket, board, x, y, dirty)).start();
                	new Thread(new InputSocket(socket, board, x, y, attr)).start();
                	new Thread(new UIThread(bbsView, board, dirty)).start();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            } catch (IOException e) {
              System.err.println("Connection failed");
            }
        }
        catch(UnknownHostException e) {
          System.err.println("Unknown host");
        }
    }
}

class UIThread implements Runnable {
	private char[][] board;
	private AtomicBoolean dirty;
	private JTextPane bbsView;
	
    private String print(char[] des){
    	StringBuilder sb = new StringBuilder();
    	int current = 0;
    	while(current < des.length)
    	{
    		if(current+1<des.length&&!BIG5TOOLS.isHalfWidth(des[current])&&des[current]==des[current+1])
    		{
    			sb.append(des[current]);
    			current+=2;
    		}
    		else
    		{
    			sb.append(des[current]);
    			current++;
    		}
    	}
    	return sb.toString();
    }
	
    private void printBoard(StyledDocument doc)
    {
    	try {
			doc.remove(0, doc.getLength());
			StringBuilder content = new StringBuilder();
			for(int i=0;i<board.length;i++)
			{
				if(i!=0)
					content.append("\n");
				content.append(String.format("%2d", i+1)+" "+print(board[i]));
			}
			doc.insertString(0, content.toString() ,null);
			dirty.set(false);
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	public UIThread(JTextPane bbsView, char[][] board, AtomicBoolean dirty) throws IOException{
        this.bbsView = bbsView;
		this.board = board;
        this.dirty = dirty;
    }

	@Override
	public void run() {		        
        StyledDocument doc = bbsView.getStyledDocument();                                           
        Font font = new Font("標楷體", Font.PLAIN, 16);       
        bbsView.setFont(font);
        while(true)
        {
        	if(dirty.get())
        	{
        		printBoard(doc);
        	}
        }
	}
}

class OutputParser implements Runnable {
  private enum State {NORMAL, OPTION, CONTROL}
// TELNET OPTIONS defined in http://www.iana.org/assignments/telnet-options/telnet-options.xhtml
  private final int TELNET_OPTION_MIN = 0;
  private final int TELNET_OPTION_MAX = 49;
// TELNET COMMAND defined in http://tools.ietf.org/html/rfc854
  private final int TELNET_COMMAND_SB = 250;  
  private final int TELNET_COMMAND_SE = 240;
  private final int TELNET_COMMAND_IAC = 255;
  private final int CHARACTER_ESCAPE = 27;
  private Socket socket;
  //private BufferedReader socketIn;
  private InputStream socketIn;
  private char[][] board;
  private int nextByte;
  private AtomicInteger currentX, currentY;
  private boolean finishOptionNegotiation = false;
  private boolean parsingControl = false;
  private StringBuilder lineBuffer = new StringBuilder();  
  private State currentState = State.NORMAL;
  private AtomicBoolean dirty;
  final private boolean debug = true;
  private JTextPane logView;
  private StyleContext sc = StyleContext.getDefaultStyleContext();
  private AttributeSet a;
  private StyledDocument doc;

    public OutputParser(JTextPane logView, Socket socket, char[][] board, AtomicInteger x, AtomicInteger y, AtomicBoolean dirty) throws IOException{
        this.logView = logView;
    	this.socket = socket;
        this.board = board;
        this.dirty = dirty;
        currentX = x;
        currentY = y;
        socketIn = socket.getInputStream();
        doc = logView.getStyledDocument();
    }
    // May be not that correct, see
    // http://stackoverflow.com/questions/13559050/how-to-know-half-width-or-full-width-character
    
    private void reset(char[] des)
    {
    	clear(des, 0);
    }
    
    private void clear(char[] des, int start)
    {
    	if(start>=1&&BIG5TOOLS.isHalfWidth(des[start])&&des[start]==des[start-1])
    	{
    		des[start-1] = ' ';
    	} 
    	for(int i=start;i<des.length;i++)
    	{
    		des[i] = ' ';
    	}
    }
    
    private int fill(String source)
    {
    	for(int i=0;i<source.length();i++)
    	{    		
    		if(currentY.get()==80)
			{				
    			currentX.incrementAndGet();
    			currentY.set(0);
    			if(currentX.get()>=24)
    				currentX.set(23);
			}
			if(source.charAt(i)=='\b')
			{				
				currentY.decrementAndGet();
				//board[currentX.get()][currentY.get()] = ' ';
			}
			else if(source.charAt(i)=='\r')
			{
				currentY.set(0);
			}
			else if(source.charAt(i)=='\n')
			{
				currentX.set(currentX.incrementAndGet());
				if(currentX.get()>=23)
					currentX.set(23);
			}
			else
			{
				board[currentX.get()][currentY.get()] = source.charAt(i);
				currentY.incrementAndGet();
			}
    	}
    	return currentY.get();
    }
    

    
    private void printDebug(String s, Color c)
    {
    	a = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
    	try {
			doc.insertString(doc.getLength(), s.replaceAll("\r","<CR>").replaceAll("\n","<LF>").replaceAll("\b","<BS>").replaceAll("","*").replaceAll(" ", "<SP>"), a);
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private boolean omitColorCode(int start)
    {
    	if(Pattern.matches("\\[[\\d;]*?m", lineBuffer.subSequence(start, lineBuffer.length())))
    	{
    		lineBuffer.delete(start, lineBuffer.length());
    		return true;
    	}
    	return false;
    }    
    
    private boolean completeBIG5Character()
    {
    	 
    	if(!BIG5TOOLS.isBIG5FirstByte(nextByte))
    	{    		
    		//Illegal nextByte
    		return false;
    	}
    	else
    	{
    		try {
    			int start = lineBuffer.length();
    			byte[] byteArray = new byte[2];
    			byteArray[0] = (byte) nextByte;    			
    			nextByte = (byte) socketIn.read();
    			if(nextByte == CHARACTER_ESCAPE)
    			{
    				lineBuffer.append((char)nextByte);
	    			while((nextByte = socketIn.read()) != 'm')
	    			{
	    				lineBuffer.append((char)nextByte);
	    			}
	    			//consume 'm'
	    			lineBuffer.append((char)nextByte);
	    			omitColorCode(start);
	    			//read next byte
	    			nextByte = socketIn.read();
    			}
    			byteArray[1] = (byte) nextByte;
    			for(int i=0;i<2;i++)
    				lineBuffer.append(new String(byteArray, "BIG5"));
    			return true;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
    	}
    	
    }
    

    
    private void consumeControl()
    {    	
    	String cmdThis = lineBuffer.toString();
    	int start = 0;
		int end = 0;
		Matcher matchm = Pattern.compile("?\\[[\\d;]*?m").matcher(cmdThis);
		Matcher matchK = Pattern.compile("\\[[012]??K").matcher(cmdThis);
		Matcher matchH = Pattern.compile("\\[[\\d;]*?H").matcher(cmdThis);
		Matcher matchJ = Pattern.compile("\\[[012]+J").matcher(cmdThis);
		if(matchm.find())
		{
			start = matchm.start();
			end = matchm.end();            			
			lineBuffer.delete(start, end);
		}
		else if(matchK.find())
		{
			start = matchK.start();
			end = matchK.end();
			if(cmdThis.substring(0, end).equals("[K"))
			{
				clear(board[currentX.get()], currentY.get());
				lineBuffer.delete(start, end);
				//board[currentX].replace(currentY, board[currentX].length());
				//currentX++;
				//currentY = 0;
			}
		}		
		else if(matchH.find())
		{            			
			start = matchH.start();
			end = matchH.end();
			if(cmdThis.substring(0, end).equals("[H")||cmdThis.substring(0, end).equals("[;H"))
			{
				currentX.set(0);
				currentY.set(0);
				lineBuffer.delete(start, end);
			}
			else if(cmdThis.substring(0, end).contains(";"))
			{
				String[] split = cmdThis.substring(0, end).split(";");
				int x = Integer.parseInt(split[0].replaceAll("\\D", ""))-1;
				int y = Integer.parseInt(split[1].replaceAll("\\D", ""))-1;
				/*if(y>=1&&!BIG5TOOLS.isHalfWidth(board[x][y])&&board[x][y]==board[x][y-1])
		    	{
		    		y--;
		    	}*/
				currentX.set(x);
				currentY.set(y);
				lineBuffer.delete(start, end);
			}
		}		
		else if(matchJ.find())
		{            			
			start = matchJ.start();
			end = matchJ.end();
			if(cmdThis.substring(0, end).equals("[J")||cmdThis.substring(0, end).equals("[0J"))
			{
				for(int i=currentX.get();i<board.length;i++)
				{
					reset(board[i]);
				}
			}
			if(cmdThis.substring(0, end).equals("[2J"))
			{
				for(int i=0;i<board.length;i++)
				{
					reset(board[i]);
				}
			}
			lineBuffer.delete(start, end);
		}
    	lineBuffer.delete(0, lineBuffer.length());
    	parsingControl = false;
    }
    
    private int parseContentAndControl(boolean waitForSE)
    {
    	try {
			do
			{
				if(BIG5TOOLS.isBIG5FirstByte(nextByte))
        		{
					completeBIG5Character();
        		}
				else
				{	
					lineBuffer.append((char)nextByte);						
				}
				switch(currentState)
				{
					case NORMAL:
						if(nextByte==CHARACTER_ESCAPE)
						{
							currentState = State.CONTROL;
							return 0;
						}
						else if(nextByte==TELNET_COMMAND_IAC)
						{
							currentState = State.OPTION;
							return 0;
						}
						else
						{
							int length = lineBuffer.length();
							fill(lineBuffer.toString());
							if(debug)
							{
								printDebug(lineBuffer.toString(), Color.black);
							}
							lineBuffer.delete(0, lineBuffer.length());							
							return length;
						}
					case OPTION:
						if(waitForSE)
						{
							if(nextByte==TELNET_COMMAND_SE)
							{
								if(debug)
								{
									printDebug(lineBuffer.toString(), Color.orange);
								}
								lineBuffer.delete(0, lineBuffer.length());
								currentState = State.NORMAL;
								waitForSE = false;
							}
							else
							{
								//Do Nothing, continue to parse
							}
						}
						else
						{
							if(nextByte==TELNET_COMMAND_SB)
							{
								waitForSE = true;
							}
							else
							{
								if(debug)
								{
									printDebug(lineBuffer.toString(), Color.orange);
								}
								lineBuffer.delete(0, lineBuffer.length());
								currentState = State.NORMAL;								
							}
						}
						return 0;
					case CONTROL:
						if (nextByte=='J'||nextByte=='K'||nextByte=='m'||nextByte=='H')
						{
							if(debug)
							{
								if(nextByte=='m')
									printDebug(lineBuffer.toString(), Color.red);
								else
									printDebug(lineBuffer.toString(), Color.blue);
							}
							consumeControl();
							currentState = State.NORMAL;
						}
						return 0;
				}
			}
			while((nextByte = socketIn.read()) != -1);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return 0;
    	
    }
  @Override
    public void run() {	  
      try {    	                   
          boolean waitForSE = false;
          while((nextByte = socketIn.read()) != -1)
          {
        	//System.out.print("<"+nextByte+">"+(char)nextByte);
    		if(parseContentAndControl(waitForSE)>0)
    		{
    			dirty.set(true);
    		}
        }        
        socket.close();        
        } catch(IOException e) {
            System.out.println(e.toString());
        }
    }
}

class InputSocket implements Runnable {
  private Socket socket;
  private PrintStream socketOut;
  private BufferedReader in;
  private char[][] board;
  private AtomicInteger x, y;
  private Attributes attr;

    public InputSocket(Socket socket, char[][] board, AtomicInteger x, AtomicInteger y, Attributes attr) throws IOException, InterruptedException{
        this.socket = socket;
        this.board = board;
        this.x = x;
        this.y = y;
        this.attr = attr;
      socketOut = new PrintStream(socket.getOutputStream());
      in = new BufferedReader(new InputStreamReader(System.in));
    }

  private void input(String s)
  {
	  try {
	  for(int i=0;i<s.length();i++)
	  {
		  char next = s.charAt(i);
		  socketOut.print(next);		  
		  //Thread.sleep(1000);
		  if(next=='\r')
		  {			  
			  x.set(x.incrementAndGet());
			  y.set(0);
			  if(x.get()>=23)
				  x.set(23);
		  }
	  }		  
		//socketOut.print(s);  
		System.out.println(s);
		Thread.sleep(1000);
	} catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }
    
  @Override
    public void run(){	  
	  while(socket.isClosed())
	  {}
	  try {
		Thread.sleep(8000);
		input(attr.getValue(new Attributes.Name("Id"))+'\r');
		input(attr.getValue(new Attributes.Name("Pwd"))+'\r');	
		input('\r'+"");		 	
		input("s");
		input("Gossiping\r");
		input(" ");
		input("1\r\rG");
		while(true)
		{
			input("gG");
			Thread.sleep(2000);
		}
	} catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}	  
    }
}