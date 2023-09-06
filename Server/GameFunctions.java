package Server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameFunctions{
    /**
     * un turno di gioco effettivo
     * confronta la parola inviata dal client con quella della partita corrente
     * comunica direttamente al client il risultato del confronto
     */
    public static boolean playTurn(DataOutputStream outToClient, String word, String correctWord) throws IOException{
        char[] w=word.toLowerCase().toCharArray();
        byte[] colors=new byte[10]; //0: verde  1: grigio  2: giallo

        //creo hashmap che mi dice quante istanze di ogni carattere ha la parola
        HashMap<Character, Integer> letterCount=new HashMap<>();
        for(int i=0; i<10; i++){ //inizializzo
            if(letterCount.putIfAbsent(correctWord.charAt(i), 1)!=null){
                letterCount.put(correctWord.charAt(i),  letterCount.get(correctWord.charAt(i))+1);
            }
        }

        boolean isEqual=true;
        for(int i=0; i<10; i++){// controllo le lettere presenti in posizione giusta
            if(w[i]!=correctWord.charAt(i)){    //carattere i non è uguale
                isEqual=false;
            }else{  //la lettera è corretta
                colors[i]=1;    //segno la lettera in posizione i è presente in posizione giusta
                letterCount.put(w[i], letterCount.get(w[i])-1); //decremento le occorrenze disponibili per i successivi controlli
            }
        }
        //controllo se carattere i è presente nella parola e non supero le occorrenze rimaste
        for(int i=0;i<10;i++){
            if(colors[i]!=1){ //se la lettera è verde non riassegno il colore
                if(letterCount.containsKey(w[i]) && letterCount.get(w[i])>0){
                    colors[i]=2; //lettera presente ma in altra posizione
                    letterCount.put(w[i], letterCount.get(w[i])-1); //decremento le occorrenze disponibili per i successivi controlli
                }else colors[i]=0; //lettera non presente
            }
        }   //  comunico al client che ha vinto
        if(isEqual) outToClient.writeUTF("equal\n");

        else{ // parola non corretta, invio anche schema di indizi
            outToClient.writeUTF("notEqual\n");
            outToClient.write(colors);
        }
        return isEqual;

    }
    /**
     * utility: controlla se la parola è nel dizionario.
     */
    public static boolean isWordValid(String w){
        return WordsManager.words.contains(w);
    }

    /**
     * invia i dati dell'utente al client.
     * Il client si occupa di generare le statistiche.
     */
    public static void sendStatistics(DataOutputStream outToClient, User usr) {
        int[] wd = usr.getWinDistribution();
        try{
            for(int e:wd) outToClient.writeInt(e);
            outToClient.writeInt(usr.getWins());
            outToClient.writeFloat(usr.getScore());
            outToClient.writeInt(usr.getCurrStreak());
            outToClient.writeInt(usr.getMaxStreak());
        } catch (IOException e){
            System.err.println("IOException while sending statistics to "+usr);
        }


    }

    /**
     * all'avvio del server imposta tutti gli utenti che possono giocare e che non hanno effettuato login
     * questo mi garantisce che anche in caso di crash effettuo un cold reboot effettivo
     */
    public static void refresh(ConcurrentHashMap<String, User> DB){
        for(Map.Entry<String, User> entry:DB.entrySet()){
            entry.getValue().setCanPlay(true);
            entry.getValue().setLoggedIn(false);
            entry.getValue().setScore();
        }
    }
}
