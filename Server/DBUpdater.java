package Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class DBUpdater implements Runnable{
     ConcurrentHashMap<String, User> DB;
     File UsersDB;
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    /**
     * thread che periodicamente aggiorna il file su disco.
     */
    public DBUpdater(ConcurrentHashMap<String, User> DB, File UsersDB){
        this.DB=DB;
        this.UsersDB=UsersDB;
    }

    @Override
    public void run(){
        try {
            while(true){
                update();
                Thread.sleep(60*1000);
            }
        } catch (InterruptedException e){
            System.out.println("Updater: Thread interrupted");

        }catch (IOException e){
            System.err.println("IOException while updating the Database");
        }
    }

    /**
     * aggiorna il file scritto su disco
     * chiamata periodica per minimizzare perdite dovute a crash
     */
    public synchronized void update()throws InterruptedException, IOException{
        //pulisce il file, necessario per via di un bug
        new FileWriter(UsersDB).close();
        //aprendo in append scrive, altrimenti no
        try(FileWriter fileWriter=new FileWriter(UsersDB, true)){

            fileWriter.write(gson.toJson(DB));
            fileWriter.close();
            System.out.println("Database updated");
        }
    }

}
