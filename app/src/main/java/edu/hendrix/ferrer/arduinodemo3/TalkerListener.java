package edu.hendrix.ferrer.arduinodemo3;

/**
 * Created by gabriel on 6/3/18.
 */

public interface TalkerListener {
    public void sendComplete(int status);
    public void receiveComplete(int status);
    public void error();
}
