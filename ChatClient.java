import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.channels.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.regex.*;



enum MessageType {
    OK, ERROR, MESSAGE, NEWNICK, JOINED, LEFT, BYE
}

class ChatMessage {
    private MessageType type;
    private String message1;
    private String message2;

    static private String ok = "OK";
    static private String error = "ERROR";
    static private String message = "MESSAGE .+ .+";
    static private String newnick = "NEWNICK .+ .+";
    static private String joined = "JOINED .+";
    static private String left = "LEFT .+";
    static private String bye = "BYE";

    public ChatMessage(MessageType type){
        this.type = type;
        message1 = "";
        message2 = "";
    }

    public ChatMessage(MessageType type, String message1){
        this.type = type;
        this.message1 = message1;
        message2 = "";
    }

    public ChatMessage(MessageType type, String message1, String message2){
        this.type = type;
        this.message1 = message1;
        this.message2 = message2;
    }

    public MessageType getType(){
        return this.type;
    }

    public String toString() {
        return this.toString(false);
    }

    public String toString(boolean controlo) {

        String output = "";

        switch(this.type) {
            case OK:
                if(controlo) {
                    output = "Command successful";
                }else{
                    output = "OK";
                }
                break;
            case ERROR:
                if(controlo) {
                    output = "Error found: " + this.message1;
                }else {
                    output = "ERROR" + this.message1;
                }
                break;
            case MESSAGE:
                if(controlo) {
                    output = this.message1 + " says: " + this.message2;
                }else {
                    output = "MESSAGE " + this.message1 + " " + this.message2;
                }
                break;
            case NEWNICK:
                if(controlo) {
                    output = this.message1 + " changed nick to " + this.message2;
                }else {
                    output = "NEWNICK" + this.message1 + " " + this.message2;
                }
                break;
            case JOINED:
                if(controlo) {
                    output = this.message1 + " has joined the room";
                }else {
                    output = "JOINED " + this.message1;
                }
                break;
            case LEFT:
                if(controlo) {
                    output = this.message1 + " has left the room";
                }else {
                    output = "LEFT " + this.message1;
                }
                break;
            case BYE:
                if(controlo) {
                    output = "Disconnected";
                }else {
                    output = "BYE";
                }

                // falta o private????
        }
        return output + "\n";
    }



    public static ChatMessage parseString(String s) {
        MessageType type;
        String message1 = "";
        String message2 = "";

        String [] parts = s.split(" "); // separa por espaços

        if(Pattern.matches(ok, s)){
            type = MessageType.OK;
        }
        else if(parts[0].equals("ERROR")) {
            type = MessageType.ERROR;
            String finalMessage = "";

            for(int i = 1; i < parts.length; i++){
                if(i > 1){
                    finalMessage += " ";
                }
                finalMessage += parts[i];
            }

            message1 = finalMessage;
        }
        else if(Pattern.matches(message, s)) {
            type = MessageType.MESSAGE;
            message1 = s.split(" ")[1];
            int position = s.substring(7).indexOf(message1);
            message2 = s.substring(7 + position + message1.length());
        }
        else if(Pattern.matches(newnick, s)) {
            type = MessageType.NEWNICK;
            message1 = s.split(" ")[1];
            message2 = s.split(" ")[2];
        }
        else if(Pattern.matches(joined, s)) {
            type = MessageType.JOINED;
            message1 = s.split(" ")[1];
        }
        else if(Pattern.matches(left, s)) {
            type = MessageType.LEFT;
            message1 = s.split(" ")[1];
        }
        else if(Pattern.matches(bye, s)) {
            type = MessageType.BYE;
        }
        else {
            type = MessageType.ERROR;
            String finalMessage = "";

            for(int i = 1; i < parts.length; i++){
                if(i > 1){
                    finalMessage += " ";
                }
                finalMessage += parts[i];
            }

            message1 = finalMessage;
        }

        return new ChatMessage(type, message1, message2);
    }
}


//////////////////////////////////////CLIENT/////////////////////////////////////////////////
public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui

    boolean finish = false; // variavel de controlo

    private SocketChannel clientSocket;
    private BufferedReader input;

    //decoder para "descodificar" a mensagem
    //encoder para "codificar" a mensagem
    private Charset charset = Charset.forName("UTF8");
    private CharsetDecoder decoder = charset.newDecoder();
    private CharsetEncoder encoder = charset.newEncoder();
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                   chatBox.setText("");
                }
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        try {
            clientSocket = SocketChannel.open();
            clientSocket.configureBlocking(true);
            clientSocket.connect(new InetSocketAddress(server, port));
        }   catch(IOException ex) {
        }

    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor

        clientSocket.write(encoder.encode(CharBuffer.wrap(message)));
    }

    public void printChatMessage(ChatMessage message) {
        printMessage(message.toString(true));
    }
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
        try {
            while (!clientSocket.finishConnect()) { 
            }
        }   catch (Exception ce) {
            System.err.println("Can't connect");
            System.exit(0);
            return;
        }

        input = new BufferedReader(new InputStreamReader(clientSocket.socket().getInputStream()));

        while (true) {
            String message = input.readLine();

            if(message == null){
                break;
            }

            message = message.trim();

            printChatMessage(ChatMessage.parseString(message));
        }

        clientSocket.close();

        try {
            Thread.sleep(74);
        } catch (InterruptedException ie) {

        }

        finish = true;
    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
