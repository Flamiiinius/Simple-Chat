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



///////////////////////////////////////////USER/////////////////////////////////////////////////////////////////
enum State {
  INIT, OUTSIDE, INSIDE
}

class ChatUser implements Comparable<ChatUser> {
  private String nick;
  private State userState;
  private SocketChannel sc;
  private ChatRoom room;

  public ChatUser(SocketChannel sc){
    this.nick = "";
    this.userState = State.INIT;
    this.sc = sc;
    this.room = null;
  }

  @Override public int compareTo(ChatUser other) {
    return this.nick.compareTo(other.nick);
  }

  public State getState() {
    return this.userState;
  }

  public void setState(State userState){
    this.userState = userState;
  }

  public void joinRoom(ChatRoom room){
    this.room = room;
  }

  public ChatRoom getRoom(){
    return this.room;
  }

  public void leaveRoom(){
    this.room = null;
  }

  public void setNick(String nick){
    this.nick = nick;
  }

  public String getNick(){
    return this.nick;
  }

  public SocketChannel getSocketChannel(){
    return this.sc;
  }
}
/////////////////////////////////////////////////ROOM////////////////////////////////////////7
class ChatRoom {
  private String name;
  private Map<String, ChatUser> users;

  public ChatRoom(String name){
    this.name = name;
    this.users = new HashMap<String, ChatUser>();
  }

  public String getName() {
    return this.name;
  }

  public ChatUser[] getUsers(){
    return this.users.values().toArray(new ChatUser[this.users.size()]);
  }

  public void addUser (ChatUser user) {
    this.users.put(user.getNick(), user);
  }

  public void removeUser(ChatUser user) {
    this.users.remove(user.getNick());
  }
}

///////////////////////////////////////MESSAGE////////////////////////////////////////////////////////////////////
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
  static private String newnick = "NEWNICK .+";
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
          output = " Comando aceite";
        }else{
          output = " OK";
        }
        break;
      case ERROR:
        if(controlo) {
          output = " Erro encontrado: " + this.message1;
        }else {
          output = " ERROR" + this.message1;
        }
        break;
      case MESSAGE:
        if(controlo) {
          output = this.message1 + " diz: " + this.message2;
        }else {
          output = " MESSAGE " + this.message1 + " " + this.message2;
        }
        break;
      case NEWNICK:
        if(controlo) {
          output = this.message1 + " mudou o nome para " + this.message2;
        }else {
          output = " NEWNICK" + this.message1 + " " + this.message2;
        }
        break;
      case JOINED:
        if(controlo) {
          output = this.message1 + " juntou-se à sala";
        }else {
          output = " JOINED " + this.message1;
        }
        break;
      case LEFT:
        if(controlo) {
          output = this.message1 + " saiu da sala";
        }else {
          output = " LEFT " + this.message1;
        }
        break;
      case BYE:
        if(controlo) {
          output = " Desconectado";
        }else {
          output = " BYE";
        }
    }
    return output + " \n";
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

