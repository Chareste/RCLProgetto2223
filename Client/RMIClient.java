package Client;

import common.RMIClientIn;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RMIClient extends UnicastRemoteObject implements RMIClientIn{
    ConcurrentLinkedQueue<String[][]> board;
    static String MAGENTA = ("\033[0;95m"); // MAGENTA
    static String RESET = ("\033[0m"); // RESET
    public RMIClient(ConcurrentLinkedQueue<String[][]> board) throws RemoteException{
        this.board=board;
    }
    /**
     * All'avvio della comunicazione ricevo la leaderboard corrente.
     */
    public void setLb(String[][] lb){
        board.add(lb);
    }
    /**
     * ogni volte che c'è un update nella lb stampo un messaggio nella CLI
     * e aggiorno la leaderboard in memoria.
     */
    public void notifyEvent(String[][]lb) throws RemoteException{
        System.out.println(MAGENTA+"The leaderboard has been updated! Go see the new leaders and their scores."+RESET);
        board.poll();
        board.add(lb);
    }
}

