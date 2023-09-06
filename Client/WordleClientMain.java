package Client;

import com.google.gson.stream.JsonReader;
import common.RMIServerIn;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;


public class WordleClientMain{
    static Scanner in=new Scanner(System.in);
    static Properties properties;
    static int serverPort;
    static String serverIP;
    static int multicastPort;
    static String multicastHost;
    static ConcurrentLinkedQueue<String[][]> list = new ConcurrentLinkedQueue<>();

    public static final String GRAY="\033[0;90m";  // GRAY
    public static final String GREEN="\033[0;92m";  // GREEN
    public static final String YELLOW="\033[0;93m"; // YELLOW
    public static final String CYAN="\033[0;96m"; // CYAN
    public static final String RED="\033[0;91m";  // RED
    public static final String RESET="\033[0m";  // Text Reset
    public static Clip clip=null;

    public static void main(String[] args){
        Locale.setDefault(new Locale("en", "US"));
        loadProperties();

        //apro socket e inizializzo le stream
        try (Socket client=new Socket(serverIP, serverPort)){
            RMIServerIn user=(RMIServerIn) Naming.lookup("//"+serverIP+"/WordleServerRMI");

            DataInputStream fromServer=new DataInputStream(client.getInputStream());
            DataOutputStream toServer=new DataOutputStream(client.getOutputStream());

            /*
            setup RMI lato client
            genero la porta casualmente tra 10000 e 65535
            e la comunico al server in TCP
            */
            RMIClient cli = new RMIClient(list);
            int regPort =10000+Math.abs(new Random().nextInt(55535));
            LocateRegistry.createRegistry(regPort);
            Registry re = LocateRegistry.getRegistry(regPort);
            re.rebind("WordleClientRMI", cli);
            toServer.writeInt(regPort);


            //inizializzo thread per ricezione messaggi multicast
            Thread multicastHandler=new Thread(new ClientMulticastHandler(multicastPort, multicastHost));
            multicastHandler.start();
            Thread.sleep(300);

            while(true){ //menu iniziale: registrati, effettua login o esci
                System.out.println("Choose an option: 1. register    2. login    0. exit");

                try{
                    int opt=Integer.parseInt(in.nextLine());

                    if(opt==1){ //opzione di registrazione (non effettua login automaticamente)
                        System.out.println("Insert Username: ");
                        String usr=in.nextLine().trim();
                        System.out.println("Insert Password: ");
                        String pwd=in.nextLine().trim();
                        System.out.println(user.register(usr, pwd));
                    }
                    else if(opt==2){ //login nel server
                        String usr=login(fromServer, toServer);

                        switch(usr){
                            case "incorrect\n": //user non esiste
                                System.out.println(RED+"The user does not exist. Maybe you wanted to register?"+RESET);
                                break;
                            case "password\n": //password errata, user esistente
                                System.out.println(RED+"Wrong password. Try again."+RESET);
                                break;
                            case "error\n":  //errore nel login
                                System.out.println(RED+"An error occurred while trying to log in, try again"+RESET);
                                break;
                            case "format\n": //formato di username e password errato
                                System.out.println(RED+"Correct usage: username >enter password >enter"+RESET);
                                break;
                            case "logged\n": //user già loggato su altro client
                                System.out.println(YELLOW+"You are already logged in. Try logging out from the other client first."+RESET);
                                break;
                            default:  //avvio dell'applicazione wordle
                                System.out.println("Loading game...");
                                game:
                                while(true){
                                    //alcune opzioni non spengono la musica del menu quindi in caso salto il blocco
                                    if(clip==null || !clip.isActive()){
                                        try{ //avvio la musica del menu se è presente
                                            clip=AudioSystem.getClip();
                                            clip.open(AudioSystem.getAudioInputStream(WordleClientMain.class.getResource("music/idle.wav")));
                                            clip.start();
                                            clip.loop(Clip.LOOP_CONTINUOUSLY);
                                        } catch (Exception ignored){}//se non carica la traccia funziona senza traccia
                                    } //stampo il menu
                                    Thread.sleep(300);
                                    System.out.println("Hello "+usr+", welcome to "+GREEN+"10WORDLE"+RESET+"! What do you want to do today?\n"+
                                            YELLOW+"1. Play game   2. Show statistics   3. See multicast shared messages\n"+
                                            "4. See leaderboard   0. Logout"+RESET);
                                    try{
                                        opt=Integer.parseInt(in.nextLine());
                                        switch(opt){
                                            case 1:  //gioco
                                                toServer.writeUTF("playGame");
                                                String r=fromServer.readUTF();
                                                if(r.equals("cantPlay\n")){ // già fatto tentativo per la parola corrente
                                                    System.out.println(RED+"You've already tried to guess the current word, try again later"+RESET);
                                                }else if(r.equals("canPlay\n")){
                                                    playWordle(fromServer, toServer);
                                                }else{
                                                    System.out.println("An error occurred while starting the game.");
                                                }
                                                break;
                                            case 2:  //visualizzare le statistiche di gioco
                                                toServer.writeUTF("sendStatistics");
                                                System.out.println(CYAN+"Getting the game statistics for "+usr+"..."+RESET);
                                                showMyStatistics(fromServer);
                                                //show stats
                                                break;
                                            case 3: //stampo i messaggi ricevuti sul multicast
                                                ClientMulticastHandler.publishAll();
                                                break;
                                            case 4:  //stampo la leaderboard
                                                printLb();
                                                break;
                                            case 0:  //logout
                                                toServer.writeUTF("logout");
                                                try{ // spengo la musica e faccio il jingle del logout
                                                        clip.close();
                                                        clip.open(AudioSystem.getAudioInputStream(WordleClientMain.class.getResource("music/SE.wav")));
                                                        clip.start();

                                                } catch (Exception ignored){} //se non carica la traccia funziona senza traccia

                                                if(fromServer.readUTF().equals("completed\n")){
                                                    System.out.println(GREEN+"You logged out from "+usr+". See you next time!"+RESET);
                                                    Thread.sleep(clip.getMicrosecondLength()/1000-1000);
                                                    break game;
                                                }else{
                                                    System.out.println(RED+"There was a problem with logout, try again"+RESET);
                                                }
                                                break;
                                            default: //opzione non riconosciuta, reindirizza al caso di errore
                                                throw new NumberFormatException();
                                        }
                                    } catch (NumberFormatException e){
                                        System.err.println("Insert a valid option.");
                                    }
                                }
                                break;
                        }
                    }else if(opt==0){ //chiudo l'applicazione
                        System.out.println("Closing...");
                        toServer.writeUTF("goodbye\n");
                        break;
                    }else  //opzione non riconosciuta, reindirizza al caso di errore
                        throw new NumberFormatException();
                } catch (NumberFormatException e){
                    System.err.println("Insert a valid option");
                }
            }
            System.exit(0);
        } catch (ConnectException e){
            System.err.println("The server has closed the connection unexpectedly.");

        } catch (IOException | InterruptedException | NotBoundException e){
            e.printStackTrace();
        }
        System.exit(1);
    }

