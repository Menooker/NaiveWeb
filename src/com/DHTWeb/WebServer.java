package com.DHTWeb;
import java.net.*; 
import java.io.*; 
import java.util.*;

public class WebServer{ 
   public static void main(String args[])throws Exception { 
   ServerSocket listenSocket = new ServerSocket(6789);
   System.out.println("httpServer running on port " + 
   listenSocket.getLocalPort());  
     while(true) {  
       try {Socket socket = listenSocket.accept();
       Thread thread = new Thread(new RequestHandler(socket));  
       thread.start(); 
	}//启动线程
       catch(Exception e){}
        } 
     } 
} 
class RequestHandler implements Runnable { 
    Socket connectionSocket; 
    OutputStream outToClient; 
    BufferedReader inFormClient;
    String requestMessageLine;
	String fileName;
    // 构造方法 
    public RequestHandler(Socket connectionSocket) throws Exception { 
    this.connectionSocket = connectionSocket; 
     }              
    public void run(){ // 实现Runnable 接口的run()方法
    try { processRequest();} 
    catch(Exception e) { System.out.println(e);} 
     } 
    private void processRequest() throws Exception { 
        //读取并显示Web 浏览器提交的请求信息 
        BufferedReader inFormClient = 
        new BufferedReader( new InputStreamReader( connectionSocket.getInputStream() ) );
        DataOutputStream outToClient = new DataOutputStream(
          	connectionSocket.getOutputStream());
        //读取html请求报文第一行
        requestMessageLine = inFormClient.readLine();
        //解析请求报文文件名
        StringTokenizer tokenizerLine =    //tokenizerLine对象是最初请求行
        new StringTokenizer(requestMessageLine);
            if (tokenizerLine.nextToken().equals("GET")){
          	   fileName = tokenizerLine.nextToken();
            if (fileName.startsWith("/")==true) fileName = fileName.substring(1);
          	File file = new File(fileName);
            int numOfBytes = (int)file.length();
          	FileInputStream inFile = new FileInputStream(fileName);
          	byte[] fileInBytes = new byte[numOfBytes]; 
          	inFile.read(fileInBytes);
            //构造http响应报文
            outToClient.writeBytes("HTTP/1.0 200 Document Follow\r\n");
            if(fileName.endsWith(".jpg"))
          	  outToClient.writeBytes("Content-type:image/jpeg\r\n");
          	if(fileName.endsWith(".gif"))
          		outToClient.writeBytes("Content-type:image/gif\r\n");
                outToClient.writeBytes("Content-type:" + "numOfBytes" + "\r\n");
          	    outToClient.writeBytes("\r\n");
          	    outToClient.write(fileInBytes,0,numOfBytes);
          	    connectionSocket.close();
               }
          	else System.out.println("Bed Request Message");
    } 
}