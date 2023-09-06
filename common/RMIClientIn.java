package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RMIClientIn extends Remote{
    public void notifyEvent(String[][]lb) throws RemoteException;
    public void setLb(String[][] lb)throws RemoteException;

}
