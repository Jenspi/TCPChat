/**
 * Server.java - A simple, terminal-based chat server that allows multiple clients to connect and communicate
 * using multithreading, TCP sockets, and a server-client architecture. (To be used in conjunction with Client.java)
 * <p>
 * The server listens on a specified port (specified by the user) for incoming client connections. When a client connects,
 * the server spawns a new thread to handle communication with that client. The server maintains a list of connected clients and broadcasts messages
 * received from any client to all connected clients (server-client architecture). The server uses multithreading to handle multiple clients simultaneously
 * and uses sockets to establish TCP connections with clients.
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;

/**
 * Server class to handle multiple client connections and message broadcasting.
 * @since 1.0
 */
public class Server {
    private Socket socket = null;
    private ServerSocket server = null;
    private DataInputStream in = null;
    private DataOutputStream out;
    private ArrayList<Socket> clientNamesList = new ArrayList<>();
    private HashMap<String, Socket> clients = new HashMap<>();
    private HashMap<String, DataOutputStream> clientOutputStreams = new HashMap<>();//avoids recreating streams when broadcasting
    private int port;

    /**
     * Constructor to initialize the server's port, thread, and start listening for client connections.
     * @param port The server port to listen on; 5000 by default (no user input).
     * @throws ArrayIndexOutOfBoundsException If an error occurs when opening the socket, usually due to the port already being used by another process on the machine.
     * @since 1.0
     */
    public Server(int port) {
        try {
            this.port = port;
            server = new ServerSocket(port);
            System.out.println("Server started!");
            System.out.println("Awaiting client login...");

            // continuously accept new client connections:
            while(true){
                Thread thread = new Thread(new ServerHandler(server));
                thread.start();
                Thread.currentThread().setName("Server-thread");
            }
        } catch (BindException e) {
            System.out.println("Oops! One of the following errors has occurred:");
            System.out.println("- The port \"" + port + "\" is already in use by another process on this machine.");
            System.out.println("- The port \"" + port + "\" is invalid (out of range).");
            System.out.println("- The port \"" + port + "\" contains special characters or alphabetical letters.");
            System.out.println("- The port \"" + port + "\" was not specified, defaulted to 5000, and 5000 is taken by another process on the machine.");
            System.out.println("A valid example is as follows: \"java tcpchat.Server 3000\". Please try again.\n");
        }
        catch (IOException e) {
            System.out.println("Error establishing server on port \"" + port + "\". " + e.getMessage());
        }
    }

    /**
     * Server thread (instance) to handle communication with a connected client on the same port.
     * @since 1.0
     */
    private class ServerHandler implements Runnable{
        private Socket socket;
        private ServerSocket server;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;
        private SocketChannel channel;
        private SocketAddress socketAddr;

