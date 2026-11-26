package com.exteragram.messenger.utils.chats;

import android.os.Bundle;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;

public interface ForwardContext {
    String FORWARD_NOQUOTE = "forward_noquote";

    ForwardParams getForwardParams();

    ArrayList<MessageObject> getForwardingMessages();

    default void setForwardParams(boolean noQuote) {
        getForwardParams().noQuote = noQuote;
    }

    default boolean isForwardNoQuote() {
        return getForwardParams().noQuote;
    }

    default void writeForwardParams(Bundle args) {
        if (args == null) {
            return;
        }
        args.putBoolean(FORWARD_NOQUOTE, isForwardNoQuote());
    }

    default void readForwardParams(Bundle args) {
        if (args == null) {
            return;
        }
        setForwardParams(args.getBoolean(FORWARD_NOQUOTE, false));
    }

    class ForwardParams {
        public boolean noQuote;
    }
}
