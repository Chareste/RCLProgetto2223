package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RMIServerIn extends Remote{
    public String register(String usr, String pwd)throws RemoteException;

    //public void notifyLeaderboard();
}
