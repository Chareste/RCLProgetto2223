package Client;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientMulticastHandler implements Runnable{
     int multicastPort;
     String multicastHost;

    static ConcurrentLinkedQueue<String> messageCache = new ConcurrentLinkedQueue<>();
    public static final String YELLOW="\033[0;93m"; // YELLOW
    public static final String RESET="\033[0m";  // Text Reset

    public ClientMulticastHandler(int multicastPort, String multicastHost){
        this.multicastPort = multicastPort;
        this.multicastHost = multicastHost;
    }
    /**
     * Thread che resta in attesa di pacchetti sull'indirizzo multicast assrgnato.
     * Quando riceve messaggi li immagazzina nella messageCache.
     */
    @Override
    public void run(){
        //inizializzazione
        try(MulticastSocket ms = new MulticastSocket(multicastPort)){
            InetSocketAddress group = new InetSocketAddress(InetAddress.getByName(multicastHost), multicastPort);
            ms.joinGroup(group.getAddress());

            //System.out.println(Thread.currentThread().getId()+": connected to multicast address "+multicastHost);
            while(true){
                byte[] buf = new byte[1024];
                DatagramPacket dp = new DatagramPacket(buf, 0,buf.length);
                ms.receive(dp);
                String res = new String(dp.getData()).trim();
                messageCache.add(res);
            }
        } catch (UnknownHostException e){
            System.err.println(multicastHost+": unknown host");
        } catch (IOException e){
            System.err.println("IOException: "+e);
            e.printStackTrace();
        }
    }
    /**
     * funzione che si occupa di stampare tutti i messaggi ricevuti.
     * Se non ne ho lo comunico al client, altrimenti li rimuovo e li stampo uno per volta.
     */
    public static void publishAll(){
        if (messageCache.size() == 0){ //se la cache è vuota non ho messaggi da leggere
            System.out.println(YELLOW+"You don't have any new messages."+RESET);
            return;

        }
        //prendo l'elemento in testa (il meno recente) e lo invio finché non li ho mandati tutti
        while(messageCache.size()>0)
            System.out.println(messageCache.poll());
        //ho mandato tutti i messaggi, finisco notificando il client
        System.out.println(YELLOW+"All messages posted!"+RESET);
    }
}

