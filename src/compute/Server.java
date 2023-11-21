package compute;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.jsonwebtoken.lang.Arrays;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Server extends UnicastRemoteObject implements ServerService {

	static private final String logFile = "./src/log.txt";
	private Authentication auth;
	private TokenVerifier token;
	private Map<String, List<String>> printQueue;
	private Map<String, String> printServerConfig;

	private boolean SERVER_IS_ON = false; // false = OFF, true = ON

	public Server() throws RemoteException {
		super();
		auth = new Authentication();
		token = new TokenVerifier();
		printQueue = new HashMap<>();
		printServerConfig = new HashMap<>();
	}

	// prints file filename on the specified printer
	//random line added to test git
	public String print(String filename, String printer, String authToken) throws RemoteException {
		log("print: " + filename + ", " + printer + " - jwt: " + authToken);

		if (!SERVER_IS_ON)
			return "SERVER_IS_OFF";
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		List<String> queue = printQueue.get(printer);
		if (queue == null) {
			queue = new ArrayList<>();
			printQueue.put(printer, queue);
		}
		queue.add(filename);

		System.out.println("Print request received from authenticated user.");

		return null;
	}

	// lists the print queue for a given printer on the user's display in lines of
	// the form <job number> <file name>
	public String queue(String printer, String authToken) throws RemoteException {
		log("queue: " + printer + " - jwt:  " + authToken);

		if (!SERVER_IS_ON)
			return "SERVER_IS_OFF";
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		List<String> queue = printQueue.get(printer);
		StringBuilder queueResult = new StringBuilder();

		if (queue == null || queue.isEmpty()) {
			return "Print queue for printer " + printer + " is empty.";
		} else {
			for (int i = 0; i < queue.size(); i++) {
				queueResult.append((i + 1) + " " + queue.get(i) + "\n");
			}
			return queueResult.toString();
		}
	}

	// moves job to the top of the queue
	public String topQueue(String printer, String job, String authToken) throws RemoteException {
		log("topQueue: " + printer + ", " + job + " - jwt:  " + authToken);

		if (!SERVER_IS_ON)
			return "SERVER_IS_OFF";
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		List<String> queue = printQueue.get(printer);

		if (queue == null || queue.isEmpty()) {
			return "Print queue for printer " + printer + " is empty.";
		} else {
			int jobNum = Integer.parseInt(job);
			if (jobNum < 1 || jobNum > queue.size()) {
				return "Invalid job number.";
			}

			String jobToMove = queue.remove(jobNum - 1);
			queue.add(0, jobToMove);
			return "Moved job " + jobNum + " to the top of the queue for printer " + printer + ".";
		}
	}

	// starts the print server
	public String start(String authToken) throws RemoteException {
		log("start" + " - jwt: " + authToken);
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		SERVER_IS_ON = true;
		System.out.println("Print server started.");

		return null;
	}

	// stops the print server
	public String stop(String authToken) throws RemoteException {
		log("stop" + " - jwt: " + authToken);
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		try {
			auth.deleteTokens();
		} catch (IOException e) {
			System.err.println("Error deleting tokens: " + e.getMessage());
		}

		SERVER_IS_ON = false;
		System.out.println("Print server stopped.");

		return null;
	}

	// stops the print server, clears the print queue and starts the print server
	// again
	public String restart(String authToken) throws RemoteException {
		log("restart" + " - jwt:  " + authToken);
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		printQueue.clear();
		System.out.println("Print queue cleared.");

		return start(authToken);
	}

	// prints status of printer on the user's display
	public String status(String printer, String authToken) throws RemoteException {
		log("status: " + printer + " - jwt: " + authToken);
		if (!SERVER_IS_ON)
			return "SERVER_IS_OFF";
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		List<String> queue = printQueue.get(printer);
		boolean printerIsWorking = queue != null && !queue.isEmpty();

		if (printerIsWorking) {
			return "Printer \"" + printer + "\" is printing " + queue.get(0) + ".";
		}
		return "Printer \"" + printer + "\" is idle or not available.";
	}

	// prints the value of the parameter on the print server to the user's display
	public String readConfig(String parameter, String authToken) throws RemoteException {
		log("readConfig: " + parameter + " - jwt: " + authToken);
		if (!SERVER_IS_ON)
			return "SERVER_IS_OFF";
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		String value = printServerConfig.get(parameter);
		if (value == null) {
			return "Parameter " + parameter + " is not set.";
		}
		return parameter + ": " + value;
	}

	// sets the parameter on the print server to value
	public String setConfig(String parameter, String value, String authToken) throws RemoteException {
		log("setConfig: " + parameter + " - jwt: " + value);
		if (!SERVER_IS_ON)
			return "SERVER_IS_OFF";
		if (tokenNotValid(authToken))
			return "TOKEN_NOT_VALID";

		printServerConfig.put(parameter, value);
		System.out.println("Parameter " + parameter + " set to " + value + ".");
		return null;
	}

	/*----------------------------------- */

	// authenticates the user and returns a token
	public String login(String username, String password) throws RemoteException, NoSuchAlgorithmException {
		String authToken = auth.authenticate(username, password);

		if (authToken == null) {
			System.out.println("Login failed: credentials not valid.");
			return null;
		}

		System.out.println("Login successful. Token generated and stored.");
		return authToken;
	}

	// utility: checks if the token is valid
	public boolean tokenNotValid(String authToken) throws RemoteException {
		if (authToken == null || !token.validate(authToken)) {
			System.out.println("Token validation failed: token not valid or expired.");
			return true;
		}
		return false;
	}

	// this is called by Printer.java, when a printer finishes printing a job, the
	// printer queue is updated
	public String removeFirstFromPrintingQueues() throws RemoteException {
		StringBuilder result = new StringBuilder();

		for (String printer : printQueue.keySet()) {
			List<String> queue = printQueue.get(printer);
			if (queue == null || queue.isEmpty()) {
				result.append("Printer " + printer + " has no jobs in the queue.\n");
			} else {
				String job = queue.remove(0);
				result.append("Job " + job + " removed from printer " + printer + " queue.\n");
			}
		}

		return result.toString();
	}

	// logs the method call in the log file
	public void log(String string) throws RemoteException {
		try {
			// Opens log file in append mode
			BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));

			// writes date and time of the method call
			LocalDateTime now = LocalDateTime.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			String formattedDateTime = now.format(formatter);

			writer.write(formattedDateTime + " - " + string + "\n");

			// closes the file
			writer.close();
		} catch (IOException e) {
			System.err.println("Error writing the log file: " + e.getMessage());
		}
	}
}