        /**
         * Constructor to initialize the following: server's Socket, DataOutputStream & DataInputStream.
         * @param server The server socket to accept client connections on.
         * @throws Exception If an error occurs when creating the socket or streams, usually when the port is already in use by another process on the machine.
         * @since 1.0
         */
        public ServerHandler(ServerSocket server){
            try{
                this.server = server;
                this.socket = server.accept();

                /* Inputs & Outputs:
                 * CLIENT (in Client.java): 
                 *  Input ---> System.in                                          (keyboard)
                 *  Output --> current client's socket's .getOutputStream()       (socket.getOutputStream())
                 * 
                 * SERVER (in Server.java): 
                 *  Input ---> current client's socket's .getInputStream()        (socket.getInputStream())
                 *  Output --> ALL clients' socket's .getOutputStream()           (ALL socket.getOutputStream())
                 */

                this.out = new DataOutputStream(socket.getOutputStream());
                this.in = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));

            } catch (Exception e) {
                System.out.println("Error connecting to port " + e.getMessage());
            }
        }

        /**
         * Helper method to extract username from welcome message.
         * @param input The welcome message input string.
         * @return The extracted username.
         * @since 1.0
         */
        private String retreiveUsername(String input){
            /* Example input: 
            ~~ 2025/12/07 21:27:28 [Server]: jackie has joined the chat.
            */
            System.out.println(input);
            String[] username_helperArray = input.split("\\]: |has joined the chat\\.");
            String u = username_helperArray[1].trim();

            return u;
        }

        /**
         * Broadcasts a message to all connected clients (all connected clients, sender, and server).
         * @throws IOException If an I/O error occurs while writing to the output stream, usually when a client disconnects unexpectedly.
         * @param message The message to broadcast.
         */
        public void broadcastMessage(String message) {
            for (DataOutputStream clientOut : Server.this.clientOutputStreams.values()) {
                try {
                    clientOut.writeUTF(message);
                    clientOut.writeUTF("");
                    clientOut.flush();
                } catch (IOException e) {
                    System.out.println("Error broadcasting message to client: " + e.getMessage());//occurs when all clients disconnect
                    //TODO: how to keep socket open after all clients leave?
                }
            }
        }

        /**
         * Broadcasts a message to all connected clients except the specified socket. So far, 
         * exclusively for broadcasting welcome messages to users without the server picking back up on it, which makes it print twice in the server console.
         * @param message The message to broadcast.
         * @param excludeSocket The socket to exclude from broadcasting.
         * @throws IOException If an I/O error occurs while writing to the output stream, usually when a client disconnects unexpectedly.
         * @since 1.0
         */
        public void broadcastMessage(String message, Socket excludeSocket) {
            for (Socket clientSocket : Server.this.clients.values()) {
                if (clientSocket != excludeSocket) {
                    try {
                        DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
                        clientOut.writeUTF(message);
                        clientOut.writeUTF("");
                        clientOut.flush();
                    } catch (IOException e) {
                        System.out.println("Error broadcasting message to client: " + e.getMessage());
                    }
                }
            }
        }

        /**
        * Run method to handle incoming messages from the connected client. Broadcasts messages to all other connected clients, 
        * unless the client specifies a client-side command such as allusers (shows all users connected) or help (shows available commands).
        * @param message The message to broadcast.
        * @throws IOException If an I/O error occurs while reading, writing, or broadcasting to the socket's stream, usually when a client disconnects unexpectedly.
        * @since 1.0
        */
        @Override
        public void run(){
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
            String helpMenu = "help- print this menu\nbye- disconnect client from chat\nallusers- list all connected users\n";
            String line = "";
            DataOutputStream secretClientOutputStream = null;

            while (!line.trim().toLowerCase().equals("bye")) {
                try {
                    line = in.readUTF();
                    
                    if(!line.isEmpty() && line.startsWith("~~") && line.contains("has joined the chat.")){// Edge case: welcome message
                        // initialize username and store client's output stream for future broadcasts
                        this.username = retreiveUsername(line);
                        Server.this.clients.put(this.username, this.socket);
                        Server.this.clientOutputStreams.put(this.username, this.out);
                        // initialize secretClientOutputStream for this client-- used for client requests like "allusers" and "help".
                        secretClientOutputStream = Server.this.clientOutputStreams.get(this.username);
                        broadcastMessage(line, Server.this.socket);
                    }
                    else if(!line.isEmpty() && line.split("]: ").length > 1 && line.split("]: ")[1].toLowerCase().equals("bye")){// Edge case: client is leaving
                        String exitMessage = String.format("~~ %s [Server]: %s has left the chat.", dtf.format(LocalDateTime.now()), this.username);
                        System.out.println(exitMessage);
                        broadcastMessage(exitMessage);
                        Server.this.clients.remove(this.username);
                        Server.this.clientOutputStreams.remove(this.username);

                    }
                    else if(!line.isEmpty() && line.split("]: ").length > 1 && line.toLowerCase().contains("allusers")){// Edge case: client requests all users
                        int i =0;
                        for (String key : Server.this.clientOutputStreams.keySet()) {
                            i++;
                            String f = String.format("%d.) %s", i, key);
                            secretClientOutputStream.writeUTF(f);
                            secretClientOutputStream.writeUTF("\n");
                            secretClientOutputStream.flush();
                        }
                    }
                    else if(!line.isEmpty() && line.split("]: ").length > 1 && line.split("]: ")[1].toLowerCase().equals("help")){// Edge case: client requests help menu
                        secretClientOutputStream.writeUTF(helpMenu);
                        secretClientOutputStream.writeUTF("\n");
                        secretClientOutputStream.flush();
                    }
                    else if(!line.isEmpty() && line.trim().split("]: ").length > 1){// Normal message to be broadcasted to all users; empty messages IGNORED.
                        System.out.println(line);
                        broadcastMessage(line);
                    }
                } catch (IOException i) {
                    //System.out.println("Error " + i.getMessage());
                    break;
                }
            }//end while loop- client entered "bye"

            try{
                // Get rid of inactive users from users list
                Server.this.clients.remove(this.username);
                Server.this.clientOutputStreams.remove(this.username);
                //in.close();
            } catch (Exception e) {
                //System.out.println("User left; input stream closed");
            }
        }
    }

    /**
     * Main method to start the server program.
     * @param args Command line arguments; first argument is the port number (optional; "5000" by default).
     * @throws ArrayIndexOutOfBoundsException If an error occurs during server initialization, usually due to invalid port input and the default port already being used by another process on the machine.
     * @since 1.0
     */
    public static void main(String[] args) 
    {
        try{
            int port = Integer.parseInt(args[0]);
            System.out.println("---------------------------");
            System.out.printf("Connecting to port %d...\n", port);
            Server server = new Server(port);
        } catch(Exception ArrayIndexOutOfBoundsException){
            System.out.println("---------------------------");
            System.out.println("No valid port entered. Connecting to default port 5000...");
            Server server = new Server(5000);
        }
    }//end main method
}
