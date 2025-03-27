import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;


public class Barista {
    private final static int port = 8888;
    private final static int max_coffee = 2;
    private final static int max_tea = 2;

    private static final Set<ClientHandler> activeClients = new HashSet<>();
    private static final Map<String, ClientState> clients = new ConcurrentHashMap<>();
    private static final Queue<ClientHandler.Order> waitingArea = new ConcurrentLinkedQueue<>();
    private static final List<ClientHandler.Order> brewingArea = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, List<ClientHandler.Order>> trayArea = new ConcurrentHashMap<>();



    public static void main(String[] args) {
        try (ServerSocket sk = new ServerSocket(port)) {
            System.out.println("Barista server is running...");

            ExecutorService pool = Executors.newCachedThreadPool();
            ClientHandler.Brewing();

            while (true) {
                Socket clientSocket = sk.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                synchronized (activeClients) {
                    activeClients.add(clientHandler);  // Add new client to the active clients set
                }
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }

    }



    private static class ClientState {
        private boolean idle = true;
        private boolean waiting = false;
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private String clientName;
        public static final String GOODBYE_MESSAGE = "Thank you for visiting cafe!";

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader messageIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter messageOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            ) {
                messageOut.println("Welcome to the Virtual Cafe! May I have your name?");
                clientName = messageIn.readLine();

                if (clientName != null && !clientName.trim().isEmpty()) {
                    clients.put(clientName, new ClientState());
                    System.out.println(clientName + " has joined the cafe");
                    messageOut.println("Welcome " + clientName + ".\nWhat would you like to order?");
                }

                String command;
                while ((command = messageIn.readLine()) != null) {
                    CommandHandler(command, messageOut);
                }
            } catch (IOException e1) {
                if (clientName != null) {
                    clients.remove(clientName);
                    System.out.println(clientName + " has left the cafe..");
                }
                try {
                    // Ensure the socket is still open before trying to send a goodbye message
                    if (!clientSocket.isClosed()) {
                        PrintWriter messageOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                        messageOut.println(GOODBYE_MESSAGE);
                    }
                    clientSocket.close();  // Close the client socket after sending the message
                } catch (IOException e2) {
                    System.err.println("Error sending goodbye message: " + e2.getMessage());
                }
            } finally {
                synchronized (activeClients) {
                    activeClients.remove(this);  // Remove client from active clients
                }
            }
        }



