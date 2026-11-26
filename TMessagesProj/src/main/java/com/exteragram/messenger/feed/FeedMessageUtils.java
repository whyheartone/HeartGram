package com.exteragram.messenger.feed;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/**
 * Копирование сообщений и состояния перевода для превью экспорта сообщений в изображение.
 */
public final class FeedMessageUtils {

    private FeedMessageUtils() {
    }

    public static void copyTranslationState(MessageObject source, MessageObject target) {
        if (source == null || target == null || source == target || source.messageOwner == null || target.messageOwner == null) {
            return;
        }
        target.messageOwner.translatedText = source.messageOwner.translatedText;
        target.messageOwner.translatedToLanguage = source.messageOwner.translatedToLanguage;
        target.messageOwner.translatedVoiceTranscription = source.messageOwner.translatedVoiceTranscription;
        target.messageOwner.translatedPoll = source.messageOwner.translatedPoll;
        target.messageOwner.summaryText = source.messageOwner.summaryText;
        target.messageOwner.summarizedOpen = source.messageOwner.summarizedOpen;
        target.messageOwner.translatedSummaryText = source.messageOwner.translatedSummaryText;
        target.messageOwner.translatedSummaryLanguage = source.messageOwner.translatedSummaryLanguage;
    }

    public static TLRPC.TL_message copyMessage(TLRPC.Message source) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = source.id;
        message.from_id = source.from_id;
        message.from_boosts_applied = source.from_boosts_applied;
        message.peer_id = source.peer_id;
        message.saved_peer_id = source.saved_peer_id;
        message.date = source.date;
        message.expire_date = source.expire_date;
        message.action = source.action;
        message.message = source.message;
        message.media = source.media;
        message.flags = source.flags;
        message.flags2 = source.flags2;
        message.mentioned = source.mentioned;
        message.media_unread = source.media_unread;
        message.out = source.out;
        message.unread = source.unread;
        message.entities = source.entities;
        message.via_bot_name = source.via_bot_name;
        message.reply_markup = source.reply_markup;
        message.views = source.views;
        message.forwards = source.forwards;
        message.replies = source.replies;
        message.edit_date = source.edit_date;
        message.silent = source.silent;
        message.post = source.post;
        message.from_scheduled = source.from_scheduled;
        message.legacy = source.legacy;
        message.edit_hide = source.edit_hide;
        message.pinned = source.pinned;
        message.fwd_from = source.fwd_from;
        message.via_bot_id = source.via_bot_id;
        message.via_business_bot_id = source.via_business_bot_id;
        message.reply_to = source.reply_to;
        message.post_author = source.post_author;
        message.grouped_id = source.grouped_id;
        message.reactions = source.reactions;
        message.restriction_reason = source.restriction_reason;
        message.ttl_period = source.ttl_period;
        message.quick_reply_shortcut_id = source.quick_reply_shortcut_id;
        message.effect = source.effect;
        message.noforwards = source.noforwards;
        message.invert_media = source.invert_media;
        message.offline = source.offline;
        message.factcheck = source.factcheck;
        message.send_state = source.send_state;
        message.fwd_msg_id = source.fwd_msg_id;
        message.params = source.params;
        message.random_id = source.random_id;
        message.local_id = source.local_id;
        message.attachPath = source.attachPath;
        message.dialog_id = source.dialog_id;
        message.ttl = source.ttl;
        message.destroyTime = source.destroyTime;
        message.destroyTimeMillis = source.destroyTimeMillis;
        message.layer = source.layer;
        message.seq_in = source.seq_in;
        message.seq_out = source.seq_out;
        message.with_my_score = source.with_my_score;
        message.replyMessage = source.replyMessage;
        message.reqId = source.reqId;
        message.realId = source.realId;
        message.stickerVerified = source.stickerVerified;
        message.isThreadMessage = source.isThreadMessage;
        message.voiceTranscription = source.voiceTranscription;
        message.voiceTranscriptionOpen = source.voiceTranscriptionOpen;
        message.voiceTranscriptionRated = source.voiceTranscriptionRated;
        message.voiceTranscriptionFinal = source.voiceTranscriptionFinal;
        message.voiceTranscriptionForce = source.voiceTranscriptionForce;
        message.voiceTranscriptionId = source.voiceTranscriptionId;
        message.premiumEffectWasPlayed = source.premiumEffectWasPlayed;
        return message;
    }
}
