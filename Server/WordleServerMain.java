package Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.*;

public class WordleServerMain{
    static ConcurrentHashMap<String, User> DB;
    static HashSet<String> words = new HashSet<>();
    static Properties properties;
    static int port;
    static String address;
    static int regPort;
    static int multicastPort;
    static String multicastHost;
    static int timeInSeconds;
    static MulticastSocket multicastSocket;


    public static void main(String[] args) throws IOException, URISyntaxException{
        //imposto proprietà di default e carico server.properties
        Locale.setDefault(new Locale("en", "US"));
        loadProperties();
       //setup tcp
        InetAddress address = InetAddress.getByName(WordleServerMain.address);
        ServerSocket server = new ServerSocket(port,1000, address);
        //setup multicast
        multicastSocket= new MulticastSocket(multicastPort);
        multicastSocket.joinGroup(InetAddress.getByName(multicastHost));
        multicastSocket.setReuseAddress(true);

        //caricamento database da file

        File DBFile = new File("src/UserDB.json");

        try{
            userLoader(DBFile);
            GameFunctions.refresh(DB);
        } catch (IOException e){
            System.err.println("Error while loading Database");
        }
        //setup rmi lato server
        RMIServer usr = new RMIServer(DB);
        LocateRegistry.createRegistry(regPort);

        Registry r = LocateRegistry.getRegistry(regPort);
        r.rebind("WordleServerRMI", usr);

        Leaderboard.loadLB();   //inizializzazione della leaderboard

        //genera una parola ogni timeInSeconds (parametro configurabile) secondi
        ScheduledExecutorService wordGeneration = Executors.newScheduledThreadPool(1);
        wordGeneration.scheduleAtFixedRate(new WordsManager(words, DB), 0,timeInSeconds, TimeUnit.SECONDS);

        //lancio thread updater del file
        DBUpdater upd = new DBUpdater(DB, DBFile);
        Thread updater = new Thread(upd);
        updater.start();

        //avvio threadpool di gestori client
        ExecutorService thPool = Executors.newCachedThreadPool();
        //creo il listener per il SIGINT
        Runtime.getRuntime().addShutdownHook(new ShutdownListener(upd));

        System.out.println("Server online"); //operazioni di avvio completate, posso restare in attesa di connessioni

        while(true){ //connessioni ai client
            Socket connectionSocket=server.accept();
            thPool.execute(new ServerTask(DB, connectionSocket));
        }
    }
    /**
     * carica gli utenti da file Json nella ConcurrentHashMap
     */
    public static void userLoader(File DBFile) throws IOException{

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (BufferedReader file = new BufferedReader(new FileReader(DBFile))){
            Type usermap = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
            ConcurrentHashMap<String, User> DBTemp;
            DBTemp = gson.fromJson(new JsonReader(file), usermap);
            if(DBTemp!=null) DB=DBTemp; //se il file non è vuoto lo carico sul db
        } catch (FileNotFoundException e){
            System.err.println("File not found while loading data");
        }
    }
    /**
     * carica le proprietà del server da file properties
     */
    public static void loadProperties() throws IOException{
        properties = new Properties();
        try(FileInputStream f = new  FileInputStream("src/server.properties")){

            properties.load(f);
            port=Integer.parseInt(properties.getProperty("S_PORT"));
            address=properties.getProperty("S_IP");
            multicastPort=Integer.parseInt(properties.getProperty("MC_PORT"));
            multicastHost=properties.getProperty("MC_IP");
            timeInSeconds=Integer.parseInt(properties.getProperty("WORD_LIFESPAN"));
            regPort=Integer.parseInt(properties.getProperty("REG_PORT"));

            System.out.println("Config file loaded successfully");
        }catch (NullPointerException e){
            System.err.println("Check the properties file path.");
            e.printStackTrace();
        }

    }

}
