package Server;

import common.RMIServerIn;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

public class RMIServer extends UnicastRemoteObject implements RMIServerIn{
    ConcurrentHashMap<String, User> DB;
    public RMIServer(ConcurrentHashMap<String, User> DB) throws RemoteException{
        this.DB = DB;

    }
    /**
     * funzione di registrazione
     * riceve username e password ed effettua il controllo se usr è già presente
     */
    @Override
    public String register(String usr, String pwd)throws RemoteException{
        User user = new User(usr,pwd);

        if(pwd.isEmpty() || usr.isEmpty()) //se uno dei campi è vuoti restituisce messaggio di errore
            return "Correct usage: username >enter password >enter";

        //controlla se l'utente esiste, in caso non ritorna null usr era già nel DB
        if(DB.putIfAbsent(usr, user) != null)
            return "User already exists";
        return "Operation complete";
    }
}
