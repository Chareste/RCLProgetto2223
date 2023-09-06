package Server;//import com.fasterxml.jackson.annotation.JsonValue;

public class User{
    private String username;
    private String password;
    private float score;
    private boolean canPlay;
    private boolean loggedIn;
    private int[] winDistribution;
    private int wins;
    //losses sono in posizione array 0
    private int currStreak;
    private int maxStreak;

    public User(String usr, String pwd){
        this.username = usr;
        this.password = pwd;
        this.score = 0;
        this.canPlay = true;
        this.loggedIn= false;
        this.winDistribution = new int[13];
        this.wins = 0;
        currStreak = 0;
        maxStreak = 0;

    }
    /**
     * funzione che aggiunge la partita effettuata nella winDistribution in accordo ai tentativi impiegati.
     * la posizione [0] conta i match persi.
     * il numero totale di vittorie è tenuto in memoria a parte per comodità.
     */
    public void addMatch(int tries){
        if(tries<0){
            this.winDistribution[0]++;
        }
        else{
            wins++;
            this.winDistribution[12-tries]++;
        }
    }
    /**
     * ho effettuato un cambiamento nel calcolo del punteggio:
     * la formula sottrae ora un punto per ogni sconfitta: (media di punti a partita*nr vittorie) - nr sconfitte
     * dove la media di punti a partita è inversamente proporzionale al numero di tentativi,
     * premiando di conseguenza chi ce ne mette di meno.
     * Il numero di punti per ogni partita vinta va da 6 per un tentativo a 0.5 per 12,
     * togliendo 0.5 per ogni tentativo extra.
     * In questo modo il punteggio può diminuire e gli utenti possono scendere di posizione.
     */
    public void setScore(){
        float med=0;
        for(int i=1;i<13;i++)
            med+=((13-i)/2.0)*winDistribution[i];
        med/=12;
        score = (med*wins)-winDistribution[0];

    }
    /**
     * utility functions
     */
    public String getUsername(){
        return username;
    }
    public String getPassword(){
        return password;
    }
    public float getScore(){
        return score;
    }
    public boolean canPlay(){
        return canPlay;
    }
    public void setCanPlay(boolean canPlay){
        this.canPlay=canPlay;
    }
    public void setLoggedIn(boolean loggedIn){
        this.loggedIn=loggedIn;
    }
    public boolean isLoggedIn(){
        return loggedIn;
    }
    public int getWins(){
        return wins;
    }
    public int[] getWinDistribution(){
        return winDistribution;
    }
    public void setCurrStreak(int currStreak){
        this.currStreak=currStreak;
    }
    public void setMaxStreak(int maxStreak){
        this.maxStreak=maxStreak;
    }
    public int getCurrStreak(){
        return currStreak;
    }
    public int getMaxStreak(){
        return maxStreak;
    }
}
