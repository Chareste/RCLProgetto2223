package Server;

import java.io.*;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class WordsManager implements Runnable{
    public static HashSet<String> words;
    ConcurrentHashMap<String, User> DB;
    static String word = "WORDLETEMP";
    public WordsManager(HashSet<String> words, ConcurrentHashMap<String, User> DB) throws IOException, URISyntaxException{
        WordsManager.words=words;
        this.DB=DB;
        loadWords();

    }
    /**
     * genera la parola a intervalli regolari
     * l'intervallo è impostato nelle properties
     */
    @Override
    public void run(){
        try{
            word=wordGenerator();
            for(Map.Entry<String, User> entry:DB.entrySet())
                entry.getValue().setCanPlay(true);
        } catch (IOException|URISyntaxException e){
            System.err.println("An error occurred during the correctWord update.");
        }

    }

    /**
     * carica le parole da file all'interno di un HashSet
     */
    public void loadWords() throws IOException, URISyntaxException{
        try (BufferedReader file = new BufferedReader(new FileReader("src/words.txt"))){
            for(String line = file.readLine(); line != null; line=file.readLine())
                words.add(line);
        }
    }
    /**
     * generatore delle parole
     * prende una posizione random dentro il file e restituisce la nuova parola
     */
    public static String wordGenerator() throws IOException, URISyntaxException{
        try(RandomAccessFile words = new RandomAccessFile("src/words.txt","r")){
            long position =Math.abs(new Random().nextLong())% words.length(); //genero una posizione random nei bound del file
            position = position - (position % 11); //arrotondo a inizio linea
            words.seek(position);
            String word = words.readLine();
            System.out.println("Update - new current word: "+word);
            return word;

        }


    }
    public static String getWord(){
        return word;
    }
}