        public void CommandHandler(String command, PrintWriter messageOut) {
            String lowerCaseCommand = command.trim().toLowerCase();
            String[] parts = lowerCaseCommand.split(" "); // Split by spaces

            if (parts.length == 0) {
                messageOut.println("Unknown command. Please try again.");
                return;
            }

            String commandType = parts[0];

            switch (commandType) {
                case "order":
                    if (parts.length < 2) {
                        messageOut.println("Invalid order format. Please use 'order <quantity> coffee/tea'.");
                    } else {
                        OrderHandler(command, messageOut);
                    }
                    break;

                case "status":
                    sendOrderStatus(messageOut);
                    break;

                case "collect":
                    collectOrder(messageOut);
                    break;

                case "exit":
                    messageOut.println("Thank you for coming " + clientName + ". Come back soon!\nPress 'Ctrl+C' to disconnect from the Barista server");
                    clients.remove(clientName);
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        System.err.println("Error with closing connection for " + clientName);
                    }
                    return;

                default:
                    messageOut.println("Unknown command. Please try again.");
                    break;
            }
        }


        public void OrderHandler(String command, PrintWriter messageOut) {
            try {
                    String handleCommand = command.toLowerCase().trim();
                    handleCommand = handleCommand.replace(" and ", " ");
                    String[] parts = handleCommand.split(" ");
                    if (!parts[0].equals("order") || parts.length < 3 || parts.length % 2 == 0) {
                        messageOut.println("Invalid order format. Please use 'order <quantity> coffee/tea'");
                        return;
                    }

                int teaCount = 0, coffeeCount = 0;
                for (int i = 1; i < parts.length; i += 2) {
                    int quantity = Integer.parseInt(parts[i]);
                    String item = parts[i + 1].toLowerCase();

                    if (item.startsWith("tea")) {
                        teaCount += quantity;
                    } else if (item.startsWith("coffee")) {
                        coffeeCount += quantity;
                    } else {
                        messageOut.println("Invalid item: " + item + ". Please use 'coffee' or 'tea'.");
                        return;
                    }
                }

                int origTeaCount = teaCount;
                int origCoffeeCount = coffeeCount;

                while (teaCount > 0) {
                    int orderChunk = Math.min(teaCount, max_tea);
                    waitingArea.add(new Order(clientName, false, true, orderChunk, clientSocket));
                    teaCount -= orderChunk;
                }

                while (coffeeCount > 0) {
                    int orderChunk = Math.min(coffeeCount, max_coffee);
                    waitingArea.add(new Order(clientName, true, false, orderChunk, clientSocket));
                    coffeeCount -= orderChunk;
                }


                System.out.println("Order received for " + clientName + " (" + origCoffeeCount + " coffees and " + origTeaCount + " teas)");
                messageOut.println("Order received for " + clientName + " (" + origCoffeeCount + " coffees and " + origTeaCount + " teas)");

                ClientState clientState = clients.get(clientName);
                clientState.idle = false;
                clientState.waiting = true;


            } catch (Exception e) {
                messageOut.println("Invalid order format. Please use 'order <quantity> coffee/tea'.");
            }
        }


        public void sendOrderStatus(PrintWriter messageOut) {
            int waitingTea = 0, waitingCoffee = 0, brewingTea = 0, brewingCoffee = 0, trayTea = 0, trayCoffee = 0;

            // Check orders in waiting area
            synchronized (waitingArea) {
                for (Order order : waitingArea) {
                    if (order.getClientName().equals(clientName)) {
                        if (order.isTea()) waitingTea += order.getQuantity();
                        if (order.isCoffee()) waitingCoffee += order.getQuantity();
                    }
                }
            }

            // Check orders in brewing area
            synchronized (brewingArea) {
                for (Order order : brewingArea) {
                    if (order.getClientName().equals(clientName)) {
                        if (order.isTea()) brewingTea += order.getQuantity();
                        if (order.isCoffee()) brewingCoffee += order.getQuantity();
                    }
                }
            }

            // Check orders in tray area
            List<Order> trayOrders = trayArea.get(clientName);
            if (trayOrders != null) {
                for (Order order : trayOrders) {
                    if (order.isTea()) trayTea += order.getQuantity();
                    if (order.isCoffee()) trayCoffee += order.getQuantity();
                }
            }

            messageOut.println("Order status for " + clientName + ":\n" +
                    "- " + waitingCoffee + " coffee and " + waitingTea + " tea in waiting area\n" +
                    "- " + brewingCoffee + " coffee and " + brewingTea + " tea currently being prepared\n" +
                    "- " + trayCoffee + " coffee and " + trayTea + " tea currently in the tray\n");
        }

        public void collectOrder(PrintWriter messageOut) {
            List<Order> trayOrders = trayArea.remove(clientName);
            if (trayOrders == null || trayOrders.isEmpty()) {
                messageOut.println("No items to collect.");
            } else {
                int collectedTea = 0, collectedCoffee = 0;
                for (Order order : trayOrders) {
                    if (order.isTea()) collectedTea += order.getQuantity();
                    if (order.isCoffee()) collectedCoffee += order.getQuantity();
                }
                messageOut.println("You have collected: " + collectedCoffee + " coffee, " + collectedTea + " tea.");
            }
        }


        private static void Brewing() {
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                synchronized (brewingArea) {
                    // Count current brewing coffees and teas
                    int brewingCoffees = 0;
                    int brewingTeas = 0;

                    for (Order order : brewingArea) {
                        if (order.isCoffee()) brewingCoffees += order.getQuantity();
                        if (order.isTea()) brewingTeas += order.getQuantity();
                    }

                    // Process coffee orders from waiting area
                    Iterator<Order> iterator = waitingArea.iterator();
                    while (iterator.hasNext() && brewingCoffees < max_coffee) {
                        Order nextOrder = iterator.next();
                        if (nextOrder.isCoffee() && brewingCoffees + nextOrder.getQuantity() <= max_coffee) {
                            brewingArea.add(nextOrder);
                            brewingCoffees += nextOrder.getQuantity();
                            iterator.remove(); // Remove from waiting area

                            System.out.println("Brewing order: " + nextOrder.getClientName() +
                                    " " + nextOrder.getQuantity() + " coffee");

                            new Thread(() -> brewOrder(nextOrder)).start();
                        }
                    }

                    // Process tea orders from waiting area
                    iterator = waitingArea.iterator(); // Reuse iterator for tea processing
                    while (iterator.hasNext() && brewingTeas < max_tea) {
                        Order nextOrder = iterator.next();
                        if (nextOrder.isTea() && brewingTeas + nextOrder.getQuantity() <= max_tea) {
                            brewingArea.add(nextOrder);
                            brewingTeas += nextOrder.getQuantity();
                            iterator.remove(); // Remove from waiting area

                            System.out.println("Brewing order: " + nextOrder.getClientName() +
                                    " " + nextOrder.getQuantity() + " tea");

                            new Thread(() -> brewOrder(nextOrder)).start();
                        }
                    }
                }
            }, 0, 1, TimeUnit.SECONDS);
        }


        private static void brewOrder(Order order) {
            try {
                Thread.sleep(order.isCoffee() ? 45000 : 30000);

                synchronized (brewingArea) {
                    brewingArea.remove(order); // Remove from brewing area
                }

                // Place the brewed order in the tray area
                trayArea.computeIfAbsent(order.getClientName(), k -> new ArrayList<>()).add(order);

                System.out.println("Order for " + order.getClientName() + " (" +
                        order.getQuantity() + " " + (order.isCoffee() ? "coffee" : "tea") + ") is ready!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }


        private static class Order {
            private final Socket clientSocket;
            private final String clientName;
            private final boolean isCoffee;
            private final boolean isTea;
            private final int quantity;

            public Order(String clientName, boolean isCoffee, boolean isTea, int quantity, Socket clientSocket) {
                this.clientName = clientName;
                this.isCoffee = isCoffee;
                this.isTea = isTea;
                this.quantity = quantity;
                this.clientSocket = clientSocket;
            }

            public boolean isCoffee() { return isCoffee; }

            public boolean isTea() { return isTea; }

            public String getClientName() { return clientName; }

            public int getQuantity() { return quantity; }

            public Socket getClientSocket() { return clientSocket; }
        }
    }
}