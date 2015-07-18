package com.DHTWeb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.Key;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.DigestResult;
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
				} 
				else if (argss[0].equals("roots")) {

					for (Entry<Number640, Data> entry : pm.readdir(PeerManager.ROOT_PEERS).m
							.entrySet()) {
						System.out.print(entry.getKey().contentKey() + "--->");

						if (entry.getValue().object().getClass() == PeerAddress.class) {
							System.out.println((PeerAddress) entry.getValue().object());
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
				}else if (argss[0].equals("digest")) {
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length ; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}
					Map<PeerAddress,Map<Number640,Data>> dmap= pm.digest(id);	
					if(dmap!=null)
					{
						for (Entry<PeerAddress,Map<Number640,Data>> e :dmap.entrySet())
						{
							System.out.println(e.getKey());
							System.out.println("Cnt:"+e.getValue().size());
							for(Entry<Number640, Data> etr : e.getValue().entrySet())
							{
								System.out.println("\t"+etr.getKey());
							}
							System.out.println();
						}
					}
				}else if (argss[0].equals("put")) {
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
					
					Number160 id = Number160.createHash(path[0]);
					System.out.println(pm.rootputdir(id, path[1], pm.peer().peerAddress()));
				} else if (argss[0].equals("getaddr")) {
					path = argss[1].split("/");
					Number160 id = Number160.createHash(path[0]);
					System.out.println((PeerAddress)pm.rootgetdir(id,path[1]));
				} 
				else if (argss[0].equals("exit")) {
					break;
				}
				else if (argss[0].equals("msend")) {
					pm.mastercall("HI");
				}
				else if (argss[0].equals("rsend")) {
					pm.rootcall("HI");
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
				else if(argss[0].equals("putbin2"))
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
					System.out.println(Utils.makeSHAHash(filecontent));
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}					
					System.out.println(pm.putdirbig(id, filecontent));				
				}
				else if(argss[0].equals("del2"))
				{
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}		
					System.out.println(pm.delfilebig(id));
				}
				else if(argss[0].equals("sha2"))
				{
					path = argss[1].split("/");
					Number160 id = (Number160) pm.getdir(PeerManager.ROOT,
							path[0]);
					for (int i = 1; i < path.length; i++) {
						id = (Number160) pm.getdir(id, path[i]);
					}	
					System.out.println(Utils.makeSHAHash((byte[])pm.getdirbig(id)));
				}
				else
				{
					System.out.println("Unknown command");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
