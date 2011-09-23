package dk.skat.ftpsgateway


import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.ftp.FTPReply;
import org.codehaus.groovy.syntax.ReadException;



class SimpleFileClient {
	
	FTPClient ftp
	
	public SimpleFileClient() {
		ftp = new FTPClient()		
		ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
	}
	
	public SimpleFileClient(keyManager) {
		ftp = new FTPSClient()
		ftp.setKeyManager keyManager
		ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));		
	}

	private static KeyManager initKeyManager(fileName, password){
		println "Initializing $fileName with $password"
		KeyStore ks = KeyStore.getInstance("pkcs12", "SunJSSE");
	
		ks.load(new FileInputStream(new File(fileName)), password.toCharArray());
	
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
	
		kmf.init(ks, password.toCharArray());
	
		KeyManager[] km = (KeyManager[])kmf.getKeyManagers();
	
		return km[0]
	}
	
	def disconnect(){
		if (ftp.isConnected()) {
			try {
				ftp.disconnect();
			}
			catch (IOException f) {
				// do nothing
			}
		}
	}
	
	
	def connectClient(host, port){		
		ftp.connect(host, port);		
	
		int reply = ftp.getReplyCode();
	
	
		if (!FTPReply.isPositiveCompletion(reply)) {
			ftp.disconnect();
			System.err.println("FTP server refused connection.");
			System.exit(1);
		}
	
		if (! ftp.login("anonymous", "")) {
			ftp.logout();
		}
	
		System.out.println("Remote system is " + ftp.getSystemName());	
	
		// Use passive mode as default because most of us are behind firewalls these days.
		ftp.enterLocalPassiveMode();
		ftp.features()
		
		return ftp
	}
	
	def uploadFiles(dir, files){
		ftp.changeWorkingDirectory(dir)
		files.each{
			def f = new File(it)
			ftp.storeFile(f.getName(), new FileInputStream(f))
		}
	}
	
	def pollForResponses(remoteDir, fileName){
		ftp.changeToParentDirectory()
		ftp.changeToParentDirectory()
		println "PWD: " + ftp.printWorkingDirectory()
		println "Dirs: " + ftp.listNames()
		ftp.changeWorkingDirectory("out")
		
		while(fileName != null){
			ftp.listNames().each{ fName ->
				String targetName = remoteDir + "_" + fileName + ".response"
				println "fName $fName - targetName $targetName"
				if(fName == targetName){
					println "Downloading!"
					ftp.retrieveFile(fName, new FileOutputStream(fName))
					fileName = null
				} 
			}
			Thread.sleep(5000)
		}
	}
	

	static main(args) {
		def cli = new CliBuilder(usage: 'SimpleFileClient.groovy -c <certFile> -s <server-addr> -p <port> -d <remote dir> file1 file2 ...')
		// Create the list of options.
		cli.with {
			h longOpt: 'help', 'Show usage information'			
			c required: false, longOpt: 'cert', args: 1, argName: 'certFile', 'Path to the pkcs12 with the client cert'
			s required: true, longOpt: 'server', args: 1, argName: 'host', 'The hostname or ip of the server'
			p required: true, longOpt: 'port' , args: 1, argName: 'port', 'The port of the server'
			d required: true, longOpt: 'directory' , args: 1, argName: 'dir', 'The directory to upload to'			
			t longOpt: 'timeout' , args: 1, argName: 'timeout', 'The time stay polling /out in secs. Default is 60 secs'
		}
		
		def options = cli.parse(args)
		if (!options) {
			return
		}
		// Show usage text when -h or --help option is used.
		if (options.h) {
			cli.usage()
			return
		}
		
		SimpleFileClient sfc = makeClient(options)
		println "Connecting..."
		sfc.connectClient(options.s, options.p as Integer)
		
		println "Uploading ${options.arguments().size()} files: ${options.arguments()}"
		
		sfc.uploadFiles(options.d, options.arguments())		
		sfc.pollForResponses(options.d, options.arguments().first() )
		
		sfc.disconnect()
	}
	
	static SimpleFileClient makeClient(options){
		if(options.c){
			println "Please input password for ${options.c}"
			KeyManager km = null
			try{
				System.in.eachLine{ line ->
					km = initKeyManager(options.c, line)
					if(km) { throw new ReadException(null) } else { println "Wrong password, try again" }
				}
			} catch (ReadException re) {/* do nothing */}
		
			return new SimpleFileClient(km)
		} else {
			return new SimpleFileClient()
		}
	}

}