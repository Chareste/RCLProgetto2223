package Server;

import java.util.Map;

public class Leaderboard{
    /**
     * più thread possono accedere alla leaderboard in contemporanea.
     * siccome una matrice non ha gestione della concorrenza,
     * accedo alla leaderboard solo con metodi synchronized.
     */
    static String[][] leaderboard = new String[3][2];
    /**
     * si occupa di caricare i punteggi attuali degli utenti e creare la leaderboard
     * chiamata all'avvio del server
     */
    public static synchronized void loadLB(){
        for(int i=0;i<=2;i++){ //inizializzo array dei leader
            leaderboard[i][0]="";
            leaderboard[i][1]="-1";
        }   //scorre tutta la map e confronta con la leaderboard in memoria
        for(Map.Entry<String, User> entry:WordleServerMain.DB.entrySet()){
            String usr = entry.getValue().getUsername();
            String score = String.format("%.02f",entry.getValue().getScore());

            //se il punteggio dell'utente corrente è maggiore del terzo corrente, aggiorno la classifica.
            if(Float.parseFloat(score)
                    > Float.parseFloat(leaderboard[2][1]))
                cascadeUpdate(usr,score);
        }
    }
    /**
     * quando si conclude una partita e c'è l'aggiornamento del punteggio,
     * provo ad aggiornare la leaderboard e invio
     */
    public static synchronized boolean updateLB(User user){
        String usr = user.getUsername();
        String score = String.format("%.02f",user.getScore());
        boolean isLower =Float.parseFloat(score)
                <= Float.parseFloat(leaderboard[2][1]);

        /*
        controllo se l'utente è già in classifica
        a seconda se ha un punteggio minore dell'ultimo decido se
        rigenerare la classifica o eliminare il record corrente e effettuare inserimento
        */
        for(int i=0;i<3;i++){
            if(leaderboard[i][0].equals(usr)){
                if(isLower)
                    loadLB();
                else{
                    leaderboard[i][0]= "";
                    leaderboard[i][1]="-1";
                    cascadeUpdate(usr,score);
                }
                return true;
            }
        }
        /*
            a questo punto usr non è già in classifica.
            se il punteggio è minore o uguale del terzo non effettuo aggiornamento (caso più comune),
            altrimenti effettuo aggiornamento a cascata.
            rule: se il punteggio è uguale ha più diritto a restare in posizione chi c'è da più tempo
        */
        if(isLower)
            return false;
        cascadeUpdate(usr,score);
        return true;
    }
    /**
     * aggiornamento effettivo della classifica a cascata.
     * Controllo per ogni posizione dalla testa se il nuovo utente ha una score maggiore del corrente.
     * in caso affermativo scambio con user i dati in quella posizione e continuo il confronto
     * con le posizioni successive della classifica.
     * Effettuerà una sostituzione a catena scartando il terzo.
     */
    public static synchronized void cascadeUpdate(String usr, String score){
        for(int i=0; i<=2;i++){
            if(Float.parseFloat(score)>
                    Float.parseFloat(leaderboard[i][1])){
                String[] temp=new String[2];
                temp[0]=leaderboard[i][0];
                temp[1]=leaderboard[i][1];
                leaderboard[i][0]=usr;
                leaderboard[i][1]=score;
                usr=temp[0];
                score=temp[1];

            }
        }
    }


}
