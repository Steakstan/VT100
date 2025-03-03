VT100 SSH Terminal Emulator
A Java-based terminal emulator that replicates the behavior of VT520-type terminals, featuring secure SSH connectivity and integration with SHD’s order management software. This application supports a wide range of ANSI escape sequences for advanced text formatting, cursor control, and screen buffer management, making it ideal for environments that require legacy terminal interfaces alongside modern SSH and UI frameworks.

Features
SSH Connectivity:
Securely connect to remote servers using SSH via the JSch library. Supports asynchronous connection handling and real-time data exchange.

ANSI Escape Sequence Processing:
Emulates VT520 terminal behavior with support for:

Cursor positioning (absolute and relative modes)
Text formatting (bold, underline, blink, reverse video, conceal)
Screen and line erasing, scrolling regions, and line insertion/deletion
Graphical character mode with charset switching
Screen Buffer Management:
Maintains an internal screen buffer for efficient rendering and manipulation of terminal content, supporting operations such as copying rectangular areas and filling regions with custom characters.

JavaFX User Interface:
Provides an interactive, modern UI built with JavaFX for rendering the terminal, handling keyboard events, and integrating clipboard operations.

Order Management Integration:
Adapted to work with SHD’s order and accounting software:

Processes order confirmations and delivery dates.
Parses and validates order data from Excel files.
Implements business logic based on the current screen content.

Technologies Used
Java 21
JavaFX – for building the graphical user interface.
JSch – for managing SSH connections.
SLF4J/Logback – for logging and debugging.
Apache POI – for Excel file processing (order and delivery data extraction).


Installation
1. Clone the Repository:


git clone https://github.com/yourusername/vt100-ssh-terminal.git

cd vt100-ssh-terminal

2. Build the Project:

Use your preferred Java build tool. For example, with Maven:

mvn clean package

Or, if you’re using Gradle:

gradle build

3. Run the Application:

After building, run the generated JAR file. For example:

java -jar target/vt100-ssh-terminal.jar

Usage
SSH Connection:
Upon startup, the application checks for an auto-connect SSH profile. If none is available, it will prompt you to select a profile for connection.

Terminal Interaction:
The emulator supports VT520-style commands and escape sequences, allowing for dynamic text rendering, cursor manipulation, and screen updates. Use keyboard shortcuts (e.g., Ctrl+C to copy, Ctrl+V to paste) as needed.

Order Processing:
The application integrates with SHD’s order management system:

It processes orders and delivery dates from provided Excel files.
The terminal dynamically adjusts its behavior based on the current screen context.
Configuration & Logging:
Logging can be enabled or disabled via the user interface, and additional configuration options are available to adjust terminal behavior.

