package dk.skat.ftpsgatewayclient


import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.ftp.FTPReply;
import org.codehaus.groovy.syntax.ReadException;



class SimpleFileClient {
	
	FTPSClient ftp = new FTPSClient()
	
	
	
	
	public SimpleFileClient(keyManager) {
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
		files.each{
			def f = new File(it)
			ftp.storeFile(f.getName(), new FileInputStream(f))
		}
	}
	
	def pollForResponses(remoteDir, files){
		ftp.changeToParentDirectory()
		ftp.changeToParentDirectory()
		ftp.changeWorkingDirectory("out")
				
		ftp.listNames().each{ println it }
		
	}
	

	static main(args) {
		def cli = new CliBuilder(usage: 'SimpleFileClient.groovy -c <certFile> -s <server-addr> -p <port> -d <remote dir> file1 file2 ...')
		// Create the list of options.
		cli.with {
			h longOpt: 'help', 'Show usage information'			
			c required: true, longOpt: 'cert', args: 1, argName: 'certFile', 'Path to the pkcs12 with the client cert'
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
		
		println "Please input password for ${options.c}"
		KeyManager km = null
		try{
			System.in.eachLine{ line ->			
				km = initKeyManager(options.c, line)			
				if(km) { throw new ReadException(null) } else { println "Wrong password, try again" }
			}
		} catch (ReadException re) {/* do nothing */}
		
		SimpleFileClient sfc = new SimpleFileClient(km)
		println "Connecting..."
		sfc.connectClient(options.s, options.p as Integer)
		
		println "Uploading ${options.arguments().size()} files: ${options.arguments()}"
		
		sfc.uploadFiles(options.d, options.arguments())		
		sfc.pollForResponses(options.d, options.arguments())
		
		sfc.disconnect()
	}

}
