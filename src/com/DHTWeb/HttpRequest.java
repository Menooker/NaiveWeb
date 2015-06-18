package com.DHTWeb;


import java.io.* ;
import java.net.* ;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.DHTWeb.PeerManager.NotMasterNodeException;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import net.tomp2p.storage.Data;



public final class HttpRequest  implements Runnable {
	final static String CRLF = "\r\n";
	static Number160 peer2Owner ;
	String[] path;
	Socket socket;
	PeerManager pm;
	static Number160 id_data;
	static Number160 id_threads;//fix-me : should not be static( in case of multithread cases)
	static Number160 id_thread_data;
	static Number160 id_page_data;
	static String main1,main2,page1,page2;
	
	
	static Random rnd=new Random();
	
	
	public static class PeerRequest implements Serializable
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = -1230610231931185223L;
		enum REQ
		{
			POST_THREAD,
			POST_REPLY
		};
		REQ req;
		Number160 id;
		String str1,str2,str3;	
		PeerRequest(REQ req,Number160 id,String str1,String str2,String str3)
		{
			this.req=req;
			this.id=id;
			this.str1=str1;
			this.str2=str2;
			this.str3=str3;
		}
		
	}
	
	static class Putter implements Runnable
	{
		PeerManager peer;
		PeerRequest req;
		Putter(PeerManager peer,PeerRequest req)
		{
			this.peer=peer;
			this.req=req;
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("PUTTING");
			switch (req.req)
			{
			case POST_THREAD:
				if(req.str2.equals("007"))
					return ;
				Number160 tid=Number160.createHash(rnd.nextLong());
				try {
					peer.createdir(id_thread_data, tid, tid);
					peer.putdir(id_threads, tid,new ThreadItem(req.str1,req.str2));
					peer.putdir(tid, Number160.ZERO, new ReplyItem(req.str3,req.str2,new Date()));	
				} catch (InvalidKeyException | SignatureException | IOException
						| NotMasterNodeException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		
				break;
			case POST_REPLY:
				Number160 tid1=req.id;
				Number160 rid=Number160.createHash(rnd.nextLong());
				try {
					peer.putdir(tid1, rid, new ReplyItem(req.str1,req.str2,new Date()));
				} catch (InvalidKeyException | SignatureException | IOException
						| NotMasterNodeException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}			
				break;
			}	
		}
		
	}
	public static class ReplyListener implements ObjectDataReply
	{
		PeerManager peer;
		@Override
		public Object reply(PeerAddress arg0, Object arg1) throws Exception {
			PeerRequest req=(PeerRequest)arg1;
			Putter p=new Putter(peer,req);
			Thread thread=new Thread(p);
			thread.start();
			System.out.println("RET OK");
			return null;
		}
		
	}
	
	
	public static class ThreadItem implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 3726088328990171437L;
		String title;
		String user;
		ThreadItem(String t,String u)
		{
			title=t;user=u;
		}
	}
	
	public static class ReplyItem implements Serializable
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = -9160793966815370818L;
		String content;
		String user;
		Date date;
		ReplyItem(String c,String u,Date d)
		{
			content=c;user=u;date=d;
		}
	}
	
	static void init_id(PeerManager pm) throws ClassNotFoundException, IOException
	{
		if(id_data==null)		id_data=(Number160)pm.getdir(PeerManager.ROOT,"data");
		if(id_thread_data==null)		id_thread_data=(Number160)pm.getdir(id_data,"thread_data");
		if(id_threads==null)		id_threads=(Number160)pm.getdir(id_data, "threads");
		if(id_page_data==null)		id_page_data=(Number160)pm.getdir(id_data, "pages");
		
		try
		{
			
			if(main1==null)	main1=(String)pm.getdir(id_page_data, "main1");
			if(main2==null)	main2=(String)pm.getdir(id_page_data, "main2");
			if(page1==null)	page1=(String)pm.getdir(id_page_data, "page1");
			if(page2==null)	page2=(String)pm.getdir(id_page_data, "page2");
		}
		catch(Exception e)
		{
			
		}
	}
	
	
	static void init_fs(PeerManager pm) throws InvalidKeyException, SignatureException, IOException, NotMasterNodeException, ClassNotFoundException
	{
		Number160 id_page=Number160.createHash("PAGES_DATA");
		Number160 id_data=Number160.createHash("DIR_DATA");
		Number160 id_css=Number160.createHash("CSS_DATA");
    	pm.createrootdir("data",Number160.createHash("DIR_DATA"));
    	pm.createrootdir("img",Number160.createHash("DIR_IMG"));
    	pm.createdir(id_data, "threads",Number160.createHash("THREADS"));
    	pm.createdir(id_data, "thread_data",Number160.createHash("thread_data_data"));
    	pm.createdir(id_data, "pages",id_page);
    	pm.createdir(id_data, "css",id_css);
    	//pm.putdir(pm.dirid("data/testhtml"), "testhtml", "<html><body><h1>Hello World</h1><br>This is our DHTWeb homepage<br><img src=../data/testjpeg /></body></html>") ;
        pm.putfile(id_page, "main1", "main1.txt");
        pm.putfile(id_page, "main2", "main2.txt");
        pm.putfile(id_page, "page1", "page1.txt");
        pm.putfile(id_page, "page2", "page2.txt");
        pm.putfile(id_css, "style.css", "style.css");
	}
	
	public HttpRequest(Socket socket,PeerManager pm) throws Exception 
	{
		this.socket = socket;
		this.pm = pm;
	}
	// Implement the run() method of the Runnable interface.
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			processRequest();
		} catch (Exception e) {
			System.out.println(e);
		}
		
	}
	
	HashMap<String,String> parsepost(BufferedReader br) throws IOException
	{
		char[] buf=new char[100];
		br.read(buf);
		System.out.println(new String(buf));
		String[] post=new String(buf).split("=|&");
		HashMap<String,String> map=new HashMap<String,String>();
		if(post.length%2==0)
		{
			for(int i=0;i<post.length/2;i++)
			{
				map.put(post[2*i],post[2*i+1]);
			}
		}
		return map;
	}

	
	private void processRequest() throws Exception
	{
		// Get a reference to the socket's input and output streams.
		InputStream is       = socket.getInputStream();
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());
		String entityBody = null;
		
		// Set up input stream filters.
		FilterInputStream fis;	
		BufferedReader br =new BufferedReader(new InputStreamReader(is));
		// Get the request line of the HTTP request message.
		String requestLine = br.readLine();
		
		// Display the request line.
		System.out.println();
		System.out.println(requestLine);

		
		// Get and display the header lines.
		String headerLine = null;
		while ((headerLine = br.readLine()).length() != 0) {
			//System.out.println(headerLine);
		}
	
		// Extract the filename from the request line.
		boolean fileExists = true;
		Object respondobj = null;
		StringTokenizer tokens = new StringTokenizer(requestLine);
		boolean isPost=false;
		if(tokens.nextToken().equals("POST"))
		{
			isPost=true;
		}

		// tokens.nextToken(); // skip over the method, which should be "GET"
		String fileName = tokens.nextToken();
		// Open the requested file.

		FileInputStream fis1 = null;

		try {
			if (fileName.equals("/") || fileName.equals("/index")) {
				
				if(isPost)
				{
					HashMap<String,String>map=parsepost(br);
					if(pm.MasterNode())
					{
						System.out.println(map.get("title"));
						System.out.println(map.get("username"));
						Number160 tid=Number160.createHash(rnd.nextLong());
						pm.createdir(id_thread_data, tid, tid);
						pm.putdir(id_threads, tid,new ThreadItem(map.get("title"),map.get("username")));
						pm.putdir(tid, Number160.ZERO, new ReplyItem(map.get("content"),map.get("username"),new Date()));
					}	
					else
					{
						pm.mastercall(new PeerRequest(PeerRequest.REQ.POST_THREAD,null,map.get("title"),map.get("username"),map.get("content")));
					}
				}

				
				StringBuilder sb = new StringBuilder(main1);
				for (Entry<Number640, Data> entry : pm.readdir(id_threads).m
						.entrySet()) {
					// System.out.print(entry.getKey().contentKey() + "--->");
					Object obj = entry.getValue().object();
					if (obj.getClass() == Number160.class)
						continue;
					ThreadItem itm=(ThreadItem)obj;
					String title = itm.title;
					String user = itm.user;
					sb.append(String.format(
							"<a href= ../threads/%s>%s</a><timestamp> %s</timestamp><br>\n", entry
									.getKey().contentKey().toString(), title,user));
				
				}
				sb.append(main2);
				respondobj = sb.toString();
				// respondobj="<html><body><h1>Hello World</h1><br>This is our DHTWeb homepage<br><img src=../data/testjpeg /><a href=\"http://baidu.com\">Baidu</a></body></html>";
			} else if(fileName.startsWith("/threads/"))
			{
				String id=fileName.substring(9);
				System.out.println(id);
				Number160 tid=new Number160(id);
				
				if(isPost)
				{
					HashMap<String,String>map=parsepost(br);
					if(pm.MasterNode())
					{
						Number160 rid=Number160.createHash(rnd.nextLong());
						pm.putdir(tid, rid, new ReplyItem(map.get("content"),map.get("username"),new Date()));
					}	
					else
					{
						pm.mastercall(new PeerRequest(PeerRequest.REQ.POST_REPLY,tid,map.get("content"),map.get("username"),null));
					}
				}
				
				
				StringBuilder sb = new StringBuilder(page1);

				ThreadItem itm=(ThreadItem)pm.getdir(id_threads,tid );
				String title = itm.title;
				String user = itm.user;
				
				sb.append(String.format("<usrdiv> %s</usrdiv><timestamp> %s</timestamp><br><br>\n", title,user));
				int i=0;
				TreeMap<Date, ReplyItem> sortmap = new TreeMap<Date, ReplyItem>(
						new Comparator<Date>() {
							public int compare(Date obj1, Date obj2) {
								return -obj2.compareTo(obj1);
							}
						});
				for (Entry<Number640, Data> entry : pm.readdir(tid).m
						.entrySet()) {
					Object obj = entry.getValue().object();
					if (obj.getClass() == Number160.class)
						continue;
					ReplyItem ritm=(ReplyItem)obj;
					sortmap.put(ritm.date, ritm);
				}
				
				for (Entry<Date, ReplyItem> entry : sortmap.entrySet()) 
				{
					i++;
					ReplyItem ritm=entry.getValue();
					sb.append(String.format(
							"<usrdiv>#%d:%s</usrdiv><timestamp> %s</timestamp><div id=\"bbscontent\">%s</div><br>\n",i,ritm.user,ritm.date.toString(), ritm.content));
				}
				sb.append(String.format(page2, id));
				respondobj = sb.toString();
				
				
			}
			else
			{
				path = fileName.split("/");
				Number160 id = (Number160) pm.getdir(PeerManager.ROOT, path[1]);
				for (int i = 2; i < path.length - 1; i++) {
					id = (Number160) pm.getdir(id, path[i]);
				}
				System.out.println(id);
				respondobj = pm.getdir(id, path[path.length - 1]);
				System.out.println("111");
			}
		} catch (Exception e) {
			e.printStackTrace();
			fileExists = false;
		}

		// Construct the response message.
		String statusLine = null;
		String contentTypeLine = null;
		String contentLengthLine = null;

		if (fileExists) {
			statusLine = "HTTP/1.0 200 OK" + CRLF;
			contentTypeLine = "Content-Type: " + contentType(fileName) + CRLF;
			// entityBody = (String)pm.getdir(id, path[path.length-1]);
			// contentLengthLine =Integer.toString(fis1.available()) + CRLF;
		} else {
			statusLine = "file not found\n";
			contentTypeLine = "no contents\n";
			entityBody = "<HTML>" + "<HEAD><TITLE>Not Found</TITLE></HEAD>"
					+ "<BODY>Not Found</BODY></HTML>";
			contentLengthLine = new Integer(entityBody.length()).toString();
			// Send the status line.
			// os.writeBytes(statusLine);

			// Send the headers.
			// os.writeBytes(contentTypeLine);

			// Send a blank line to indicate the end of the header lines.
			// os.writeBytes(CRLF);
		}
		
		// Send the entity body.
		if (fileExists) {
			// os.writeBytes(entityBody);
			SendBytes2(respondobj, os);
			// fis1.close();
		} else {
			os.writeBytes(entityBody);
		}
		
		
		os.close();
		br.close();
		socket.close();
	}
	private static void SendBytes2(Object resobj,OutputStream os) throws IOException{
		byte[] bytes=null;
		if(resobj.getClass()==String.class){
			bytes = ((String)resobj).getBytes();
		}
		else{
			bytes = (byte[])resobj;
		}

//        ByteArrayOutputStream bos = new ByteArrayOutputStream();      
//        try {        
//            ObjectOutputStream oos = new ObjectOutputStream(bos);         
//            oos.writeObject(resobj);        
//            oos.flush();         
//            bytes = bos.toByteArray ();      
//            oos.close();         
//            bos.close();        
//        } catch (IOException ex) {        
//            ex.printStackTrace();   
//        }
        os.write(bytes);
	}
	private static void sendBytes(FileInputStream fis, OutputStream os) 
			throws Exception
			{
			   // Construct a 1K buffer to hold bytes on their way to the socket.
			   byte[] buffer = new byte[1024];
			   int bytes = 0;

			   // Copy requested file into the socket's output stream.
			   while((bytes = fis.read(buffer)) != -1 ) {
			      os.write(buffer, 0, bytes);
			   }
			}
	
	private static String contentType(String fileName)
	{
		if(fileName.endsWith(".htm") || fileName.endsWith(".html")) {
			return "text/html";
		}
		if(fileName.endsWith(".gif") || fileName.endsWith(".GIF"))
		{
			return "image/gif";
		}
		if(fileName.endsWith(".jpeg")|| fileName.endsWith(".JPEG"))
		{
			return "image/jpeg";
		}
		return "application/octet-stream";
	}
}