    /**
     * funzione di login
     * richiede inserimento di username e password
     * comunica al main un messaggio di errore o il messaggio di conferma, direttamente dal server
     */
    public static String login(DataInputStream fromServer, DataOutputStream toServer){
        //richiesta dati all'utente
        System.out.println("Insert Username: ");
        String usr=in.nextLine().trim();
        System.out.println("Insert Password: ");
        String pwd=in.nextLine().trim();

        //se uno dei campi è vuoti restituisce messaggio di errore senza passare dal server
        if(pwd.isEmpty() || usr.isEmpty())
            return "format\n";
        try{
            toServer.writeUTF(usr+"\n"+pwd);
            usr=fromServer.readUTF();
        } catch (IOException e){
            e.printStackTrace();
        }
        return usr;
    }

    //caricamento del file properties relativo al client
    public static void loadProperties(){
        try (FileInputStream f=new FileInputStream("src/client.properties")){
            properties=new Properties();
            properties.load(f);
            serverPort=Integer.parseInt(properties.getProperty("S_PORT"));
            serverIP=properties.getProperty("S_IP");
            multicastHost=properties.getProperty("MC_IP");
            multicastPort=Integer.parseInt(properties.getProperty("MC_PORT"));


        } catch (NullPointerException e){
            System.err.println("Check the properties file path.");
            e.printStackTrace();
        }
        catch (IOException e){
            System.err.println("IOException in loading properties");
            e.printStackTrace();
        }
    }
    /**
     * gestisce la partita effettiva.
     */
    public static void playWordle(DataInputStream fromServer, DataOutputStream toServer) throws IOException, InterruptedException{

        try{ //carico la musica di gioco se disponibile
            clip.close();
            clip=AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(WordleClientMain.class.getResource("music/game.wav")));

            clip.start();
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception ignored){

        }//se non carica la traccia funziona senza traccia