//////////////////////////////////////////SERVER//////////////////////////////////////////////////////////////
public class ChatServer
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  static private Map<SocketChannel, ChatUser> users = new HashMap<SocketChannel, ChatUser>();
  static private Map<String, ChatUser> nicks = new HashMap<String, ChatUser>();
  static private Map<String, ChatRoom> rooms = new HashMap<String, ChatRoom>();

  static private String nick = "nick .+";
  static private String join = "join .+";
  static private String leave = "leave.*";
  static private String bye = "bye.*";
  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();
  static private final CharsetEncoder encoder = charset.newEncoder();

  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
            SelectionKey.OP_ACCEPT) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );
            users.put(sc, new ChatUser(sc));
          } else if ((key.readyOps() & SelectionKey.OP_READ) ==
            SelectionKey.OP_READ) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();
                fechaCliente(sc);
              } 
            }
              catch( IOException ie ) {
                  key.cancel();
                  fechaCliente(sc);
              }
          }

        }
              keys.clear();
      } 
    }catch( IOException ie ) {
      System.err.println(ie);
    }
  }

  static private void fechaCliente(SocketChannel sc){ // fecha conexao com o cliente
    Socket s = sc.socket();
    try {
      System.out.println(" Conexão fechada com " + s);
      sc.close();
    } catch (IOException ie){
      System.err.println(ie + " Error a fechar socket " + s);
    }

    if(!users.containsKey(sc))
      return;

    ChatUser sender = users.get(sc); 
    if(sender.getState() == State.INSIDE) {
      ChatRoom room = sender.getRoom();
      room.removeUser(sender);
      ChatUser[] userList = room.getUsers();

      for(ChatUser user : userList){
        try{
          enviarMensagemLeave(user, sender.getNick());
        } catch(IOException ie){
          System.err.println(ie + " Erro a enviar mensagem de saida");
        }
      }

      if(userList.length==0)
        rooms.remove(room.getName());
    }
      nicks.remove(sender.getNick());
      users.remove(sc);
  }

  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc ) throws IOException {
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    // Decode and print the message to stdout
    String message = decoder.decode(buffer).toString().trim();
    ChatUser sender = users.get(sc);

    if(message.startsWith("/")){ //é um comando
      String escapedMessage = message.substring(1);
      String cmd = escapedMessage.trim();

      if(Pattern.matches(nick, cmd))//se o cmd a seguir ao "/" for igual a "nick"
        enviarComandoNick(sender, cmd.split(" ")[1]);
      else if(Pattern.matches(join, cmd))// se o cmd for "join"
        enviarComandoJoin(sender, cmd.split(" ")[1]);
      else if(Pattern.matches(leave, cmd))//se o cmd for "leave"
        enviarComandoLeave(sender);
      else if(Pattern.matches(bye, cmd))// se o comando for "bye"
        enviarComandoBye(sender);
      else if(cmd.startsWith("/"))//se tiver outro "/"" envia uma menssagem
        enviarMensagemSimples(sender, escapedMessage);
      else// desconhece o comando
        enviarMensagemErro(sender, " Comando desconhecido");

    }
    else // manda uma mensgaem simples para a sala de chat
      enviarMensagemSimples(sender, message);

    return true;
  }

  static private void enviarComandoNick(ChatUser sender, String nick) throws IOException { //comando enviado do cliente para o servidor
    if(nicks.containsKey(nick))
      enviarMensagemErro(sender, " Não podes usar o nome: " + nick);//mensagem de erro
    else {
      if(sender.getState() == State.INIT)
        sender.setState(State.OUTSIDE);

      if(sender.getState() == State.INSIDE){
        ChatRoom room = sender.getRoom();
        ChatUser[] userList = room.getUsers();

        for(ChatUser user : userList){
          if(user != sender){
            enviarMensagemNick(user, sender.getNick(), nick); //envia o novo nome aos outros clientes
          }
        }
      }
      nicks.remove(sender.getNick());//remove o nick anterior da lista
      nicks.put(nick, sender);//coloca no novo nick na lista
      enviarMensagemOk(sender);//envia uma mensagem de OK"200"
      sender.setNick(nick);//coloca o novo nick no cliente
    }
  }

  static private void enviarComandoJoin(ChatUser sender, String room) throws IOException { //comando enviado do cliente para o servidor para fazer join numa sala
    if(sender.getState() == State.INIT)
      enviarMensagemErro(sender, " Seleciona o nick primeiro");
    else {
      if(!rooms.containsKey(room))//se a sala nao existir
        rooms.put(room, new ChatRoom(room));//cria uma sala e coloca na lista

      ChatRoom newRoom = rooms.get(room);
      ChatUser[] userList = newRoom.getUsers();//cria uma lista com todos os clientes da sala
      newRoom.addUser(sender);//adiciona o cliente na sala

      for(ChatUser user: userList)
        enviarMensagemJoin(user, sender.getNick());//envia mensagem de join para os clientes nessa sala

      if(sender.getState() == State.INSIDE) {//se o utilizador ja tiver numa sala
        ChatRoom oldRoom = sender.getRoom(); 
        oldRoom.removeUser(sender);//remove o cliente da sala antiga
        userList = oldRoom.getUsers();

        for(ChatUser user: userList)
          enviarMensagemLeave(user, sender.getNick());//envia mensagem de leave para os clientes nessa sala
      }
      enviarMensagemOk(sender);//manda mensagem de OK
      sender.joinRoom(newRoom);//adiciona o cliente na nova sala
      sender.setState(State.INSIDE);//coloca o cliente em estado INSIDE
    }
  }

  static private void enviarComandoLeave(ChatUser sender) throws IOException {
    if(sender.getState() != State.INSIDE)//se o cliente nao estiver em sala nenhuma nao consegue dar leave
      enviarMensagemErro(sender, " Não podes fazer isso BRO, tens de estar numa sala para sair dela");
    else{
      ChatRoom room = sender.getRoom();
      room.removeUser(sender);
      ChatUser[] userList = room.getUsers();

      for(ChatUser user : userList)
        enviarMensagemLeave(user, sender.getNick());//envia mensagem de leave para os outros clientes nessa sala

      if(userList.length == 0)
        rooms.remove(room.getName());//se ao dar leave nao houver mais clientes na sala, o server apaga a sala

      enviarMensagemOk(sender);
      sender.setState(State.OUTSIDE);
    }
  }

  static private void enviarComandoBye(ChatUser sender) throws IOException {
    enviarMensagemBye(sender);
    fechaCliente(sender.getSocketChannel());
  }

  static private void enviarMensagemOk(ChatUser receiver) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.OK);
    enviarMensagem(receiver.getSocketChannel(), message);
  }

  static private void enviarMensagemErro(ChatUser receiver, String message) throws IOException{
    ChatMessage chatMessage = new ChatMessage(MessageType.ERROR, message);
    enviarMensagem(receiver.getSocketChannel(), chatMessage);
  }

  static private void enviarMensagemNick(ChatUser receiver, String oldNick, String newNick) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.NEWNICK, oldNick, newNick);
    enviarMensagem(receiver.getSocketChannel(), message);
  }

  static private void enviarMensagemJoin(ChatUser receiver, String nick) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.JOINED, nick);
    enviarMensagem(receiver.getSocketChannel(), message);
  }

  static private void enviarMensagemLeave(ChatUser user, String nick) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.LEFT, nick);
    enviarMensagem(user.getSocketChannel(), message);
  }

  static private  void enviarMensagemBye(ChatUser receiver) throws IOException {
    ChatMessage message = new ChatMessage(MessageType.BYE);
    enviarMensagem(receiver.getSocketChannel(), message);
  }

  static private void enviarMensagemSimples(ChatUser sender, String message) throws IOException {
    if(sender.getState() == State.INSIDE){
      ChatRoom senderRoom = sender.getRoom();
      ChatUser[] userList = senderRoom.getUsers();

      for(ChatUser user : userList)
        enviarMensagem2(user, sender.getNick(), message);
    }
    else
      enviarMensagemErro(sender, " Não podes fazer isso BRO, não estas numa sala");
  }


  static private void enviarMensagem2(ChatUser receiver, String sender, String message) throws IOException {
    ChatMessage chatmessage = new ChatMessage(MessageType.MESSAGE, sender, message);
    enviarMensagem(receiver.getSocketChannel(), chatmessage);
  }

  static private void enviarMensagem(SocketChannel sc, ChatMessage message) throws IOException {
    sc.write(encoder.encode(CharBuffer.wrap(message.toString())));//faz encode da mensagem
  }
}