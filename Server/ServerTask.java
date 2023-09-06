package Server;

import common.RMIClientIn;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.util.concurrent.ConcurrentHashMap;

public class ServerTask implements Runnable{
    ConcurrentHashMap<String, User> DB;

    Socket connectionSocket;

    /**
     * un ServerTask è l'effettiva istanza del server:
     * si occupa di mantenere la comunicazione con il client ad esso connesso
     * e gestisce tutte le richieste del client.
     */
    public ServerTask(ConcurrentHashMap<String, User> DB, Socket connectionSocket){
        this.DB=DB;
        this.connectionSocket=connectionSocket;
    }

    @Override
    public void run(){
        // avvio del thread e inizializzazione

        System.out.println("Server instance "+Thread.currentThread().getId()+
                ": connected to client "+connectionSocket.getInetAddress().getHostAddress());
        String usr = null;
        String in;
        try (DataInputStream fromClient=new DataInputStream(connectionSocket.getInputStream());
             DataOutputStream outToClient=new DataOutputStream(connectionSocket.getOutputStream())){
            /*
            setup della comunicazione RMIcallback con il client
            client invia la porta
            */
            int clientPort = fromClient.readInt();
            RMIClientIn cli=(RMIClientIn) Naming.lookup
                    ("//"+connectionSocket.getInetAddress().getHostAddress()+":"+clientPort+"/WordleClientRMI");
            cli.setLb(Leaderboard.leaderboard); //genero la leaderboard

            while(!(in=fromClient.readUTF()).equals("goodbye\n")){ //se client chiude la connessione, mi disconnetto
                /*
                Step 1: gestione login
                Quando un client si connette la prima operazione effettiva di connessione consiste nel login.
                    Da input ricevo un'unica stringa, contenente "username"+"\n"+"password.
                    Scelgo "\n" per dividere le due stringhe perchè è un escape character e non rischio di
                    trovarlo come carattere di user o password.
                */
                String[] parts=in.split("\n");
                usr=parts[0];
                String pwd=parts[1];

                //  Fase dei controlli di login.

                if(DB.containsKey(usr)){ // controlla se l'user è presente nel db
                    if(DB.get(usr).isLoggedIn()) // user è già logged su un altro client
                        outToClient.writeUTF("logged\n");
                    else if(DB.get(usr).getPassword().equals(pwd)){ //login corretto

                        DB.get(usr).setLoggedIn(true);
                        outToClient.writeUTF(DB.get(usr).getUsername()); // ritorno l'username come conferma al client
                        /*
                            a questo punto ho effettuato il login correttamente e posso iniziare la sessione.
                            Il client ora ha accesso al menù con le opzioni con cui interagire con il server.
                        */
                        session:
                        while(true){
                            in=fromClient.readUTF().trim(); //   lettura opzione da client

                            switch(in){
                                /*
                                    Inizio della partita effettiva.
                                    L'utente invia una stringa di 10 caratteri.
                                    Controllo se è una parola presente nel dizionario.
                                    Se è presente, effettuo il turno di gioco, altrimenti rispondo con uno specifico messaggio.
                                    L'utente ha 12 tentativi durante i quali indovinare la parola,
                                    se arrivano al termine la partita è considerata persa.
                                 */
                                case "playGame":
                                    String correctWord=WordsManager.getWord();
                                    //prendo la parola corrente, che resta costante per tutta la partita
                                    if(!DB.get(usr).canPlay())
                                        outToClient.writeUTF("cantPlay\n");
                                    else{
                                        outToClient.writeUTF("canPlay\n");
                                        DB.get(usr).setCanPlay(false);
                                        int i;
                                        try{
                                            for(i=12; i>0; ){ // loop di gioco
                                                String word=fromClient.readUTF().trim();
                                                if(GameFunctions.isWordValid(word)){ // controllo parola
                                                    if(GameFunctions.playTurn(outToClient, word, correctWord)) //  turno di gioco
                                                        break; //   se ho vinto esco dal loop di gioco
                                                    i--;
                                                }else{ //   parola non nel dizionario
                                                    outToClient.writeUTF("notValid\n");
                                                }
                                            }
                                            /*
                                            se il client viene chiuso durante una partita,
                                            imposto i tentativi rimasti a 0 e gestisco come se fosse una partita persa.
                                            */
                                        }catch (EOFException e){
                                            System.err.println("Unexpected disconnection detected during match, " +
                                                    "setting the current match as lost.");
                                            i=0;
                                        }
                                        // fine partita, effettuo operazioni di update del punteggio e statistiche
                                        i--;
                                        DB.get(usr).addMatch(i);
                                        DB.get(usr).setScore();
                                        String matchState = "guessed";

                                        // se ho avuto aggiornamento della leaderboard mando callback RMI al client
                                        if(Leaderboard.updateLB(DB.get(usr)))
                                            cli.notifyEvent(Leaderboard.leaderboard);

                                        if(i<0){//se ho perso, imposto la streak corrente a 0
                                            DB.get(usr).setCurrStreak(0);
                                            outToClient.writeUTF(correctWord);
                                            matchState = "not guessed";

                                        }else{//altrimenti la incremento di 1
                                            DB.get(usr).setCurrStreak(DB.get(usr).getCurrStreak()+1);
                                            //controllo se la streak corrente è maggiore della massima e in caso aggiorno
                                            if(DB.get(usr).getCurrStreak()>DB.get(usr).getMaxStreak())
                                                DB.get(usr).setMaxStreak(DB.get(usr).getCurrStreak());
                                        }
                                        //a questo punto il client può decidere di condividere sul multicast
                                        // i risultati della sua partita.
                                        if(fromClient.readUTF().equals("share\n")){
                                            //genero la stringa correttamente a seconda di vittoria/sconfitta
                                            StringBuilder msg =new StringBuilder((usr+" has "+matchState+" the word "+correctWord.toUpperCase()));
                                            if(matchState.equals("guessed"))
                                                msg.append(" in ").append(12-i).append(" tries!\n");
                                            else msg.append(".\n");
                                            //creo il datagramma vero e proprio e lo invio
                                            byte[] data= msg.toString().getBytes();
                                            DatagramPacket dp = new DatagramPacket(data, data.length,
                                                    InetAddress.getByName(WordleServerMain.multicastHost), WordleServerMain.multicastPort);
                                            WordleServerMain.multicastSocket.send(dp);
                                            //invio messaggio di conferma
                                            outToClient.writeUTF("done\n");
                                        }
                                    }
                                    break;

                                case "sendStatistics": //Invio delle statistiche di gioco
                                    GameFunctions.sendStatistics(outToClient,DB.get(usr));
                                    break;
                                    /*
                                    Logout della sessione
                                    Memorizzo che l'utente non è più loggato ed esco dalla sessione.
                                     */
                                case "logout":
                                    DB.get(usr).setLoggedIn(false);
                                    outToClient.writeUTF("completed\n");
                                    break session;
                                default:
                                    System.err.println("Error: unreachable");
                                    break;
                            }

                        }
                    } //password errata
                    else outToClient.writeUTF("password\n");
                }
                //L'utente non è nel DB
                else outToClient.writeUTF("incorrect\n");

            }
            /*
            il client ha richiesto la disconnessione, chiudo quindi la socket
            */
            System.out.println("Server instance "+Thread.currentThread().getId()+
                    ": connection ended with "+connectionSocket.getInetAddress().getHostAddress());
            connectionSocket.close();
        }

        //Nel caso il client non viene chiuso correttamente effettuo la disconnessione e stampo un messaggio di errore.
         catch (IOException e){
            System.err.println("Client "+connectionSocket.getInetAddress().getHostAddress()+" has closed the connection unexpectedly.");
            if(usr!=null) //se un utente è collegato effettuo il logout
                DB.get(usr).setLoggedIn(false);
            try{ connectionSocket.close();} catch (IOException ignored){}
        } catch (NotBoundException ignored){}
    }

}