        System.out.println(YELLOW+"New game of"+GREEN+" 10WORDLE "+YELLOW+"started! Try to guess the current word.\n"+
                "Do you need to read the rules? Y/N"+RESET);
        //richiedo all'utente se vuole stampare le regole
        String r=in.nextLine();
        while(!r.matches("[yYnN]")){
            System.out.println(RED+"Insert a valid option."+RESET);
            r=in.nextLine();
        }
        if(r.matches("[yY]"))
            printRules();

        boolean wonGame=false;
        //il loop di gioco effettivo
        gameLoop:
        for(int tries=12; tries>0; ){
            /*
            richiedo all'utente l'input di una parola di 10 lettere
            nota: per parola intendo una stringa. Se contiene simboli che non sono lettere,
             la stringa non è nel dizionario. Non è rilevante distinguere i due casi.
            */
            System.out.println(CYAN+"Input your guess. Tries left: "+tries+RESET);
            String word=in.nextLine().trim();
            if(word.length()!=10) //non ho un input di 10 lettere, richiedo un altro input
                System.out.println(RED+"Please input a 10 letter long word (does not count as a try)."+RESET);
            else{//parola da 10 lettere, invio per controllo
                toServer.writeUTF(word);
                r=fromServer.readUTF();

                switch(r){
                    case "notValid\n": //parola non nel dizionario
                        System.out.println(RED+"Please input a valid word (does not count as a try)."+RESET);
                        break;
                    case "notEqual\n": //non è la parola corrente
                        tries--;
                        byte[] colors=new byte[10];
                        fromServer.read(colors); //ricevo dal server un byteArray con i colori per ogni carattere
                        System.out.println("Word not correct. Here is how you did:");
                        String res=formatResult(word, colors); //elaboro il bytearray e ricevo una stringa formattata
                        System.out.println(res);
                        break;
                    case "equal\n": //è la parola corrente: ho vinto.
                        try{ // riproduco jingle di vittoria
                            clip.close();
                            clip.open(AudioSystem.getAudioInputStream(WordleClientMain.class.getResource("music/win.wav")));
                            clip.start();
                        } catch (Exception ignored){}//se non carica la traccia funziona senza traccia
                        tries--;
                        wonGame=true;
                        System.out.println("Correct!\nYou won the game guessing the word \""
                                +GREEN+word.toUpperCase()+RESET+"\" in "+(12-tries)+" tries.");
                        //recupero la traduzione della parola del giorno
                        getTranslation(word);
                        Thread.sleep(1000);
                        break gameLoop;
                }
            }
        }//a questo punto o ho vinto o ho finito i tentativi

        if(!wonGame){// ho perso
            try{ //riproduco il jingle di sconfitta
                clip.close();
                clip.open(AudioSystem.getAudioInputStream(WordleClientMain.class.getResource("music/lose.wav")));
                clip.start();
            } catch (Exception ignored){}//se non carica la traccia funziona senza traccia

            //ricevo dal server la parola corrente e ne stampo la traduzione
            String correctWord = fromServer.readUTF();
            System.out.println("You've used all your tries. the word was: "+GREEN +correctWord+RESET);
            getTranslation(correctWord);

            Thread.sleep(1000);
        }
        //  opzione di condividere messaggi sul multicast
        System.out.println("Do you want to share your results? Y/N");
        String res = in.nextLine();
        while(!res.matches("[yYnN]")){
            System.out.println(RED+"Please input a valid option."+RESET);
            res=in.nextLine();
        }
        //se invio al server che voglio condividere, server manda messaggio sul multicast
        if(res.matches("[yY]")){
            toServer.writeUTF("share\n");
            if(fromServer.readUTF().equals("done\n"))
                System.out.println("The result was successfully shared!");
            else
                System.out.println("An error occurred while trying to share your result.\n" +
                    "The result of your match was not shared.");
        }
        else{ //altrimenti termino
            toServer.writeUTF("notShare\n");
            System.out.println("You decided to not share this match's results.");
        }

