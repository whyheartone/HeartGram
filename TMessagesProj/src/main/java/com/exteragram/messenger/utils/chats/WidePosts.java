package com.exteragram.messenger.utils.chats;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Минимальный хелпер широких постов для превью экспорта сообщений.
 */
public final class WidePosts {

    private static final Map<MessageObject, Boolean> WIDE_PREVIEWS = new WeakHashMap<>();
    private static final Map<MessageObject, Boolean> HIDDEN_PROFILES = new WeakHashMap<>();

    private WidePosts() {
    }

    public static void allowInPreview(MessageObject messageObject) {
        if (messageObject != null) {
            WIDE_PREVIEWS.put(messageObject, Boolean.TRUE);
        }
    }

    public static void setProfileHidden(MessageObject messageObject, boolean hidden) {
        if (messageObject == null) {
            return;
        }
        if (hidden) {
            HIDDEN_PROFILES.put(messageObject, Boolean.TRUE);
        } else {
            HIDDEN_PROFILES.remove(messageObject);
        }
    }

    public static boolean isEnabledFor(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.messageOwner.peer_id != null && messageObject.messageOwner.peer_id.channel_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(messageObject.messageOwner.peer_id.channel_id);
            if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                return ExteraConfig.getWidePostsInChannels() || WIDE_PREVIEWS.containsKey(messageObject);
            }
        }
        return false;
    }

    public static boolean shouldEmbedProfileAvatar(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || HIDDEN_PROFILES.containsKey(messageObject)) {
            return false;
        }
        if (!isEnabledFor(messageObject) || messageObject.isOutOwner() || !messageObject.needDrawAvatar()) {
            return false;
        }
        if (messageObject.messageOwner.peer_id != null && messageObject.messageOwner.peer_id.channel_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(messageObject.messageOwner.peer_id.channel_id);
            return chat != null && chat.signature_profiles && ChatObject.isChannelAndNotMegaGroup(chat);
        }
        return false;
    }

    public static boolean hasSameEmbeddedProfileHeader(MessageObject first, MessageObject second) {
        if (first == null || second == null || first.messageOwner == null || second.messageOwner == null) {
            return false;
        }
        return first.getFromChatId() == second.getFromChatId();
    }
}
