import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Agent extends Thread {

	String serverIp = "localhost";
	String ip;
	private int serverPort;
	private int portAgentaWprowadzajacego;
	public ServerSocket serverSocket;
	private List<Integer> portList = new ArrayList<>();
	long start;
	long counter = 0;
	long suma = 0;

	/**
	 * Tworzenie Agenta bez agenta wprowadzajacego
	 * 
	 * @param serverPort
	 */
	public Agent(int serverPort) {
		this.serverPort = serverPort;
		this.start = System.currentTimeMillis();
	}

	/**
	 * Tworzenie agenta, posiadającego agenta wprowadzajacego
	 * 
	 * @param serverPort
	 * @param portAgentaWprowadzajacego
	 */
	public Agent(int serverPort, int portAgentaWprowadzajacego) {
		this.start = System.currentTimeMillis();
		this.serverPort = serverPort;
		this.portAgentaWprowadzajacego = portAgentaWprowadzajacego;
		if (!portList.contains(portAgentaWprowadzajacego)) {
			portList.add(portAgentaWprowadzajacego);
		}
	}

	public void run() {
		/**
		 * Pobranie listy adresów od agenta wprowadzającego
		 */

		if (!portList.isEmpty()) {
			pobierzListeAdresow(portList.get(0));
			for (int i = 0; i < portList.size(); i++) {
				if (portList.get(i) != this.serverPort && portList.get(i) != portAgentaWprowadzajacego) {
					pobierzListeAdresow(portList.get(i));
				}
			}
		}
		/**
		 * Pobranie licznikow od agentów z listy adresów
		 */

		if (!portList.isEmpty()) {
			for (int i = 0; i < portList.size(); i++) {
				if (portList.get(i) != this.serverPort) {
					pobierzStanLicznika(portList.get(i));
				}
			}
		}
		/**
		 * Wysyłanie komunikatów SYN do agentów z listy adresów
		 */
		
		if (!portList.isEmpty()) {
			for (int i = 0; i < portList.size(); i++) {
				if (portList.get(i) != this.serverPort) {
					synchronizujLicznik(portList.get(i));
				}
			}
		}

		if (!this.portList.contains(serverPort)) {
			portList.add(serverPort);
		}
		/**
		 * Przygotowanie nasłuchu na porcie serverPort.
		 */
		try {
			serverSocket = new ServerSocket(serverPort);
			String command;
			String[] args;
			String response = "";
			long synResponse = 0;
			long timeResponse = 0;
			while (true) {
				Socket connectionSocket = serverSocket.accept();			
				BufferedReader inFromClient = new BufferedReader(
						new InputStreamReader(connectionSocket.getInputStream()));
				DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
				args = inFromClient.readLine().split(" ");
				command = args[0];
				log("command received: [" + command + "]");
				switch (command) {
				/**
				 * Wysyłanie komunikatów( SYN, NET, CLK) na porty agentów.
				 */
				case "SYN":
					connectionSocket.close();
					outToClient.close();
					synResponse = handleSync(args[1]);
					//log("NET response [" + synResponse + "]");
					this.counter = this.counter + synResponse / portList.size();
					log("My new time: " + this.counter);
					long difference = handleClk(args[1]) - this.counter;
					this.start = this.start + difference;
					break;
				case "NET":
					response = handleNet(args[1]); // port agenta przychodzacego
					log("NET response [" + response + "]");
					outToClient.writeBytes(response + '\n');
					break;
				case "CLK":
					timeResponse = handleClk(args[1]);
					log("CLK response: [" + timeResponse + "]");
					outToClient.writeBytes("" + timeResponse + '\n');
					log("CLK  END");	
					break;

				}
				if(!connectionSocket.isClosed()) {
					connectionSocket.close();
					outToClient.close();
				}
				
			}

		} catch (

		IOException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Pobieranie listy adresów reszty agentów.
	 * 
	 * @param portAgentaWprowadzajacego
	 */
		private void pobierzListeAdresow(Integer portAgentaWprowadzajacego) {
			try {
				Socket socket = new Socket(serverIp, portAgentaWprowadzajacego);
				DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
				BufferedReader inFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				outToServer.writeBytes("NET " + this.serverPort + '\n');
				String response = inFromServer.readLine();
				log("pobierzListeAdresow response [" + response + "]");
				String[] args = response.split(", ");
				for (int i = 0; i < args.length; i++) {
					String intValue = args[i].replaceAll("[\\D]", "");
					int porty = Integer.parseInt(intValue);
					if (!this.portList.contains(porty)) {
						this.portList.add(porty);
					}
				}
				socket.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

	/**
	 * Pobieranie wartosci licznika od agenta.
	 * 
	 * @param portAgentaWprowadzajacego
	 */
	private void pobierzStanLicznika(Integer portAgentaWprowadzajacego) {
		try {
			int countAgent = portList.size();
			Socket socket = new Socket(serverIp, portAgentaWprowadzajacego);
			DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
			BufferedReader inFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			outToServer.writeBytes("CLK " + this.serverPort + '\n');
			String response = inFromServer.readLine();
			log("pobierzStanLicznika CLK Response from: " + response);
			suma = suma + Long.parseLong(response);
			this.counter = suma / countAgent;
			log("My new time: " + this.counter);
			outToServer.close();
			socket.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	/**
	 * Synchronizacja agenta, który otrzymał polecenie SYN
	 * @param portAgenta
	 * @return
	 */

	private long synchronizujAgentow(Integer portAgenta) {
		try {
			suma = 0;
			Socket socket = new Socket(serverIp, portAgenta);
			DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
			BufferedReader inFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			outToServer.writeBytes("CLK " + portAgenta + '\n');
			String response = inFromServer.readLine();
			log("synchronizujAgentow CLK Response from: " + response);
			suma = Long.parseLong(response);
			outToServer.close();
			socket.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return suma;
	}

	/**
	 * Wysyłanie komunikatu SYN do reszty agentów
	 * 
	 * @param portAgentaWprowadzajacego
	 */
	private void synchronizujLicznik(Integer portAgentaWprowadzajacego) {
		try {
			Socket socket = new Socket(serverIp, portAgentaWprowadzajacego);
			DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
			outToServer.writeBytes("SYN " + this.serverPort + '\n');
			outToServer.close();
			socket.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

	}

	private long handleSync(String arg) {
		long sum = 0;
		int port = Integer.parseInt(arg);
		sum += synchronizujAgentow(port);
		/*
		for (int i = 0; i < portList.size(); i++) {
			if (this.portList.get(i) != this.serverPort && this.portList.get(i) != port) {
				sum += synchronizujAgentow(this.portList.get(i));
			}	
		}
		*/ 
		return sum;
	}

	private String handleNet(String arg) {
		int port = Integer.parseInt(arg);
		if (!this.portList.contains(port)) {
			this.portList.add(port);
		}
		String response = Arrays.toString(this.portList.toArray());
		return response;
	}

	private long handleClk(String arg) {
		long response = 0;
		if (this.counter == 0) {
			long startTime = start;
			long stopTime = System.currentTimeMillis();
			response = (stopTime - startTime);
			this.counter = response;
		} else {
			long startTime = start;
			long stopTime = System.currentTimeMillis();
			long difference = stopTime - startTime;
			response = difference + counter;
		}
		return response;
	}

	public static void main(String[] args) throws UnknownHostException, IOException {
		int serverPort = 0;
		try {
			serverPort = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Niepoprawny argument");
		}
		int portAgentaWprowadzajacego = 0;
		try {
			if (args.length > 1) {
				portAgentaWprowadzajacego = Integer.parseInt(args[1]);
			}
		} catch (Exception e) {
			System.out.println("Niepoprawny argument");
		}
		Agent a;
		if (portAgentaWprowadzajacego == 0) {
			a = new Agent(serverPort);
		} else {
			a = new Agent(serverPort, portAgentaWprowadzajacego);
		}
		a.start();
	}
	
	public void log(String txt) {
		System.out.println("AGENT [" + this.serverPort + "]: " + txt);
	}

}