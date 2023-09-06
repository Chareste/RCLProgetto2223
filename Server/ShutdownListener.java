package Server;

import java.io.IOException;

public class ShutdownListener extends Thread{
    DBUpdater dbUpdater;
    public ShutdownListener(DBUpdater updater){
        this.dbUpdater = updater;
    }
    /**
     * gestisce l'interruzione del server tramite SIGINT.
     * prima di chiudere si occupa di aggiornare il file di database.
     */
    @Override
    public void run(){
        try{
            System.out.println("Received signal interrupt, closing...");
            dbUpdater.update();
            Thread.sleep(1000);
            System.out.println("Goodbye.");
        } catch (IOException | InterruptedException ignored){}

    }
}