        try{   clip.close();
        } catch (Exception ignored){}
    }
    //stampa le regole su richiesta dell'utente
    public static void printRules() throws InterruptedException{
        System.out.println("How to play:\n"+
                "Guess the 10Wordle in 12 tries!\n"+
                "Each guess must be a valid 10-letter word.\n"+
                "The color of the letters will change to show\nhow close your guess was to the word.\n"+
                GREEN+"Green means the letter is in the word and in the correct spot.\n"+
                YELLOW+"Yellow means it's in the word but in the wrong spot.\n"+
                GRAY+"And lastly, gray means it's not in the word in any spot.");
        Thread.sleep(500);
        System.out.println(RESET+"Now you're ready to start playing!");

    }

    //formatta il testo che restituisce a schermo con i colori per gli indizi
    public static String formatResult(String w, byte[] colors){

        StringBuilder str=new StringBuilder("");

        for(int i=0; i<10; i++){
            switch(colors[i]){ //1: verde //0: grigio 2: giallo
                case 1:
                    str.append(GREEN).append(w.charAt(i)).append(RESET);
                    break;
                case 0:
                    str.append(GRAY).append(w.charAt(i)).append(RESET);
                    break;
                case 2:
                    str.append(YELLOW).append(w.charAt(i)).append(RESET);
                    break;
            }
        }
        return str.toString();
    }

    //funzione che elabora le statistiche con i dati inviati dal server
    public static void showMyStatistics(DataInputStream fromServer){
        try{
            int[] stats=new int[13];
            for(int i=0; i<13; i++) stats[i]=fromServer.readInt();
            int wins=fromServer.readInt();
            /*
            stampa molte informazioni: totale partite, vittorie, sconfitte, percentuale di vittorie,
             punteggio, la winstreak attuale e la massima raggiunta dall'utente, la distribuzione di vittorie per tentativi
            */
            System.out.println("Total matches: "+(wins+stats[0])+
                    "    Wins: "+wins+"    Losses: "+stats[0]+
                    "\nWin percentage: "+(String.format("%.02f", (float) wins/(wins+stats[0])*100)+"%"));
            System.out.println("Current score: "+String.format("%.02f",fromServer.readFloat())+
                    "\nCurrent win streak: "+fromServer.readInt()+"   Max win streak: "+fromServer.readInt());
            System.out.println(CYAN+"Win distribution for number of tries:"+RESET);
            for(int i=1; i<13; i++){
                System.out.print(i+": "+stats[i]+"   ");
                if(i%6==0) System.out.print("\n");
            }
            //Genera un grafico a barre di distribuzione delle vittorie
            System.out.println(CYAN+"\nGenerating win distribution graph..."+RESET);
            for(int i=1; i<13; i++){
                System.out.print(i+": ");
                float perc=(float) stats[i]/wins*100;
                System.out.print(GREEN);
                for(int t=0; t<perc; t++) System.out.print("#");
                System.out.print(RESET+"\n");
            }
            System.out.println(GREEN+"Done!"+RESET);

        } catch (IOException e){
            e.printStackTrace();
        }

    }

    //stampa la leaderboard attuale (prime 3 posizioni)
    public static void printLb(){
        String[][] lb = list.peek();
        System.out.println(CYAN+"Here is the current top three:"+RESET);
        for(int i=0;i<3;i++){
            System.out.println("Position "+(1+i)+" -"+YELLOW+" User: "+lb[i][0]+
                    "  Score: "+lb[i][1]+RESET);
        }
    }

    //ottiene la traduzione della parola dall'API come da consegna
    public static void getTranslation(String word){
        try{
            URLConnection connection=new URL
                    ("https://api.mymemory.translated.net/get"+"?q="+word+"&langpair=en|it").openConnection();
            connection.connect();
            //api restituisce un json contenente un oggetto
            try (JsonReader reader = new JsonReader(
                    new BufferedReader(new InputStreamReader(connection.getInputStream())))){
                String translation=null;
                //inizio oggetto generale
                reader.beginObject();
                while(reader.hasNext()){
                    String name=reader.nextName();
                    //campo responseData contiene un altro oggetto
                    if(name.equals("responseData")){
                        reader.beginObject();
                        while(reader.hasNext()){
                            name=reader.nextName();
                            //mi interessa il campo translatedText
                            if(name.equals("translatedText"))
                                translation=reader.nextString();
                            else//salto gli altri valori di responseData
                                reader.skipValue();
                        } reader.endObject();
                    }else//salto gli altri valori dell'oggetto generale
                        reader.skipValue();
                } reader.endObject();

                //stampo la traduzione recuperata dall'API
                System.out.println("Here's the italian translation of the current word: "+CYAN+translation+RESET);
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}