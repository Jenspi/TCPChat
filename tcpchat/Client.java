/**
 * @description
 * Client.java - A simple, terminal-based chat client that connects to a server and allows users to send and receive messages
 * using multithreading, TCP sockets, and a server-client architecture. (To be used in conjunction with Server.java)
 * <p>
 * The client (program) connects to a specified server address and port specified by the user, prompts the user for a username,
 * and then allows the user to send messages to the server. Incoming messages from the server (sent by other users connected to the same server) are displayed in real-time.
 * The client uses multithreading to handle user input and incoming messages simultaneously. The client also uses sockets to establish a TCP connection with the server.
 * <p>
 *
 * @author Jenny Spicer
 * @version 1.0
 * Date: Jan 20, 2024
 * Updated: December 7, 2025
 * License: CC BY-NC-ND 4.0 International (https://creativecommons.org/licenses/by-nc-nd/4.0/)
 */
package tcpchat;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.io.*;
import java.lang.Runnable;
import java.lang.reflect.Array;

/**
 * Client class to handle connecting to server and user input/output.
 * @since 1.0
 */
public class Client{
    private int port;

    /**
     * Constructor to initialize the client's port, address, and create its thread.
     * @param address The server address to connect to; localhost by default (no user input). Use IP address of Server for connecting clients on different machines.
     * @param port The server port to connect to; 5000 by default (no user input).
     * @since 1.0
     */
    public Client(String address, int port) {
        this.port = port;
        ClientHandler ch = new ClientHandler(address, port);

        Thread thread = new Thread(ch);
        thread.start();
    }

    /**
     * Client thread (instance) to handle user input and server communication.
     * @since 1.0
     */
    private class ClientHandler implements Runnable{
        // For multithreaded coding-- this inner class is what will allow multiple users at once
        private Socket socket = null;
        private BufferedReader in = null;
        private DataOutputStream out = null;
        private DataInputStream serverIn = null;
        private String username;
        private SocketChannel channel;
        private SocketAddress socketAddr;

        /**
         * Constructor to initialize the following: client's Socket, DataOutputStream & DataInputStream, and message listener Thread.
         * @param address The server address to connect to.
         * @param port The server port to connect to.
         * @throws Exception If an error occurs when creating the socket or streams.
         * @since 1.0
         */
        public ClientHandler(String address, int port){
            try {
                this.socket = new Socket(address, port);
                System.out.printf("Connected to port %d!\n", port);

                /* Inputs & Outputs:
                 * CLIENT (in Client.java): 
                 *  Input ---> System.in                                          (keyboard)
                 *  Output --> current client's socket's .getOutputStream()       (socket.getOutputStream())
                 * 
                 * SERVER (in Server.java): 
                 *  Input ---> current client's socket's .getInputStream()        (socket.getInputStream())
                 *  Output --> ALL clients' socket's .getOutputStream()           (ALL socket.getOutputStream())
                 */
                
                in = new BufferedReader(new InputStreamReader(System.in));
                out = new DataOutputStream(socket.getOutputStream());
                serverIn = new DataInputStream(socket.getInputStream());
                
                // Start a thread to listen for incoming messages
                Thread listenerThread = new Thread(new ServerListener());
                listenerThread.start();
            } catch (Exception e) {
                //System.out.println("error " + e.getMessage());
                System.out.println("ERROR... Must have server running first before connecting client");
            }
        }
        
        /**
         * Inner class to listen for incoming messages from the server.
         * @throws IOException If an I/O error occurs while reading from the server.
         * @since 1.0
         */
        private class ServerListener implements Runnable {
            @Override
            public void run() {
                try {
                    while (true) {
                        String message = serverIn.readUTF();
                        // Print the incoming server message (typically other clients' messages) to the current client's console
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    // Connection closed
                }
            }
        }

        /**
         * Run method to handle user input and communication with the server. Client socket disconnects when user types "bye".
         * @throws IOException If an I/O error occurs, usually due to connection issues.
         * @since 1.0
         */
        @Override
        public void run(){
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
            LocalDateTime now;

            // run() -- USERNAME ENTRY:
            try {
                System.out.printf("Enter username to continue: ");
                username = in.readLine();
                Thread.currentThread().setName(username+"-thread");

                while(username.trim().isEmpty()){
                    System.out.printf("Not permitted until you enter a valid username. Enter a username to continue: ");
                    username = in.readLine();
                }

                // send to server so it knows this user's name
                String entrance = String.format("~~ %s [Server]: %s has joined the chat.", dtf.format(LocalDateTime.now()), username);
                out.writeUTF(entrance);
            } catch (IOException e) {
                // User leaves manually before entering username
                System.out.println("User did not enter valid username.");
            }
            catch(NullPointerException npe){
                System.out.println("Please check that the server is running on the provided port and try again.");
            }
    
            // run() -- REST OF MESSAGES:
            String line = "";
            String help = "help- print this menu\nbye- disconnect client from chat\nallusers- list all connected users\n";
            while (!line.trim().toLowerCase().equals("bye")) {
                try {
                    line = in.readLine(); //Need to use BufferedReader and readLine() bc DataInputStream/readUTF() doesn't work
                    
                    /* to be read in by server's input stream and broadcasted to all users, including sender.
                        sender does not send directly to own output stream, just directly to server. */
                    String formatted_line = String.format("%s [%s]: %s", dtf.format(LocalDateTime.now()), username, line);
                    out.writeUTF(formatted_line);
                    
                } catch (IOException i) {
                    System.out.println(i);
                }
            }

            // User initiated exit with keyword "bye":
            try {
                String goodbye = String.format("%s [Server]: Goodbye, %s.", dtf.format(LocalDateTime.now()), username);
                System.out.println(goodbye);

                in.close();
                out.close();
                socket.close();
            } catch (IOException i) {
                System.out.println(i);
            }
        }

    }

    /**
     * Main method to start the client program.
     * @param args Command line arguments: [0] = server IP address (optional; "localhost" by default), [1] = server port (optional; "5000" by default).
     * @throws ArrayIndexOutOfBoundsException If an error occurs during client initialization, usually due to invalid port input.
     * @since 1.0
     */
    public static void main(String[] args) 
	{ 
        try{
            String ipaddress = args[0];
            int port = Integer.parseInt(args[1]);
            System.out.println("---------------------------");
            System.out.printf("Connecting to port %d...\n", port);
            Client client = new Client(ipaddress, port);
        } catch(Exception ArrayIndexOutOfBoundsException){
            if(args[0] != null && !args[0].trim().isEmpty() && args[0].matches("\\d+")){
                int port = Integer.parseInt(args[0]);
                System.out.println("---------------------------");
                System.out.println("No valid entry for server address. Example: \"java tcpchat.Client 192.168.4.38 3000\"...\nConnecting to default server address \"localhost\" with port " + port + "...");
                Client client = new Client("localhost", port);
            }
            else{
                System.out.println("---------------------------");
                System.out.println("No valid entry for one or both options: server address and port. Example: \"java tcpchat.Client 192.168.4.38 3000\" or \"java tcpchat.Client localhost 3000\"...\nConnecting to default server address \"localhost\" and default port \"5000\"...");
                Client client = new Client("localhost", 5000);
            }
        }
	}//end main method
}
