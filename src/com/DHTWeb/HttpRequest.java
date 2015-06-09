package com.DHTWeb;


import java.io.* ;
import java.net.* ;
import java.util.StringTokenizer;

import net.tomp2p.peers.Number160;



public final class HttpRequest  implements Runnable {
	final static String CRLF = "\r\n";
	static Number160 peer2Owner ;
	String[] path;
	Socket socket;
	PeerManager pm;
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
//			System.out.println(headerLine);
		}
		// Extract the filename from the request line.
		StringTokenizer tokens = new StringTokenizer(requestLine);
		tokens.nextToken();  // skip over the method, which should be "GET"
		String fileName = tokens.nextToken();
		System.out.println("ddddd"+ fileName) ;
		// Open the requested file.
		
		FileInputStream fis1 = null;
		boolean fileExists = true;
		Object respondobj = null;
		try {
			path= fileName.split("/");
    		Number160 id=(Number160) pm.getdir(PeerManager.ROOT, path[1]);
    		for (int i=2;i<path.length-1;i++)
    		{
    			id=(Number160)pm.getdir(id,path[i]);	
    		}
    		System.out.println(id) ;
    		respondobj=pm.getdir(id, path[path.length-1]);
    		System.out.println("111") ;
		} catch(Exception e){
			fileExists = false;
		}
		
		// Construct the response message.
		String statusLine = null;
		String contentTypeLine = null;
		String contentLengthLine = null;

		if (fileExists) {
			statusLine ="HTTP/1.0 200 OK" + CRLF;
			contentTypeLine = "Content-Type: " + 
				contentType( fileName ) + CRLF;
//			entityBody = (String)pm.getdir(id, path[path.length-1]);
//			contentLengthLine =Integer.toString(fis1.available())  + CRLF;
		} else {
			statusLine = "file not found\n";
			contentTypeLine = "no contents\n";
			entityBody = "<HTML>" + 
					"<HEAD><TITLE>Not Found</TITLE></HEAD>" +
					"<BODY>Not Found</BODY></HTML>";
			contentLengthLine = new Integer(entityBody.length()).toString();
			// Send the status line.
			//os.writeBytes(statusLine);

			// Send the headers.
			//os.writeBytes(contentTypeLine);

			// Send a blank line to indicate the end of the header lines.
			//os.writeBytes(CRLF);
		}
			// Send the entity body.
			if (fileExists)	{
//				os.writeBytes(entityBody);
				SendBytes2(respondobj, os);
//				fis1.close();
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
