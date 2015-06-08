package com.DHTWeb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.Key;
import java.util.Map.Entry;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;

public class FileSystemShell {
	static String getLine() {
		System.out.print(">");
		InputStreamReader converter = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(converter);
		String inLine = "";
		try {
			inLine = in.readLine();
		} catch (Exception e) {
			System.err.println("Error reading input.");
			e.printStackTrace();
			System.exit(1);
		}
		return inLine;
	}

    public static void main(String[] args) throws NumberFormatException, Exception {
    	PeerManager pm = null;
    	
        if (args[0].equals("-n")) { //-s name ip key
        	pm=new PeerManager();
        	PeerManager.WriteKey(pm.getMasterKey(), "master_");
        	PeerManager.WriteKey(pm.getRootKey(), "root_");
        	
        	pm.createrootdir("data",Number160.createHash("DIR_DATA"));
        	pm.createrootdir("img",Number160.createHash("DIR_IMG"));
        }
        if (args[0].equals("-c")) {
        	pm=new PeerManager(args[1]);
            //peer.shutdown();
        }
        if(args[0].equals("-w")) //-w host name ip key
        {
        	pm=new PeerManager(args[1],PeerManager.ReadKey("master_"));
        }
        if(args[0].equals("-s"))
        {
        	pm=new PeerManager(PeerManager.ReadKey("master_"),
        			PeerManager.ReadKey("root_"));     	
        }
        loop(pm);
    }
    
	static void loop(PeerManager pm) {
		while (pm != null) {
			try {
				String cmd = getLine();
				String[] argss = cmd.split(" ");
				String[] path;
				if (cmd.equals("stat")) {
					//for (;;) {
						System.out.println("Ser--------");
						for (PeerAddress pa : pm.peer().peerBean().peerMap()
								.all()) {
							System.out.println("peer online (TCP):" + pa);
						}
					//	Thread.sleep(2000);
					//}
				} else if (argss[0].equals("mkdir")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					pm.createdir(id, path[path.length - 1],
							Number160.createHash(path[path.length - 1]));
				} else if (argss[0].equals("mkrdir")) {
					pm.createrootdir(argss[1], Number160.createHash(argss[1]));
				} else if (argss[0].equals("ls")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					for (Entry<Number640, Data> entry : pm.readdir(id).m
							.entrySet()) {
						System.out.print(entry.getKey().contentKey() + "--->");
						if (entry.getValue().object().getClass() == String.class) {
							System.out.println((String) entry.getValue()
									.object());
						} else if (entry.getValue().object().getClass() == Number160.class) {
							System.out.println((Number160) entry.getValue()
									.object());
						} else {
							System.out.println(entry.getValue().object());
						}
					}
				} else if (argss[0].equals("del")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					pm.deldirfile(id, path[path.length - 1]);
				} else if (argss[0].equals("put")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					pm.putdir(id, path[path.length - 1], argss[2]);
				} else if (argss[0].equals("get")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					System.out.println((String) pm.getdir(id,
							path[path.length - 1]));
				}
				else if (argss[0].equals("putaddr")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					Key k=MyCipher.toKey("NI");
					byte[] cy=MyCipher.encrypt(pm.peer().peerAddress(), k);
					pm.putdir(id, path[path.length - 1], cy);
				} else if (argss[0].equals("getaddr")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					byte[] buf=(byte[]) pm.getdir(id,path[path.length - 1]);
					Key k=MyCipher.toKey("NI");
					buf=MyCipher.decrypt(buf, k);
					System.out.println((PeerAddress)Utils.decodeJavaObject(buf,0,buf.length));
				} 
				else if (argss[0].equals("exit")) {
					pm.peer().shutdown();
					break;
				}
				else if(argss[0].equals("puttxt"))
				{
					File file=new File(argss[2]);
					Long filelength = file.length(); // 获取文件长度
					byte[] filecontent = new byte[filelength.intValue()];
					try {
						FileInputStream in = new FileInputStream(file);
						in.read(filecontent);
						in.close();

					} catch (FileNotFoundException e) {

						e.printStackTrace();

					} catch (IOException e) {

						e.printStackTrace();

					}
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}					
					System.out.println(pm.putdir(id, path[path.length - 1], new String(filecontent)));

				}
				else if(argss[0].equals("putbin"))
				{
					File file=new File(argss[2]);
					Long filelength = file.length(); // 获取文件长度
					byte[] filecontent = new byte[filelength.intValue()];
					try {
						FileInputStream in = new FileInputStream(file);
						in.read(filecontent);
						in.close();

					} catch (FileNotFoundException e) {

						e.printStackTrace();

					} catch (IOException e) {

						e.printStackTrace();

					}
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length - 1; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}					
					System.out.println(pm.putdir(id, path[path.length - 1], filecontent));				
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
