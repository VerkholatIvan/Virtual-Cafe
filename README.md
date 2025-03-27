# **Virtual Café System**

## Description
The Virtual Café System is a multi-threaded client-server application simulating a virtual cafe.
A customer plays as a role, where he can order tea or coffee and check status of his order. Barista is the server
which is responsible for making orders and automatically manage drinks in real time between three areas: waiting, brewing and waiting area. 



## Notable Features

* **Multithreaded Interaction:** Supports multiple customers simultaneously with efficient handling of orders.
* **Order Queue Management:** Implements a waiting area, brewing area, and tray area to manage orders systematically.
* **Real-Time Status Updates:** Customers can check the status of their orders anytime.
* **Custom Order Handling:** Accepts complex orders (e.g., multiple items) and handles brewing limits (2 coffees and 2 teas at a time).
* **Threaded Brewing System:** Prepares coffee in 45 seconds and tea in 30 seconds.


## Instructions

### Compile the Program

1. Have both of the files Barista.java and Customer.java in the same directory.


2. Compile the files using `javac -cp "." Barista.java` and `javac -cp "." Customer.java` **doing it in separate command-line-terminals.**

    

### Running the Server

1. Start the server by typing `java -cp "." Barista`  in the first terminal.

### Running the Customer

1. In the second terminal start the customer application by entering `java -cp "." Customer`.
   
    _It is also possible to run the client program without compiling by entering_ `ncat localhost 8888` _**in original directory** whenever opening the terminal._


2. Start entering commands.


### Customer Commands
* `order <quantity> coffee/tea: Place an order (e.g., order 1 coffee and 2 tea).`


* `status: Check the status of your order.`


* `collect: Collect ready items from the tray.`


* `exit: Exit the cafe. It is reccomended to disconnect from the server by pressing 'Ctrl+C'`


## Known Issues and Limitations

1. **Manual Client Disconnection:** Clients must manually disconnect `Ctrl+C`. If using `exit` command after pressing `Enter` key two times the Customer crashes.


2. **Error Recovery:** If the server crashes, all active clients lose their session state.


3. **Order Parsing:** The order command parser may not handle malformed input gracefully in certain edge cases.4. 


4. **Brewing Limits:** Hardcoded limits for brewing coffee and tea (2 each) may not suit all use cases.
