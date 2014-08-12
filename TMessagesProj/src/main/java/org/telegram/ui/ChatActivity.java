/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationsController;
import org.telegram.messenger.TLRPC;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.objects.MessageObject;
import org.telegram.objects.PhotoObject;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.android.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.ChatAudioCell;
import org.telegram.ui.Cells.ChatBaseCell;
import org.telegram.ui.Cells.ChatMediaCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.ChatActivityEnterView;
import org.telegram.ui.Views.ImageReceiver;
import org.telegram.ui.Views.LayoutListView;
import org.telegram.ui.Views.MessageActionLayout;
import org.telegram.ui.Views.SizeNotifierRelativeLayout;
import org.telegram.ui.Views.TimerButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class ChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MessagesActivity.MessagesActivityDelegate,
        DocumentSelectActivity.DocumentSelectActivityDelegate, PhotoViewer.PhotoViewerProvider, PhotoPickerActivity.PhotoPickerActivityDelegate,
        VideoEditorActivity.VideoEditorActivityDelegate {

    private ChatActivityEnterView chatActivityEnterView;
    private View timeItem;
    private View menuItem;
    private LayoutListView chatListView;
    private BackupImageView avatarImageView;
    private TLRPC.Chat currentChat;
    private TLRPC.User currentUser;
    private TLRPC.EncryptedChat currentEncryptedChat;
    private ChatAdapter chatAdapter;

    private View topPanel;
    private View secretChatPlaceholder;
    private View progressView;
    private TextView emptyView;
    private View bottomOverlay;

    private TextView bottomOverlayText;

    private MessageObject selectedObject;
    private MessageObject forwaringMessage;
    private TextView secretViewStatusTextView;
    private TimerButton timerButton;
    private TextView selectedMessagesCountTextView;
    private boolean paused = true;
    private boolean readWhenResume = false;

    private int readWithDate = 0;
    private int readWithMid = 0;
    private boolean scrollToTopOnResume = false;
    private boolean scrollToTopUnReadOnResume = false;
    private boolean isCustomTheme = false;
    private int downloadPhotos = 0;
    private int downloadAudios = 0;
    private ImageView topPlaneClose;
    private View pagedownButton;
    private TextView topPanelText;
    private long dialog_id;
    private HashMap<Integer, MessageObject> selectedMessagesIds = new HashMap<Integer, MessageObject>();
    private HashMap<Integer, MessageObject> selectedMessagesCanCopyIds = new HashMap<Integer, MessageObject>();

    private HashMap<Integer, MessageObject> messagesDict = new HashMap<Integer, MessageObject>();
    private HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<String, ArrayList<MessageObject>>();
    private ArrayList<MessageObject> messages = new ArrayList<MessageObject>();
    private int maxMessageId = Integer.MAX_VALUE;
    private int minMessageId = Integer.MIN_VALUE;
    private int maxDate = Integer.MIN_VALUE;
    private boolean endReached = false;
    private boolean loading = false;
    private boolean cacheEndReaced = false;

    private int minDate = 0;
    private int progressTag = 0;
    boolean first = true;
    private int unread_to_load = 0;
    private int first_unread_id = 0;
    private int last_unread_id = 0;
    private boolean unread_end_reached = true;
    private boolean loadingForward = false;
    private MessageObject unreadMessageObject = null;

    private String currentPicturePath;

    private TLRPC.ChatParticipants info = null;
    private int onlineCount = -1;

    private HashMap<String, ProgressBar> progressBarMap = new HashMap<String, ProgressBar>();
    private HashMap<String, ArrayList<ProgressBar>> loadingFile = new HashMap<String, ArrayList<ProgressBar>>();
    private HashMap<Integer, String> progressByTag = new HashMap<Integer, String>();

    private CharSequence lastPrintString;

    private long chatEnterTime = 0;
    private long chatLeaveTime = 0;

    private final static int copy = 1;
    private final static int forward = 2;
    private final static int delete = 3;
    private final static int chat_enc_timer = 4;
    private final static int chat_menu_attach = 5;
    private final static int attach_photo = 6;
    private final static int attach_gallery = 7;
    private final static int attach_video = 8;
    private final static int attach_document = 9;
    private final static int attach_location = 10;
    private final static int chat_menu_avatar = 11;

    public ChatActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        final int chatId = arguments.getInt("chat_id", 0);
        final int userId = arguments.getInt("user_id", 0);
        final int encId = arguments.getInt("enc_id", 0);
        final String messageToSend = arguments.getString("message_to_send", null);
        scrollToTopOnResume = arguments.getBoolean("scrollToTopOnResume", false);

        if (chatId != 0) {
            currentChat = MessagesController.getInstance().chats.get(chatId);
            if (currentChat == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentChat = MessagesStorage.getInstance().getChat(chatId);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentChat != null) {
                    MessagesController.getInstance().chats.put(currentChat.id, currentChat);
                } else {
                    return false;
                }
            }
            MessagesController.getInstance().loadChatInfo(currentChat.id);
            dialog_id = -chatId;
        } else if (userId != 0) {
            currentUser = MessagesController.getInstance().users.get(userId);
            if (currentUser == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentUser = MessagesStorage.getInstance().getUser(userId);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentUser != null) {
                    MessagesController.getInstance().users.putIfAbsent(currentUser.id, currentUser);
                } else {
                    return false;
                }
            }
            dialog_id = userId;
        } else if (encId != 0) {
            currentEncryptedChat = MessagesController.getInstance().encryptedChats.get(encId);
            if (currentEncryptedChat == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentEncryptedChat = MessagesStorage.getInstance().getEncryptedChat(encId);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentEncryptedChat != null) {
                    MessagesController.getInstance().encryptedChats.putIfAbsent(currentEncryptedChat.id, currentEncryptedChat);
                } else {
                    return false;
                }
            }
            currentUser = MessagesController.getInstance().users.get(currentEncryptedChat.user_id);
            if (currentUser == null) {
                final Semaphore semaphore = new Semaphore(0);
                MessagesStorage.getInstance().storageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        currentUser = MessagesStorage.getInstance().getUser(currentEncryptedChat.user_id);
                        semaphore.release();
                    }
                });
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (currentUser != null) {
                    MessagesController.getInstance().users.putIfAbsent(currentUser.id, currentUser);
                } else {
                    return false;
                }
            }
            dialog_id = ((long)encId) << 32;
            maxMessageId = Integer.MIN_VALUE;
            minMessageId = Integer.MAX_VALUE;
            MediaController.getInstance().startMediaObserver();
        } else {
            return false;
        }
        chatActivityEnterView = new ChatActivityEnterView();
        chatActivityEnterView.setDialogId(dialog_id);
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend() {
                chatListView.post(new Runnable() {
                    @Override
                    public void run() {
                        chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                    }
                });
            }

            @Override
            public void needSendTyping() {
                MessagesController.getInstance().sendTyping(dialog_id, classGuid);
            }
        });
        NotificationCenter.getInstance().addObserver(this, MessagesController.messagesDidLoaded);
        NotificationCenter.getInstance().addObserver(this, 999);
        NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, MessagesController.didReceivedNewMessages);
        NotificationCenter.getInstance().addObserver(this, MessagesController.closeChats);
        NotificationCenter.getInstance().addObserver(this, MessagesController.messagesReaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.messagesDeleted);
        NotificationCenter.getInstance().addObserver(this, MessagesController.messageReceivedByServer);
        NotificationCenter.getInstance().addObserver(this, MessagesController.messageReceivedByAck);
        NotificationCenter.getInstance().addObserver(this, MessagesController.messageSendError);
        NotificationCenter.getInstance().addObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().addObserver(this, MessagesController.messagesReadedEncrypted);
        NotificationCenter.getInstance().addObserver(this, MessagesController.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileUploadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, MediaController.audioProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, MediaController.audioDidReset);
        NotificationCenter.getInstance().addObserver(this, MediaController.screenshotTook);
        NotificationCenter.getInstance().addObserver(this, 997);

        super.onFragmentCreate();

        loading = true;
        MessagesController.getInstance().loadMessages(dialog_id, 0, 30, 0, true, 0, classGuid, true, false);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);

        if (currentChat != null) {
            downloadPhotos = preferences.getInt("photo_download_chat2", 0);
            downloadAudios = preferences.getInt("audio_download_chat2", 0);
        } else {
            downloadPhotos = preferences.getInt("photo_download_user2", 0);
            downloadAudios = preferences.getInt("audio_download_user2", 0);
        }

        if (messageToSend != null) {
            MessagesController.getInstance().sendMessage(messageToSend, dialog_id);
        }

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        NotificationCenter.getInstance().removeObserver(this, MessagesController.messagesDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, 999);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.didReceivedNewMessages);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.closeChats);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.messagesReaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.messageReceivedByServer);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.messageReceivedByAck);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.messageSendError);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.chatInfoDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.encryptedChatUpdated);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.messagesReadedEncrypted);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.removeAllMessagesFromDialog);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileUploadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, MediaController.audioProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, MediaController.audioDidReset);
        NotificationCenter.getInstance().removeObserver(this, MediaController.screenshotTook);
        NotificationCenter.getInstance().removeObserver(this, 997);
        if (currentEncryptedChat != null) {
            MediaController.getInstance().stopMediaObserver();
        }

        AndroidUtilities.unlockOrientation(getParentActivity());
        MediaController.getInstance().stopAudio();
    }

    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
            actionBarLayer.setBackOverlay(R.layout.updating_state_layout);
            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == -2) {
                        selectedMessagesIds.clear();
                        selectedMessagesCanCopyIds.clear();
                        actionBarLayer.hideActionMode();
                        updateVisibleRows();
                    } else if (id == attach_photo) {
                        try {
                            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            File image = Utilities.generatePicturePath();
                            if (image != null) {
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                                currentPicturePath = image.getAbsolutePath();
                            }
                            getParentActivity().startActivityForResult(takePictureIntent, 0);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (id == attach_gallery) {
                        PhotoPickerActivity fragment = new PhotoPickerActivity();
                        fragment.setDelegate(ChatActivity.this);
                        presentFragment(fragment);
                    } else if (id == attach_video) {
                        try {
                            Intent pickIntent = new Intent();
                            pickIntent.setType("video/*");
                            pickIntent.setAction(Intent.ACTION_GET_CONTENT);
                            pickIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1000));
                            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                            File video = Utilities.generateVideoPath();
                            if (video != null) {
                                if (android.os.Build.VERSION.SDK_INT > 16) {
                                    takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
                                }
                                takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) (1024 * 1024 * 1000));
                                currentPicturePath = video.getAbsolutePath();
                            }
                            Intent chooserIntent = Intent.createChooser(pickIntent, "");
                            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takeVideoIntent});

                            getParentActivity().startActivityForResult(chooserIntent, 2);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else if (id == attach_location) {
                        if (!isGoogleMapsInstalled()) {
                            return;
                        }
                        LocationActivity fragment = new LocationActivity();
                        presentFragment(fragment);
                    } else if (id == attach_document) {
                        DocumentSelectActivity fragment = new DocumentSelectActivity();
                        fragment.setDelegate(ChatActivity.this);
                        presentFragment(fragment);
                    } else if (id == chat_menu_avatar) {
                        if (currentUser != null) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", currentUser.id);
                            if (currentEncryptedChat != null) {
                                args.putLong("dialog_id", dialog_id);
                            }
                            presentFragment(new UserProfileActivity(args));
                        } else if (currentChat != null) {
                            if (info != null && info instanceof TLRPC.TL_chatParticipantsForbidden) {
                                return;
                            }
                            if (currentChat.participants_count == 0 || currentChat.left || currentChat instanceof TLRPC.TL_chatForbidden) {
                                return;
                            }
                            Bundle args = new Bundle();
                            args.putInt("chat_id", currentChat.id);
                            ChatProfileActivity fragment = new ChatProfileActivity(args);
                            fragment.setChatInfo(info);
                            presentFragment(fragment);
                        }
                    } else if (id == copy) {
                        String str = "";
                        ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesCanCopyIds.keySet());
                        if (currentEncryptedChat == null) {
                            Collections.sort(ids);
                        } else {
                            Collections.sort(ids, Collections.reverseOrder());
                        }
                        for (Integer messageId : ids) {
                            MessageObject messageObject = selectedMessagesCanCopyIds.get(messageId);
                            if (str.length() != 0) {
                                str += "\n";
                            }
                            if (messageObject.messageOwner.message != null) {
                                str += messageObject.messageOwner.message;
                            } else {
                                str += messageObject.messageText;
                            }
                        }
                        if (str.length() != 0) {
                            if (android.os.Build.VERSION.SDK_INT < 11) {
                                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                clipboard.setText(str);
                            } else {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", str);
                                clipboard.setPrimaryClip(clip);
                            }
                        }
                        selectedMessagesIds.clear();
                        selectedMessagesCanCopyIds.clear();
                        actionBarLayer.hideActionMode();
                        updateVisibleRows();
                    } else if (id == delete) {
                        ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesIds.keySet());
                        ArrayList<Long> random_ids = null;
                        if (currentEncryptedChat != null) {
                            random_ids = new ArrayList<Long>();
                            for (HashMap.Entry<Integer, MessageObject> entry : selectedMessagesIds.entrySet()) {
                                MessageObject msg = entry.getValue();
                                if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                    random_ids.add(msg.messageOwner.random_id);
                                }
                            }
                        }
                        MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat);
                        actionBarLayer.hideActionMode();
                    } else if (id == forward) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlySelect", true);
                        args.putBoolean("serverOnly", true);
                        args.putString("selectAlertString", LocaleController.getString("ForwardMessagesTo", R.string.ForwardMessagesTo));
                        MessagesActivity fragment = new MessagesActivity(args);
                        fragment.setDelegate(ChatActivity.this);
                        presentFragment(fragment);
                    }
                }
            });

            updateSubtitle();

            if (currentEncryptedChat != null) {
                actionBarLayer.setTitleIcon(R.drawable.ic_lock_white, AndroidUtilities.dp(4));
            }

            ActionBarMenu menu = actionBarLayer.createMenu();

            if (currentEncryptedChat != null) {
                timeItem = menu.addItemResource(chat_enc_timer, R.layout.chat_header_enc_layout);
            }

            ActionBarMenuItem item = menu.addItem(chat_menu_attach, R.drawable.ic_ab_attach);
            item.addSubItem(attach_photo, LocaleController.getString("ChatTakePhoto", R.string.ChatTakePhoto), R.drawable.ic_attach_photo);
            item.addSubItem(attach_gallery, LocaleController.getString("ChatGallery", R.string.ChatGallery), R.drawable.ic_attach_gallery);
            item.addSubItem(attach_video, LocaleController.getString("ChatVideo", R.string.ChatVideo), R.drawable.ic_attach_video);
            item.addSubItem(attach_document, LocaleController.getString("ChatDocument", R.string.ChatDocument), R.drawable.ic_ab_doc);
            item.addSubItem(attach_location, LocaleController.getString("ChatLocation", R.string.ChatLocation), R.drawable.ic_attach_location);
            menuItem = item;

            ActionBarMenu actionMode = actionBarLayer.createActionMode();
            actionMode.addItem(-2, R.drawable.ic_ab_done_gray);

            FrameLayout layout = new FrameLayout(actionMode.getContext());
            layout.setBackgroundColor(0xffe5e5e5);
            actionMode.addView(layout);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)layout.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(1);
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.topMargin = AndroidUtilities.dp(12);
            layoutParams.bottomMargin = AndroidUtilities.dp(12);
            layoutParams.gravity = Gravity.CENTER_VERTICAL;
            layout.setLayoutParams(layoutParams);

            selectedMessagesCountTextView = new TextView(actionMode.getContext());
            selectedMessagesCountTextView.setTextSize(18);
            selectedMessagesCountTextView.setTextColor(0xff000000);
            selectedMessagesCountTextView.setSingleLine(true);
            selectedMessagesCountTextView.setLines(1);
            selectedMessagesCountTextView.setEllipsize(TextUtils.TruncateAt.END);
            selectedMessagesCountTextView.setPadding(AndroidUtilities.dp(6), 0, 0, 0);
            selectedMessagesCountTextView.setGravity(Gravity.CENTER_VERTICAL);
            selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            actionMode.addView(selectedMessagesCountTextView);
            layoutParams = (LinearLayout.LayoutParams)selectedMessagesCountTextView.getLayoutParams();
            layoutParams.weight = 1;
            layoutParams.width = 0;
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            selectedMessagesCountTextView.setLayoutParams(layoutParams);

            if (currentEncryptedChat == null) {
                actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy);
                actionMode.addItem(forward, R.drawable.ic_ab_fwd_forward);
                actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete);
            } else {
                actionMode.addItem(copy, R.drawable.ic_ab_fwd_copy);
                actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete);
            }
            actionMode.getItem(copy).setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);

            View avatarLayout = menu.addItemResource(chat_menu_avatar, R.layout.chat_header_layout);
            avatarImageView = (BackupImageView)avatarLayout.findViewById(R.id.chat_avatar_image);
            avatarImageView.processDetach = false;
            checkActionBarMenu();

            fragmentView = inflater.inflate(R.layout.chat_layout, container, false);

            View contentView = fragmentView.findViewById(R.id.chat_layout);
            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setText(LocaleController.getString("NoMessages", R.string.NoMessages));
            chatListView = (LayoutListView)fragmentView.findViewById(R.id.chat_list_view);
            chatListView.setAdapter(chatAdapter = new ChatAdapter(getParentActivity()));
            topPanel = fragmentView.findViewById(R.id.top_panel);
            topPlaneClose = (ImageView)fragmentView.findViewById(R.id.top_plane_close);
            topPanelText = (TextView)fragmentView.findViewById(R.id.top_panel_text);
            bottomOverlay = fragmentView.findViewById(R.id.bottom_overlay);
            bottomOverlayText = (TextView)fragmentView.findViewById(R.id.bottom_overlay_text);
            View bottomOverlayChat = fragmentView.findViewById(R.id.bottom_overlay_chat);
            progressView = fragmentView.findViewById(R.id.progressLayout);
            pagedownButton = fragmentView.findViewById(R.id.pagedown_button);

            View progressViewInner = progressView.findViewById(R.id.progressLayoutInner);

            updateContactStatus();

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            int selectedBackground = preferences.getInt("selectedBackground", 1000001);
            int selectedColor = preferences.getInt("selectedColor", 0);
            if (selectedColor != 0) {
                contentView.setBackgroundColor(selectedColor);
                chatListView.setCacheColorHint(selectedColor);
            } else {
                chatListView.setCacheColorHint(0);
                try {
                    if (selectedBackground == 1000001) {
                        ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(R.drawable.background_hd);
                    } else {
                        File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                        if (toFile.exists()) {
                            if (ApplicationLoader.cachedWallpaper != null) {
                                ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(ApplicationLoader.cachedWallpaper);
                            } else {
                                Drawable drawable = Drawable.createFromPath(toFile.getAbsolutePath());
                                if (drawable != null) {
                                    ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(drawable);
                                    ApplicationLoader.cachedWallpaper = drawable;
                                } else {
                                    contentView.setBackgroundColor(-2693905);
                                    chatListView.setCacheColorHint(-2693905);
                                }
                            }
                            isCustomTheme = true;
                        } else {
                            ((SizeNotifierRelativeLayout) contentView).setBackgroundImage(R.drawable.background_hd);
                        }
                    }
                } catch (Exception e) {
                    contentView.setBackgroundColor(-2693905);
                    chatListView.setCacheColorHint(-2693905);
                    FileLog.e("tmessages", e);
                }
            }

            if (currentEncryptedChat != null) {
                secretChatPlaceholder = contentView.findViewById(R.id.secret_placeholder);
                if (isCustomTheme) {
                    secretChatPlaceholder.setBackgroundResource(R.drawable.system_black);
                } else {
                    secretChatPlaceholder.setBackgroundResource(R.drawable.system_blue);
                }
                secretViewStatusTextView = (TextView) contentView.findViewById(R.id.invite_text);
                secretChatPlaceholder.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

                View v = contentView.findViewById(R.id.secret_placeholder);
                v.setVisibility(View.VISIBLE);

                if (currentEncryptedChat.admin_id == UserConfig.getClientUserId()) {
                    if (currentUser.first_name.length() > 0) {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleOutgoing", R.string.EncryptedPlaceholderTitleOutgoing, currentUser.first_name));
                    } else {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleOutgoing", R.string.EncryptedPlaceholderTitleOutgoing, currentUser.last_name));
                    }
                } else {
                    if (currentUser.first_name.length() > 0) {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleIncoming", R.string.EncryptedPlaceholderTitleIncoming, currentUser.first_name));
                    } else {
                        secretViewStatusTextView.setText(LocaleController.formatString("EncryptedPlaceholderTitleIncoming", R.string.EncryptedPlaceholderTitleIncoming, currentUser.last_name));
                    }
                }

                updateSecretStatus();
            }

            if (isCustomTheme) {
                progressViewInner.setBackgroundResource(R.drawable.system_loader2);
                emptyView.setBackgroundResource(R.drawable.system_black);
            } else {
                progressViewInner.setBackgroundResource(R.drawable.system_loader1);
                emptyView.setBackgroundResource(R.drawable.system_blue);
            }
            emptyView.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(1), AndroidUtilities.dp(7), AndroidUtilities.dp(1));

            if (currentUser != null && currentUser.id / 1000 == 333) {
                emptyView.setText(LocaleController.getString("GotAQuestion", R.string.GotAQuestion));
            }

            chatListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapter, View view, int position, long id) {
                    if (!actionBarLayer.isActionModeShowed()) {
                        createMenu(view, false);
                    }
                    return true;
                }
            });

            final Rect scrollRect = new Rect();

            chatListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {

                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (visibleItemCount > 0) {
                        if (firstVisibleItem <= 4) {
                            if (!endReached && !loading) {
                                if (messagesByDays.size() != 0) {
                                    MessagesController.getInstance().loadMessages(dialog_id, 0, 20, maxMessageId, !cacheEndReaced, minDate, classGuid, false, false);
                                } else {
                                    MessagesController.getInstance().loadMessages(dialog_id, 0, 20, 0, !cacheEndReaced, minDate, classGuid, false, false);
                                }
                                loading = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount >= totalItemCount - 6) {
                            if (!unread_end_reached && !loadingForward) {
                                MessagesController.getInstance().loadMessages(dialog_id, 0, 20, minMessageId, true, maxDate, classGuid, false, true);
                                loadingForward = true;
                            }
                        }
                        if (firstVisibleItem + visibleItemCount == totalItemCount && unread_end_reached) {
                            showPagedownButton(false, true);
                        }
                    } else {
                        showPagedownButton(false, false);
                    }
                    for (int a = 0; a < visibleItemCount; a++) {
                        View view = absListView.getChildAt(a);
                        if (view instanceof ChatMessageCell) {
                            ChatMessageCell messageCell = (ChatMessageCell)view;
                            messageCell.getLocalVisibleRect(scrollRect);
                            messageCell.setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top);
                        }
                    }
                }
            });

            TextView textView = (TextView)fragmentView.findViewById(R.id.bottom_overlay_chat_text);
            if (currentUser == null) {
                textView.setText(LocaleController.getString("DeleteThisGroup", R.string.DeleteThisGroup));
            } else {
                textView.setText(LocaleController.getString("DeleteThisChat", R.string.DeleteThisChat));
            }
            textView = (TextView)fragmentView.findViewById(R.id.secret_title);
            textView.setText(LocaleController.getString("EncryptedDescriptionTitle", R.string.EncryptedDescriptionTitle));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description1);
            textView.setText(LocaleController.getString("EncryptedDescription1", R.string.EncryptedDescription1));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description2);
            textView.setText(LocaleController.getString("EncryptedDescription2", R.string.EncryptedDescription2));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description3);
            textView.setText(LocaleController.getString("EncryptedDescription3", R.string.EncryptedDescription3));
            textView = (TextView)fragmentView.findViewById(R.id.secret_description4);
            textView.setText(LocaleController.getString("EncryptedDescription4", R.string.EncryptedDescription4));

            if (loading && messages.isEmpty()) {
                progressView.setVisibility(View.VISIBLE);
                chatListView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                if (currentEncryptedChat == null) {
                    chatListView.setEmptyView(emptyView);
                } else {
                    chatListView.setEmptyView(secretChatPlaceholder);
                }
            }

            pagedownButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    scrollToLastMessage();
                }
            });

            bottomOverlayChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteThisChat", R.string.AreYouSureDeleteThisChat));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            MessagesController.getInstance().deleteDialog(dialog_id, 0, false);
                            finishFragment();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                }
            });

            chatListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (actionBarLayer.isActionModeShowed()) {
                        processRowSelect(view);
                        return;
                    }
                    createMenu(view, true);
                }
            });

            if (currentChat != null && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left) ||
                    currentUser != null && (currentUser instanceof TLRPC.TL_userDeleted || currentUser instanceof TLRPC.TL_userEmpty)) {
                bottomOverlayChat.setVisibility(View.VISIBLE);
            } else {
                bottomOverlayChat.setVisibility(View.GONE);
            }

            chatActivityEnterView.setContainerView(getParentActivity(), fragmentView);
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void scrollToLastMessage() {
        if (unread_end_reached || first_unread_id == 0) {
            chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
        } else {
            messages.clear();
            messagesByDays.clear();
            messagesDict.clear();
            progressView.setVisibility(View.VISIBLE);
            chatListView.setEmptyView(null);
            if (currentEncryptedChat == null) {
                maxMessageId = Integer.MAX_VALUE;
                minMessageId = Integer.MIN_VALUE;
            } else {
                maxMessageId = Integer.MIN_VALUE;
                minMessageId = Integer.MAX_VALUE;
            }
            maxDate = Integer.MIN_VALUE;
            minDate = 0;
            unread_end_reached = true;
            MessagesController.getInstance().loadMessages(dialog_id, 0, 30, 0, true, 0, classGuid, true, false);
            loading = true;
            chatAdapter.notifyDataSetChanged();
        }
    }

    private void showPagedownButton(boolean show, boolean animated) {
        if (pagedownButton == null) {
            return;
        }
        if (show) {
            if (pagedownButton.getVisibility() == View.GONE) {
                if (android.os.Build.VERSION.SDK_INT >= 16 && animated) {
                    pagedownButton.setVisibility(View.VISIBLE);
                    pagedownButton.setAlpha(0);
                    pagedownButton.animate().alpha(1).setDuration(200).start();
                } else {
                    pagedownButton.setVisibility(View.VISIBLE);
                }
            }
        } else {
            if (pagedownButton.getVisibility() == View.VISIBLE) {
                if (android.os.Build.VERSION.SDK_INT >= 16 && animated) {
                    pagedownButton.animate().alpha(0).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            pagedownButton.setVisibility(View.GONE);
                        }
                    }).setDuration(200).start();
                } else {
                    pagedownButton.setVisibility(View.GONE);
                }
            }
        }
    }

    private void updateSecretStatus() {
        if (bottomOverlay == null) {
            return;
        }
        if (currentEncryptedChat == null || secretViewStatusTextView == null) {
            bottomOverlay.setVisibility(View.GONE);
            return;
        }
        boolean hideKeyboard = false;
        if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatRequested) {
            bottomOverlayText.setText(LocaleController.getString("EncryptionProcessing", R.string.EncryptionProcessing));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatWaiting) {
            bottomOverlayText.setText(Html.fromHtml(LocaleController.formatString("AwaitingEncryption", R.string.AwaitingEncryption, "<b>" + currentUser.first_name + "</b>")));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChatDiscarded) {
            bottomOverlayText.setText(LocaleController.getString("EncryptionRejected", R.string.EncryptionRejected));
            bottomOverlay.setVisibility(View.VISIBLE);
            hideKeyboard = true;
        } else if (currentEncryptedChat instanceof TLRPC.TL_encryptedChat) {
            bottomOverlay.setVisibility(View.GONE);
        }
        if (hideKeyboard) {
            chatActivityEnterView.hideEmojiPopup();
            AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        }
        checkActionBarMenu();
    }

    private void checkActionBarMenu() {
        if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat) ||
                currentChat != null && (currentChat instanceof TLRPC.TL_chatForbidden || currentChat.left) ||
                currentUser != null && (currentUser instanceof TLRPC.TL_userDeleted || currentUser instanceof TLRPC.TL_userEmpty)) {

            if (menuItem != null) {
                menuItem.setVisibility(View.GONE);
            }

            if (timeItem != null) {
                timeItem.setVisibility(View.GONE);
            }
        } else {
            if (menuItem != null) {
                menuItem.setVisibility(View.VISIBLE);
            }

            if (timeItem != null) {
                timeItem.setVisibility(View.VISIBLE);
            }
        }

        if (timeItem != null) {
            timerButton = (TimerButton)timeItem.findViewById(R.id.chat_timer);
            timerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("MessageLifetime", R.string.MessageLifetime));
                    builder.setItems(new CharSequence[]{
                            LocaleController.getString("ShortMessageLifetimeForever", R.string.ShortMessageLifetimeForever),
                            LocaleController.getString("ShortMessageLifetime2s", R.string.ShortMessageLifetime2s),
                            LocaleController.getString("ShortMessageLifetime5s", R.string.ShortMessageLifetime5s),
                            LocaleController.getString("ShortMessageLifetime1m", R.string.ShortMessageLifetime1m),
                            LocaleController.getString("ShortMessageLifetime1h", R.string.ShortMessageLifetime1h),
                            LocaleController.getString("ShortMessageLifetime1d", R.string.ShortMessageLifetime1d),
                            LocaleController.getString("ShortMessageLifetime1w", R.string.ShortMessageLifetime1w)

                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int oldValue = currentEncryptedChat.ttl;
                            if (which == 0) {
                                currentEncryptedChat.ttl = 0;
                            } else if (which == 1) {
                                currentEncryptedChat.ttl = 2;
                            } else if (which == 2) {
                                currentEncryptedChat.ttl = 5;
                            } else if (which == 3) {
                                currentEncryptedChat.ttl = 60;
                            } else if (which == 4) {
                                currentEncryptedChat.ttl = 60 * 60;
                            } else if (which == 5) {
                                currentEncryptedChat.ttl = 60 * 60 * 24;
                            } else if (which == 6) {
                                currentEncryptedChat.ttl = 60 * 60 * 24 * 7;
                            }
                            if (oldValue != currentEncryptedChat.ttl) {
                                MessagesController.getInstance().sendTTLMessage(currentEncryptedChat);
                                MessagesStorage.getInstance().updateEncryptedChat(currentEncryptedChat);
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showAlertDialog(builder);
                }
            });
            timerButton.setTime(currentEncryptedChat.ttl);
        }

        if (avatarImageView != null) {
            TLRPC.FileLocation photo = null;
            int placeHolderId = 0;
            if (currentUser != null) {
                if (currentUser.photo != null) {
                    photo = currentUser.photo.photo_small;
                }
                placeHolderId = Utilities.getUserAvatarForId(currentUser.id);
            } else if (currentChat != null) {
                if (currentChat.photo != null) {
                    photo = currentChat.photo.photo_small;
                }
                placeHolderId = Utilities.getGroupAvatarForId(currentChat.id);
            }
            avatarImageView.setImage(photo, "50_50", placeHolderId);
        }
    }

    private void addToLoadingFile(String path, ProgressBar bar) {
        ArrayList<ProgressBar> arr = loadingFile.get(path);
        if (arr == null) {
            arr = new ArrayList<ProgressBar>();
            loadingFile.put(path, arr);
        }
        arr.add(bar);
    }

    private void removeFromloadingFile(String path, ProgressBar bar) {
        ArrayList<ProgressBar> arr = loadingFile.get(path);
        if (arr != null) {
            arr.remove(bar);
        }
    }

    private void updateOnlineCount() {
        if (info == null) {
            return;
        }
        onlineCount = 0;
        int currentTime = ConnectionsManager.getInstance().getCurrentTime();
        for (TLRPC.TL_chatParticipant participant : info.participants) {
            TLRPC.User user = MessagesController.getInstance().users.get(participant.user_id);
            if (user != null && user.status != null && (user.status.expires > currentTime || user.id == UserConfig.getClientUserId()) && user.status.expires > 10000) {
                onlineCount++;
            }
        }

        updateSubtitle();
    }

    private int getMessageType(MessageObject messageObject) {
        if (currentEncryptedChat == null) {
            if (messageObject.messageOwner.id <= 0 && messageObject.isOut()) {
                if (messageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                    if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                        return 0;
                    } else {
                        return 6;
                    }
                } else {
                    return -1;
                }
            } else {
                if (messageObject.type == 7) {
                    return -1;
                } else if (messageObject.type == 10 || messageObject.type == 11) {
                    if (messageObject.messageOwner.id == 0) {
                        return -1;
                    }
                    return 1;
                } else {
                    if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo ||
                                messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto ||
                                messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            boolean canSave = false;
                            if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() != 0) {
                                File f = new File(messageObject.messageOwner.attachPath);
                                if (f.exists()) {
                                    canSave = true;
                                }
                            }
                            if (!canSave) {
                                File f = new File(AndroidUtilities.getCacheDir(), messageObject.getFileName());
                                if (f.exists()) {
                                    canSave = true;
                                }
                            }
                            if (canSave) {
                                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    String mime = messageObject.messageOwner.media.document.mime_type;
                                    if (mime != null && mime.endsWith("/xml")) {
                                        return 5;
                                    }
                                }
                                return 4;
                            }
                        }
                        return 2;
                    } else {
                        return 3;
                    }
                }
            }
        } else {
            if (messageObject.type == 7) {
                return -1;
            } else if (messageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                    return 0;
                } else {
                    return 6;
                }
            } else if (messageObject.type == 10 || messageObject.type == 11 || messageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                if (messageObject.messageOwner.id == 0) {
                    return -1;
                }
                return 1;
            } else {
                if (!(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo ||
                            messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto ||
                            messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        boolean canSave = false;
                        if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() != 0) {
                            File f = new File(messageObject.messageOwner.attachPath);
                            if (f.exists()) {
                                canSave = true;
                            }
                        }
                        if (!canSave) {
                            File f = new File(AndroidUtilities.getCacheDir(), messageObject.getFileName());
                            if (f.exists()) {
                                canSave = true;
                            }
                        }
                        if (canSave) {
                            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                String mime = messageObject.messageOwner.media.document.mime_type;
                                if (mime != null && mime.endsWith("text/xml")) {
                                    return 5;
                                }
                            }
                            //return 4;
                        }
                    }
                    return 2;
                } else {
                    return 3;
                }
            }
        }
    }

    private void addToSelectedMessages(MessageObject messageObject) {
        if (selectedMessagesIds.containsKey(messageObject.messageOwner.id)) {
            selectedMessagesIds.remove(messageObject.messageOwner.id);
            if (messageObject.type == 0) {
                selectedMessagesCanCopyIds.remove(messageObject.messageOwner.id);
            }
        } else {
            selectedMessagesIds.put(messageObject.messageOwner.id, messageObject);
            if (messageObject.type == 0) {
                selectedMessagesCanCopyIds.put(messageObject.messageOwner.id, messageObject);
            }
        }
        if (actionBarLayer.isActionModeShowed()) {
            if (selectedMessagesIds.isEmpty()) {
                actionBarLayer.hideActionMode();
            }
            actionBarLayer.createActionMode().getItem(copy).setVisibility(selectedMessagesCanCopyIds.size() != 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void processRowSelect(View view) {
        View parentView = getRowParentView(view);
        if (parentView == null) {
            return;
        }
        MessageObject message = null;
        if (view instanceof ChatBaseCell) {
            message = ((ChatBaseCell)view).getMessageObject();
        } else {
            ChatListRowHolderEx holder = (ChatListRowHolderEx)parentView.getTag();
            message = holder.message;
        }

        int type = getMessageType(message);

        if (type < 2 || type == 6) {
            return;
        }
        addToSelectedMessages(message);
        updateActionModeTitle();
        updateVisibleRows();
    }

    private void updateActionModeTitle() {
        if (!actionBarLayer.isActionModeShowed()) {
            return;
        }
        if (!selectedMessagesIds.isEmpty()) {
            selectedMessagesCountTextView.setText(LocaleController.formatString("Selected", R.string.Selected, selectedMessagesIds.size()));
        }
    }

    private void updateSubtitle() {
        if (currentChat != null) {
            actionBarLayer.setTitle(currentChat.title);
        } else if (currentUser != null) {
            if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance().contactsDict.get(currentUser.id) == null && (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts())) {
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    actionBarLayer.setTitle(PhoneFormat.getInstance().format("+" + currentUser.phone));
                } else {
                    actionBarLayer.setTitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
                }
            } else {
                actionBarLayer.setTitle(Utilities.formatName(currentUser.first_name, currentUser.last_name));
            }
        }

        CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
        if (printString == null || printString.length() == 0) {
            lastPrintString = null;
            setTypingAnimation(false);
            if (currentChat != null) {
                if (currentChat instanceof TLRPC.TL_chatForbidden) {
                    actionBarLayer.setSubtitle(LocaleController.getString("YouWereKicked", R.string.YouWereKicked));
                } else if (currentChat.left) {
                    actionBarLayer.setSubtitle(LocaleController.getString("YouLeft", R.string.YouLeft));
                } else {
                    if (onlineCount > 0 && currentChat.participants_count != 0) {
                        actionBarLayer.setSubtitle(String.format("%s, %d %s", LocaleController.formatPluralString("Members", currentChat.participants_count), onlineCount, LocaleController.getString("Online", R.string.Online)));
                    } else {
                        actionBarLayer.setSubtitle(LocaleController.formatPluralString("Members", currentChat.participants_count));
                    }
                }
            } else if (currentUser != null) {
                TLRPC.User user = MessagesController.getInstance().users.get(currentUser.id);
                if (user != null) {
                    currentUser = user;
                }
                actionBarLayer.setSubtitle(LocaleController.formatUserStatus(currentUser));
            }
        } else {
            lastPrintString = printString;
            actionBarLayer.setSubtitle(printString);
            setTypingAnimation(true);
        }
    }

    private void checkAndUpdateAvatar() {
        TLRPC.FileLocation newPhoto = null;
        int placeHolderId = 0;
        if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance().users.get(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
            if (currentUser.photo != null) {
                newPhoto = currentUser.photo.photo_small;
            }
            placeHolderId = Utilities.getUserAvatarForId(currentUser.id);
        } else if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance().chats.get(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
            if (currentChat.photo != null) {
                newPhoto = currentChat.photo.photo_small;
            }
            placeHolderId = Utilities.getGroupAvatarForId(currentChat.id);
        }
        if (avatarImageView != null) {
            avatarImageView.setImage(newPhoto, "50_50", placeHolderId);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {
                Utilities.addMediaToGallery(currentPicturePath);
                processSendingPhoto(currentPicturePath, null);
                currentPicturePath = null;
            } else if (requestCode == 1) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                processSendingPhoto(null, data.getData());
            } else if (requestCode == 2) {
                String videoPath = null;
                if (data != null) {
                    Uri uri = data.getData();
                    boolean fromCamera = false;
                    if (uri != null && uri.getScheme() != null) {
                        fromCamera = uri.getScheme().contains("file");
                    } else if (uri == null) {
                        fromCamera = true;
                    }
                    if (fromCamera) {
                        if (uri != null) {
                            videoPath = uri.getPath();
                        } else {
                            videoPath = currentPicturePath;
                        }
                        Utilities.addMediaToGallery(currentPicturePath);
                        currentPicturePath = null;
                    } else {
                        try {
                            videoPath = Utilities.getPath(uri);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
                if (videoPath == null && currentPicturePath != null) {
                    File f = new File(currentPicturePath);
                    if (f.exists()) {
                        videoPath = currentPicturePath;
                    }
                    currentPicturePath = null;
                }
                /*if(android.os.Build.VERSION.SDK_INT >= 10) {
                    Bundle args = new Bundle();
                    args.putString("videoPath", videoPath);
                    VideoEditorActivity fragment = new VideoEditorActivity(args);
                    fragment.setDelegate(this);
                    presentFragment(fragment);
                } else {*/
                    processSendingVideo(videoPath);
                //}
            } else if (requestCode == 21) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                String tempPath = Utilities.getPath(data.getData());
                String originalPath = tempPath;
                if (tempPath == null) {
                    originalPath = data.toString();
                    tempPath = MediaController.copyDocumentToCache(data.getData(), "file");
                }
                if (tempPath == null) {
                    showAttachmentError();
                    return;
                }
                processSendingDocument(tempPath, originalPath);
            }
        }
    }

    @Override
    public void didFinishedVideoConverting(String videoPath) {
        processSendingVideo(videoPath);
    }

    private void showAttachmentError() {
        if (getParentActivity() == null) {
            return;
        }
        Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (currentPicturePath != null) {
            args.putString("path", currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        currentPicturePath = args.getString("path");
    }

    public boolean processSendingText(String text) {
        return chatActivityEnterView.processSendingText(text);
    }

    public void processSendingPhoto(String imageFilePath, Uri imageUri) {
        ArrayList<String> paths = null;
        ArrayList<Uri> uris = null;
        if (imageFilePath != null && imageFilePath.length() != 0) {
            paths = new ArrayList<String>();
            paths.add(imageFilePath);
        }
        if (imageUri != null) {
            uris = new ArrayList<Uri>();
            uris.add(imageUri);
        }
        processSendingPhotos(paths, uris);
    }

    public void processSendingPhotos(ArrayList<String> paths, ArrayList<Uri> uris) {
        if (paths == null && uris == null || paths != null && paths.isEmpty() || uris != null && uris.isEmpty()) {
            return;
        }
        final ArrayList<String> pathsCopy = new ArrayList<String>();
        final ArrayList<Uri> urisCopy = new ArrayList<Uri>();
        if (paths != null) {
            pathsCopy.addAll(paths);
        }
        if (uris != null) {
            urisCopy.addAll(uris);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> sendAsDocuments = null;
                ArrayList<String> sendAsDocumentsOriginal = null;
                int count = !pathsCopy.isEmpty() ? pathsCopy.size() : urisCopy.size();
                String path = null;
                Uri uri = null;
                for (int a = 0; a < count; a++) {
                    if (!pathsCopy.isEmpty()) {
                        path = pathsCopy.get(a);
                    } else if (!urisCopy.isEmpty()) {
                        uri = urisCopy.get(a);
                    }

                    String originalPath = path;
                    String tempPath = path;
                    if (tempPath == null && uri != null) {
                        tempPath = Utilities.getPath(uri);
                        originalPath = uri.toString();
                    }

                    boolean isGif = false;
                    if (tempPath != null && tempPath.endsWith(".gif")) {
                        isGif = true;
                    } else if (tempPath == null && uri != null) {
                        isGif = MediaController.isGif(uri);
                        if (isGif) {
                            originalPath = uri.toString();
                            tempPath = MediaController.copyDocumentToCache(uri, "gif");
                        }
                    }

                    if (isGif) {
                        if (sendAsDocuments == null) {
                            sendAsDocuments = new ArrayList<String>();
                            sendAsDocumentsOriginal = new ArrayList<String>();
                        }
                        sendAsDocuments.add(tempPath);
                        sendAsDocumentsOriginal.add(originalPath);
                    } else {
                        if (tempPath != null) {
                            File temp = new File(tempPath);
                            originalPath += temp.length() + "_" + temp.lastModified();
                        }
                        TLRPC.TL_photo photo = (TLRPC.TL_photo)MessagesStorage.getInstance().getSentFile(originalPath, currentEncryptedChat == null ? 0 : 3);
                        if (photo == null && uri != null) {
                            photo = (TLRPC.TL_photo)MessagesStorage.getInstance().getSentFile(Utilities.getPath(uri), currentEncryptedChat == null ? 0 : 3);
                        }
                        if (photo == null) {
                            photo = MessagesController.getInstance().generatePhotoSizes(path, uri);
                        }
                        if (photo != null) {
                            final String originalPathFinal = originalPath;
                            final TLRPC.TL_photo photoFinal = photo;
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    MessagesController.getInstance().sendMessage(photoFinal, originalPathFinal, dialog_id);
                                    if (chatListView != null) {
                                        chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                    }
                                    if (paused) {
                                        scrollToTopOnResume = true;
                                    }
                                }
                            });
                        }
                    }
                }
                if (sendAsDocuments != null && !sendAsDocuments.isEmpty()) {
                    for (int a = 0; a < sendAsDocuments.size(); a++) {
                        processSendingDocumentInternal(sendAsDocuments.get(a), sendAsDocumentsOriginal.get(a));
                    }
                }
            }
        }).start();
    }

    private void processSendingDocumentInternal(String path, String originalPath) {
        if (path == null || path.length() == 0) {
            return;
        }
        final File f = new File(path);
        if (!f.exists() || f.length() == 0) {
            return;
        }

        String name = f.getName();
        if (name == null) {
            name = "noname";
        }
        String ext = "";
        int idx = path.lastIndexOf(".");
        if (idx != -1) {
            ext = path.substring(idx + 1);
        }
        if (originalPath != null) {
            originalPath += "" + f.length();
        }

        TLRPC.TL_document document = (TLRPC.TL_document)MessagesStorage.getInstance().getSentFile(originalPath, currentEncryptedChat == null ? 1 : 4);
        if (document == null && !path.equals(originalPath)) {
            document = (TLRPC.TL_document)MessagesStorage.getInstance().getSentFile(path + f.length(), currentEncryptedChat == null ? 1 : 4);
        }
        if (document == null) {
            document = new TLRPC.TL_document();
            document.id = 0;
            document.user_id = UserConfig.getClientUserId();
            document.date = ConnectionsManager.getInstance().getCurrentTime();
            document.file_name = name;
            document.size = (int)f.length();
            document.dc_id = 0;
            if (ext.length() != 0) {
                MimeTypeMap myMime = MimeTypeMap.getSingleton();
                String mimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                if (mimeType != null) {
                    document.mime_type = mimeType;
                } else {
                    document.mime_type = "application/octet-stream";
                }
            } else {
                document.mime_type = "application/octet-stream";
            }
            if (document.mime_type.equals("image/gif")) {
                try {
                    Bitmap bitmap = FileLoader.loadBitmap(f.getAbsolutePath(), null, 90, 90);
                    if (bitmap != null) {
                        document.thumb = FileLoader.scaleAndSaveImage(bitmap, 90, 90, 55, currentEncryptedChat != null);
                        document.thumb.type = "s";
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            if (document.thumb == null) {
                document.thumb = new TLRPC.TL_photoSizeEmpty();
                document.thumb.type = "s";
            }
        }
        document.path = path;

        final TLRPC.TL_document documentFinal = document;
        final String originalPathFinal = originalPath;
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance().sendMessage(documentFinal, originalPathFinal, dialog_id);
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                }
                if (paused) {
                    scrollToTopOnResume = true;
                }
            }
        });
    }

    public void processSendingDocument(String path, String originalPath) {
        if (path == null || originalPath == null) {
            return;
        }
        ArrayList<String> paths = new ArrayList<String>();
        ArrayList<String> originalPaths = new ArrayList<String>();
        paths.add(path);
        originalPaths.add(originalPath);
        processSendingDocuments(paths, originalPaths);
    }

    public void processSendingDocuments(final ArrayList<String> paths, final ArrayList<String> originalPaths) {
        if (paths == null && originalPaths == null || paths != null && originalPaths != null && paths.size() != originalPaths.size()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int a = 0; a < paths.size(); a++) {
                    processSendingDocumentInternal(paths.get(a), originalPaths.get(a));
                }
            }
        }).start();
    }

    public void processSendingVideo(final String videoPath) {
        if (videoPath == null || videoPath.length() == 0) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String originalPath = videoPath;
                File temp = new File(originalPath);
                originalPath += temp.length() + "_" + temp.lastModified();
                TLRPC.TL_video video = (TLRPC.TL_video)MessagesStorage.getInstance().getSentFile(originalPath, currentEncryptedChat == null ? 2 : 5);
                if (video == null) {
                    Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                    TLRPC.PhotoSize size = FileLoader.scaleAndSaveImage(thumb, 90, 90, 55, currentEncryptedChat != null);
                    if (size == null) {
                        return;
                    }
                    size.type = "s";
                    video = new TLRPC.TL_video();
                    video.thumb = size;
                    video.caption = "";
                    video.mime_type = "video/mp4";
                    video.id = 0;
                    if (temp != null && temp.exists()) {
                        video.size = (int) temp.length();
                    }
                    UserConfig.lastLocalId--;
                    UserConfig.saveConfig(false);

                    MediaPlayer mp = MediaPlayer.create(ApplicationLoader.applicationContext, Uri.fromFile(new File(videoPath)));
                    if (mp == null) {
                        return;
                    }
                    video.duration = (int) Math.ceil(mp.getDuration() / 1000.0f);
                    video.w = mp.getVideoWidth();
                    video.h = mp.getVideoHeight();
                    mp.release();
                }
                video.path = videoPath;

                final TLRPC.TL_video videoFinal = video;
                final String originalPathFinal = originalPath;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        MessagesController.getInstance().sendMessage(videoFinal, originalPathFinal, dialog_id);
                        if (chatListView != null) {
                            chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                        }
                        if (paused) {
                            scrollToTopOnResume = true;
                        }
                    }
                });
            }
        }).start();
    }

    private void removeUnreadPlane(boolean reload) {
        if (unreadMessageObject != null) {
            messages.remove(unreadMessageObject);
            unread_end_reached = true;
            first_unread_id = 0;
            last_unread_id = 0;
            unread_to_load = 0;
            unreadMessageObject = null;
            if (reload) {
                chatAdapter.notifyDataSetChanged();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == MessagesController.messagesDidLoaded) {
            long did = (Long)args[0];
            if (did == dialog_id) {
                int offset = (Integer)args[1];
                int count = (Integer)args[2];
                boolean isCache = (Boolean)args[4];
                int fnid = (Integer)args[5];
                int last_unread_date = (Integer)args[8];
                boolean forwardLoad = (Boolean)args[9];
                boolean wasUnread = false;
                boolean positionToUnread = false;
                if (fnid != 0) {
                    first_unread_id = (Integer)args[5];
                    last_unread_id = (Integer)args[6];
                    unread_to_load = (Integer)args[7];
                    positionToUnread = true;
                }
                ArrayList<MessageObject> messArr = (ArrayList<MessageObject>)args[3];

                int newRowsCount = 0;
                unread_end_reached = last_unread_id == 0;
                for (int a = 0; a < messArr.size(); a++) {
                    MessageObject obj = messArr.get(a);
                    if (messagesDict.containsKey(obj.messageOwner.id)) {
                        continue;
                    }

                    if (obj.messageOwner.id > 0) {
                        maxMessageId = Math.min(obj.messageOwner.id, maxMessageId);
                        minMessageId = Math.max(obj.messageOwner.id, minMessageId);
                    } else if (currentEncryptedChat != null) {
                        maxMessageId = Math.max(obj.messageOwner.id, maxMessageId);
                        minMessageId = Math.min(obj.messageOwner.id, minMessageId);
                    }
                    maxDate = Math.max(maxDate, obj.messageOwner.date);
                    if (minDate == 0 || obj.messageOwner.date < minDate) {
                        minDate = obj.messageOwner.date;
                    }
                    if (!obj.isOut() && obj.messageOwner.unread) {
                        wasUnread = true;
                    }
                    messagesDict.put(obj.messageOwner.id, obj);
                    ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);

                    if (dayArray == null) {
                        dayArray = new ArrayList<MessageObject>();
                        messagesByDays.put(obj.dateKey, dayArray);

                        TLRPC.Message dateMsg = new TLRPC.Message();
                        dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                        dateMsg.id = 0;
                        MessageObject dateObj = new MessageObject(dateMsg, null);
                        dateObj.contentType = dateObj.type = 10;
                        if (forwardLoad) {
                            messages.add(0, dateObj);
                        } else {
                            messages.add(dateObj);
                        }
                        newRowsCount++;
                    }

                    newRowsCount++;
                    dayArray.add(obj);
                    if (forwardLoad) {
                        messages.add(0, obj);
                    } else {
                        messages.add(messages.size() - 1, obj);
                    }

                    if (!forwardLoad) {
                        if (obj.messageOwner.id == first_unread_id) {
                            TLRPC.Message dateMsg = new TLRPC.Message();
                            dateMsg.message = "";
                            dateMsg.id = 0;
                            MessageObject dateObj = new MessageObject(dateMsg, null);
                            dateObj.contentType = dateObj.type = 7;
                            boolean dateAdded = true;
                            if (a != messArr.size() - 1) {
                                MessageObject next = messArr.get(a + 1);
                                dateAdded = !next.dateKey.equals(obj.dateKey);
                            }
                            messages.add(messages.size() - (dateAdded ? 0 : 1), dateObj);
                            unreadMessageObject = dateObj;
                            newRowsCount++;
                        }
                        if (obj.messageOwner.id == last_unread_id) {
                            unread_end_reached = true;
                        }
                    }

                }

                if (unread_end_reached) {
                    first_unread_id = 0;
                    last_unread_id = 0;
                }

                if (forwardLoad) {
                    if (messArr.size() != count) {
                        unread_end_reached = true;
                        first_unread_id = 0;
                        last_unread_id = 0;
                    }

                    chatAdapter.notifyDataSetChanged();
                    loadingForward = false;
                } else {
                    if (messArr.size() != count) {
                        if (isCache) {
                            cacheEndReaced = true;
                            if (currentEncryptedChat != null) {
                                endReached = true;
                            }
                        } else {
                            cacheEndReaced = true;
                            endReached = true;
                        }
                    }
                    loading = false;

                    if (chatListView != null) {
                        if (first || scrollToTopOnResume) {
                            chatAdapter.notifyDataSetChanged();
                            if (positionToUnread && unreadMessageObject != null) {
                                if (messages.get(messages.size() - 1) == unreadMessageObject) {
                                    chatListView.setSelectionFromTop(0, AndroidUtilities.dp(-11));
                                } else {
                                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), AndroidUtilities.dp(-11));
                                }
                                ViewTreeObserver obs = chatListView.getViewTreeObserver();
                                obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (!messages.isEmpty()) {
                                            if (messages.get(messages.size() - 1) == unreadMessageObject) {
                                                chatListView.setSelectionFromTop(0, AndroidUtilities.dp(-11));
                                            } else {
                                                chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), AndroidUtilities.dp(-11));
                                            }
                                        }
                                        chatListView.getViewTreeObserver().removeOnPreDrawListener(this);
                                        return false;
                                    }
                                });
                                chatListView.invalidate();
                                showPagedownButton(true, true);
                            } else {
                                chatListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                    }
                                });
                            }
                        } else {
                            int firstVisPos = chatListView.getLastVisiblePosition();
                            View firstVisView = chatListView.getChildAt(chatListView.getChildCount() - 1);
                            int top = ((firstVisView == null) ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                            chatAdapter.notifyDataSetChanged();
                            chatListView.setSelectionFromTop(firstVisPos + newRowsCount - (endReached ? 1 : 0), top);
                        }

                        if (paused) {
                            scrollToTopOnResume = true;
                            if (positionToUnread && unreadMessageObject != null) {
                                scrollToTopUnReadOnResume = true;
                            }
                        }

                        if (first) {
                            if (chatListView.getEmptyView() == null) {
                                if (currentEncryptedChat == null) {
                                    chatListView.setEmptyView(emptyView);
                                } else {
                                    chatListView.setEmptyView(secretChatPlaceholder);
                                }
                            }
                        }
                    } else {
                        scrollToTopOnResume = true;
                        if (positionToUnread && unreadMessageObject != null) {
                            scrollToTopUnReadOnResume = true;
                        }
                    }
                }

                if (first && messages.size() > 0) {
                    if (last_unread_id != 0) {
                        MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, last_unread_id, 0, last_unread_date, wasUnread);
                    } else {
                        MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, minMessageId, 0, maxDate, wasUnread);
                    }
                    first = false;
                }

                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
            }
        } else if (id == 999) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == MessagesController.updateInterfaces) {
            int updateMask = (Integer)args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0) {
                updateSubtitle();
                updateOnlineCount();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_NAME) != 0) {
                checkAndUpdateAvatar();
                updateVisibleRows();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                CharSequence printString = MessagesController.getInstance().printingStrings.get(dialog_id);
                if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && printString != null && !lastPrintString.equals(printString)) {
                    updateSubtitle();
                }
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PHONE) != 0) {
                updateContactStatus();
            }
        } else if (id == MessagesController.didReceivedNewMessages) {
            long did = (Long)args[0];
            if (did == dialog_id) {

                boolean updateChat = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[1];

                if (!unread_end_reached) {
                    int currentMaxDate = Integer.MIN_VALUE;
                    int currentMinMsgId = Integer.MIN_VALUE;
                    if (currentEncryptedChat != null) {
                        currentMinMsgId = Integer.MAX_VALUE;
                    }
                    boolean currentMarkAsRead = false;

                    for (MessageObject obj : arr) {
                        if (currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageActionTTLChange && timerButton != null) {
                            timerButton.setTime(obj.messageOwner.action.ttl);
                        }
                        if (obj.isOut() && obj.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                            scrollToLastMessage();
                            return;
                        }
                        if (messagesDict.containsKey(obj.messageOwner.id)) {
                            continue;
                        }
                        currentMaxDate = Math.max(currentMaxDate, obj.messageOwner.date);
                        if (obj.messageOwner.id > 0) {
                            currentMinMsgId = Math.max(obj.messageOwner.id, currentMinMsgId);
                        } else if (currentEncryptedChat != null) {
                            currentMinMsgId = Math.min(obj.messageOwner.id, currentMinMsgId);
                        }

                        if (!obj.isOut() && obj.messageOwner.unread) {
                            unread_to_load++;
                            currentMarkAsRead = true;
                        }
                        if (obj.type == 10 || obj.type == 11) {
                            updateChat = true;
                        }
                    }

                    if (currentMarkAsRead) {
                        if (paused) {
                            readWhenResume = true;
                            readWithDate = currentMaxDate;
                            readWithMid = currentMinMsgId;
                        } else {
                            if (messages.size() > 0) {
                                MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, currentMinMsgId, 0, currentMaxDate, true);
                            }
                        }
                    }
                    updateVisibleRows();
                } else {
                    boolean markAsRead = false;
                    int oldCount = messages.size();
                    for (MessageObject obj : arr) {
                        if (currentEncryptedChat != null && obj.messageOwner.action != null && obj.messageOwner.action instanceof TLRPC.TL_messageActionTTLChange && timerButton != null) {
                            timerButton.setTime(obj.messageOwner.action.ttl);
                        }
                        if (messagesDict.containsKey(obj.messageOwner.id)) {
                            continue;
                        }
                        if (minDate == 0 || obj.messageOwner.date < minDate) {
                            minDate = obj.messageOwner.date;
                        }
                        if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                            progressBarMap.put(obj.messageOwner.attachPath, null);
                        }

                        if (obj.isOut()) {
                            removeUnreadPlane(false);
                        }

                        if (!obj.isOut() && unreadMessageObject != null) {
                            unread_to_load++;
                        }

                        if (obj.messageOwner.id > 0) {
                            maxMessageId = Math.min(obj.messageOwner.id, maxMessageId);
                            minMessageId = Math.max(obj.messageOwner.id, minMessageId);
                        } else if (currentEncryptedChat != null) {
                            maxMessageId = Math.max(obj.messageOwner.id, maxMessageId);
                            minMessageId = Math.min(obj.messageOwner.id, minMessageId);
                        }
                        maxDate = Math.max(maxDate, obj.messageOwner.date);
                        messagesDict.put(obj.messageOwner.id, obj);
                        ArrayList<MessageObject> dayArray = messagesByDays.get(obj.dateKey);
                        if (dayArray == null) {
                            dayArray = new ArrayList<MessageObject>();
                            messagesByDays.put(obj.dateKey, dayArray);

                            TLRPC.Message dateMsg = new TLRPC.Message();
                            dateMsg.message = LocaleController.formatDateChat(obj.messageOwner.date);
                            dateMsg.id = 0;
                            MessageObject dateObj = new MessageObject(dateMsg, null);
                            dateObj.contentType = dateObj.type = 10;
                            messages.add(0, dateObj);
                        }
                        if (!obj.isOut() && obj.messageOwner.unread) {
                            if (!paused) {
                                obj.messageOwner.unread = false;
                            }
                            markAsRead = true;
                        }
                        dayArray.add(0, obj);
                        messages.add(0, obj);
                        if (obj.type == 10 || obj.type == 11) {
                            updateChat = true;
                        }
                    }
                    if (progressView != null) {
                        progressView.setVisibility(View.GONE);
                    }
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    } else {
                        scrollToTopOnResume = true;
                    }

                    if (chatListView != null && chatAdapter != null) {
                        int lastVisible = chatListView.getLastVisiblePosition();
                        if (endReached) {
                            lastVisible++;
                        }
                        if (lastVisible == oldCount) {
                            if (paused) {
                                scrollToTopOnResume = true;
                            } else {
                                chatListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                    }
                                });
                            }
                        } else {
                            showPagedownButton(true, true);
                        }
                    } else {
                        scrollToTopOnResume = true;
                    }

                    if (markAsRead) {
                        if (paused) {
                            readWhenResume = true;
                            readWithDate = maxDate;
                            readWithMid = minMessageId;
                        } else {
                            MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, minMessageId, 0, maxDate, true);
                        }
                    }
                }
                if (updateChat) {
                    updateSubtitle();
                    checkAndUpdateAvatar();
                }
            }
        } else if (id == MessagesController.closeChats) {
            removeSelfFromStack();
        } else if (id == MessagesController.messagesReaded) {
            ArrayList<Integer> markAsReadMessages = (ArrayList<Integer>)args[0];
            boolean updated = false;
            for (Integer ids : markAsReadMessages) {
                MessageObject obj = messagesDict.get(ids);
                if (obj != null) {
                    obj.messageOwner.unread = false;
                    updated = true;
                }
            }
            if (updated) {
                updateVisibleRows();
            }
        } else if (id == MessagesController.messagesDeleted) {
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>)args[0];
            boolean updated = false;
            for (Integer ids : markAsDeletedMessages) {
                MessageObject obj = messagesDict.get(ids);
                if (obj != null) {
                    int index = messages.indexOf(obj);
                    if (index != -1) {
                        messages.remove(index);
                        messagesDict.remove(ids);
                        ArrayList<MessageObject> dayArr = messagesByDays.get(obj.dateKey);
                        dayArr.remove(obj);
                        if (dayArr.isEmpty()) {
                            messagesByDays.remove(obj.dateKey);
                            messages.remove(index);
                        }
                        updated = true;
                    }
                }
            }
            if (messages.isEmpty()) {
                if (!endReached && !loading) {
                    progressView.setVisibility(View.GONE);
                    chatListView.setEmptyView(null);
                    if (currentEncryptedChat == null) {
                        maxMessageId = Integer.MAX_VALUE;
                        minMessageId = Integer.MIN_VALUE;
                    } else {
                        maxMessageId = Integer.MIN_VALUE;
                        minMessageId = Integer.MAX_VALUE;
                    }
                    maxDate = Integer.MIN_VALUE;
                    minDate = 0;
                    MessagesController.getInstance().loadMessages(dialog_id, 0, 30, 0, !cacheEndReaced, minDate, classGuid, false, false);
                    loading = true;
                }
            }
            if (updated && chatAdapter != null) {
                removeUnreadPlane(false);
                chatAdapter.notifyDataSetChanged();
            }
        } else if (id == MessagesController.messageReceivedByServer) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer)args[1];
                MessageObject newMsgObj = (MessageObject)args[2];
                if (newMsgObj != null) {
                    obj.messageOwner.media = newMsgObj.messageOwner.media;
                }
                messagesDict.remove(msgId);
                messagesDict.put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SENT;
                updateVisibleRows();
                if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                    progressBarMap.remove(obj.messageOwner.attachPath);
                }
            }
        } else if (id == MessagesController.messageReceivedByAck) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                    progressBarMap.remove(obj.messageOwner.attachPath);
                }
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SENT;
                updateVisibleRows();
            }
        } else if (id == MessagesController.messageSendError) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = messagesDict.get(msgId);
            if (obj != null) {
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SEND_ERROR;
                updateVisibleRows();
                if (obj.messageOwner.attachPath != null && obj.messageOwner.attachPath.length() != 0) {
                    progressBarMap.remove(obj.messageOwner.attachPath);
                }
            }
        } else if (id == 997) {
            MessagesController.getInstance().sendMessage((Double) args[0], (Double) args[1], dialog_id);
            if (chatListView != null) {
                chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                scrollToTopOnResume = true;
            }
        } else if (id == MessagesController.chatInfoDidLoaded) {
            int chatId = (Integer)args[0];
            if (currentChat != null && chatId == currentChat.id) {
                info = (TLRPC.ChatParticipants)args[1];
                updateOnlineCount();
            }
        } else if (id == FileLoader.FileUploadProgressChanged) {
            String location = (String)args[0];
            boolean enc = (Boolean)args[2];
            if (enc && currentEncryptedChat == null) {
                return;
            } else if (!enc && currentEncryptedChat != null) {
                return;
            }
            ProgressBar bar;
            if ((bar = progressBarMap.get(location)) != null) {
                Float progress = (Float)args[1];
                bar.setProgress((int)(progress * 100));
            }
        } else if (id == FileLoader.FileDidFailedLoad) {
            String location = (String)args[0];
            if (loadingFile.containsKey(location)) {
                loadingFile.remove(location);
                updateVisibleRows();
            }
        } else if (id == FileLoader.FileDidLoaded) {
            String location = (String)args[0];
            if (loadingFile.containsKey(location)) {
                loadingFile.remove(location);
                updateVisibleRows();
            }
        } else if (id == FileLoader.FileLoadProgressChanged) {
            String location = (String)args[0];
            ArrayList<ProgressBar> arr = loadingFile.get(location);
            if (arr != null) {
                Float progress = (Float)args[1];
                for (ProgressBar bar : arr) {
                    bar.setProgress((int)(progress * 100));
                }
            }
        } else if (id == MessagesController.contactsDidLoaded) {
            updateContactStatus();
            updateSubtitle();
        } else if (id == MessagesController.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)args[0];
            if (currentEncryptedChat != null && chat.id == currentEncryptedChat.id) {
                currentEncryptedChat = chat;
                updateContactStatus();
                updateSecretStatus();
            }
        } else if (id == MessagesController.messagesReadedEncrypted) {
            int encId = (Integer)args[0];
            if (currentEncryptedChat != null && currentEncryptedChat.id == encId) {
                int date = (Integer)args[1];
                boolean started = false;
                for (MessageObject obj : messages) {
                    if (!obj.isOut()) {
                        continue;
                    } else if (obj.isOut() && !obj.messageOwner.unread) {
                        break;
                    }
                    if (obj.messageOwner.date <= date) {
                        obj.messageOwner.unread = false;
                    }
                }
                updateVisibleRows();
            }
        } else if (id == MediaController.audioDidReset) {
            Integer mid = (Integer)args[0];
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatAudioCell) {
                        ChatAudioCell cell = (ChatAudioCell)view;
                        if (cell.getMessageObject() != null && cell.getMessageObject().messageOwner.id == mid) {
                            cell.updateButtonState();
                            break;
                        }
                    }
                }
            }
        } else if (id == MediaController.audioProgressDidChanged) {
            Integer mid = (Integer)args[0];
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatAudioCell) {
                        ChatAudioCell cell = (ChatAudioCell)view;
                        if (cell.getMessageObject() != null && cell.getMessageObject().messageOwner.id == mid) {
                            cell.updateProgress();
                            break;
                        }
                    }
                }
            }
        } else if (id == MessagesController.removeAllMessagesFromDialog) {
            messages.clear();
            messagesByDays.clear();
            messagesDict.clear();
            progressView.setVisibility(View.GONE);
            if (currentEncryptedChat == null) {
                chatListView.setEmptyView(emptyView);
            } else {
                chatListView.setEmptyView(secretChatPlaceholder);
            }
            if (currentEncryptedChat == null) {
                maxMessageId = Integer.MAX_VALUE;
                minMessageId = Integer.MIN_VALUE;
            } else {
                maxMessageId = Integer.MIN_VALUE;
                minMessageId = Integer.MAX_VALUE;
            }
            maxDate = Integer.MIN_VALUE;
            minDate = 0;
            selectedMessagesIds.clear();
            selectedMessagesCanCopyIds.clear();
            actionBarLayer.hideActionMode();
            chatAdapter.notifyDataSetChanged();
        } else if (id == MediaController.screenshotTook) {
            updateInformationForScreenshotDetector();
        }
    }

    private void updateContactStatus() {
        if (topPanel == null) {
            return;
        }
        if (currentUser == null) {
            topPanel.setVisibility(View.GONE);
        } else {
            TLRPC.User user = MessagesController.getInstance().users.get(currentUser.id);
            if (user != null) {
                currentUser = user;
            }
            if (currentEncryptedChat != null && !(currentEncryptedChat instanceof TLRPC.TL_encryptedChat)
                    || currentUser.id / 1000 == 333
                    || currentUser.id / 1000 == 777
                    || currentUser instanceof TLRPC.TL_userEmpty || currentUser instanceof TLRPC.TL_userDeleted
                    || (currentUser.phone != null && currentUser.phone.length() != 0 &&
                    ContactsController.getInstance().contactsDict.get(currentUser.id) != null &&
                    (ContactsController.getInstance().contactsDict.size() != 0 || !ContactsController.getInstance().isLoadingContacts()))) {
                topPanel.setVisibility(View.GONE);
            } else {
                topPanel.setVisibility(View.VISIBLE);
                topPanelText.setShadowLayer(1, 0, AndroidUtilities.dp(1), 0xff8797a3);
                if (isCustomTheme) {
                    topPlaneClose.setImageResource(R.drawable.ic_msg_btn_cross_custom);
                    topPanel.setBackgroundResource(R.drawable.top_pane_custom);
                } else {
                    topPlaneClose.setImageResource(R.drawable.ic_msg_btn_cross_custom);
                    topPanel.setBackgroundResource(R.drawable.top_pane);
                }
                if (currentUser.phone != null && currentUser.phone.length() != 0) {
                    if (MessagesController.getInstance().hidenAddToContacts.get(currentUser.id) != null) {
                        topPanel.setVisibility(View.INVISIBLE);
                    } else {
                        topPanelText.setText(LocaleController.getString("AddToContacts", R.string.AddToContacts));
                        topPlaneClose.setVisibility(View.VISIBLE);
                        topPlaneClose.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MessagesController.getInstance().hidenAddToContacts.put(currentUser.id, currentUser);
                                topPanel.setVisibility(View.GONE);
                            }
                        });
                        topPanel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Bundle args = new Bundle();
                                args.putInt("user_id", currentUser.id);
                                presentFragment(new ContactAddActivity(args));
                            }
                        });
                    }
                } else {
                    if (MessagesController.getInstance().hidenAddToContacts.get(currentUser.id) != null) {
                        topPanel.setVisibility(View.INVISIBLE);
                    } else {
                        topPanelText.setText(LocaleController.getString("ShareMyContactInfo", R.string.ShareMyContactInfo));
                        topPlaneClose.setVisibility(View.GONE);
                        topPanel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setMessage(LocaleController.getString("AreYouSureShareMyContactInfo", R.string.AreYouSureShareMyContactInfo));
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        MessagesController.getInstance().hidenAddToContacts.put(currentUser.id, currentUser);
                                        topPanel.setVisibility(View.GONE);
                                        MessagesController.getInstance().sendMessage(UserConfig.getCurrentUser(), dialog_id);
                                        chatListView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                                            }
                                        });
                                    }
                                });
                                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                showAlertDialog(builder);
                            }
                        });
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        checkActionBarMenu();
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        NotificationsController.getInstance().setOpennedDialogId(dialog_id);
        if (scrollToTopOnResume) {
            if (scrollToTopUnReadOnResume && unreadMessageObject != null) {
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - messages.indexOf(unreadMessageObject), -chatListView.getPaddingTop() - AndroidUtilities.dp(7));
                }
            } else {
                if (chatListView != null) {
                    chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                }
            }
            scrollToTopUnReadOnResume = false;
            scrollToTopOnResume = false;
        }
        paused = false;
        if (readWhenResume && !messages.isEmpty()) {
            for (MessageObject messageObject : messages) {
                if (!messageObject.isUnread() && !messageObject.isFromMe()) {
                    break;
                }
                messageObject.messageOwner.unread = false;
            }
            readWhenResume = false;
            MessagesController.getInstance().markDialogAsRead(dialog_id, messages.get(0).messageOwner.id, readWithMid, 0, readWithDate, true);
        }

        fixLayout(true);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String lastMessageText = preferences.getString("dialog_" + dialog_id, null);
        if (lastMessageText != null) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove("dialog_" + dialog_id);
            editor.commit();
            chatActivityEnterView.setFieldText(lastMessageText);
        }
        chatActivityEnterView.setFieldFocused(true);
        if (currentEncryptedChat != null) {
            chatEnterTime = System.currentTimeMillis();
            chatLeaveTime = 0;
        }
    }

    @Override
    public void didSelectPhotos(ArrayList<String> photos) {
        processSendingPhotos(photos, null);
    }

    @Override
    public void startPhotoSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            getParentActivity().startActivityForResult(photoPickerIntent, 1);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onBeginSlide() {
        super.onBeginSlide();
        chatActivityEnterView.hideEmojiPopup();
    }

    private void setTypingAnimation(boolean start) {
        if (actionBarLayer == null) {
            return;
        }
        if (start) {
            try {
                if (currentChat != null) {
                    actionBarLayer.setSubTitleIcon(R.drawable.typing_dots_chat, AndroidUtilities.dp(4));
                } else {
                    actionBarLayer.setSubTitleIcon(R.drawable.typing_dots, AndroidUtilities.dp(4));
                }
                AnimationDrawable mAnim = (AnimationDrawable)actionBarLayer.getSubTitleIcon();
                mAnim.setAlpha(200);
                mAnim.start();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            actionBarLayer.setSubTitleIcon(0, 0);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        actionBarLayer.hideActionMode();
        chatActivityEnterView.hideEmojiPopup();
        paused = true;
        NotificationsController.getInstance().setOpennedDialogId(0);

        String text = chatActivityEnterView.getFieldText();
        if (text != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("dialog_" + dialog_id, text);
            editor.commit();
        }

        chatActivityEnterView.setFieldFocused(false);

        /*if (currentEncryptedChat != null) { disabled
            chatLeaveTime = System.currentTimeMillis();
            updateInformationForScreenshotDetector();
        }*/
    }

    private void updateInformationForScreenshotDetector() {
        ArrayList<Long> visibleMessages = new ArrayList<Long>();
        if (chatListView != null) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = chatListView.getChildAt(a);
                MessageObject object = null;
                if (view instanceof ChatBaseCell) {
                    ChatBaseCell cell = (ChatBaseCell) view;
                    object = cell.getMessageObject();
                } else {
                    Object tag = view.getTag();
                    if (tag instanceof ChatListRowHolderEx) {
                        ChatListRowHolderEx holder = (ChatListRowHolderEx) tag;
                        object = holder.message;
                    }
                }
                if (object != null && object.messageOwner.id < 0 && object.messageOwner.random_id != 0) {
                    visibleMessages.add(object.messageOwner.random_id);
                }
            }
        }
        MediaController.getInstance().setLastEncryptedChatParams(chatEnterTime, chatLeaveTime, currentEncryptedChat, visibleMessages);
    }

    private void fixLayout(final boolean resume) {
        final int lastPos = chatListView.getLastVisiblePosition();
        ViewTreeObserver obs = chatListView.getViewTreeObserver();
        obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (chatListView != null) {
                    chatListView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                if (getParentActivity() == null) {
                    return true;
                }
                int height = AndroidUtilities.dp(48);
                if (!Utilities.isTablet(getParentActivity()) && getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    height = AndroidUtilities.dp(40);
                    selectedMessagesCountTextView.setTextSize(16);
                } else {
                    selectedMessagesCountTextView.setTextSize(18);
                }
                if (avatarImageView != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) avatarImageView.getLayoutParams();
                    params.width = height;
                    params.height = height;
                    avatarImageView.setLayoutParams(params);
                }
                if (!resume && lastPos >= messages.size() - 1) {
                    chatListView.post(new Runnable() {
                        @Override
                        public void run() {
                            chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                        }
                    });
                }
                return false;
            }
        });
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        fixLayout(false);
    }

    private View getRowParentView(View v) {
        if (v instanceof ChatBaseCell) {
            return v;
        } else {
            while (!(v.getTag() instanceof ChatListRowHolderEx)) {
                ViewParent parent = v.getParent();
                if (!(parent instanceof View)) {
                    return null;
                }
                v = (View)v.getParent();
                if (v == null) {
                    return null;
                }
            }
            return v;
        }
    }

    public void createMenu(View v, boolean single) {
        if (actionBarLayer.isActionModeShowed()) {
            return;
        }

        View parentView = getRowParentView(v);
        if (parentView == null) {
            return;
        }
        MessageObject message = null;
        if (v instanceof ChatBaseCell) {
            message = ((ChatBaseCell)v).getMessageObject();
        } else {
            ChatListRowHolderEx holder = (ChatListRowHolderEx)parentView.getTag();
            message = holder.message;
        }
        final int type = getMessageType(message);

        selectedObject = null;
        forwaringMessage = null;
        selectedMessagesCanCopyIds.clear();
        selectedMessagesIds.clear();

        if (single || type < 2 || type == 6) {
            if (type >= 0) {
                selectedObject = message;
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                CharSequence[] items = null;

                if (type == 0) {
                    items = new CharSequence[] {LocaleController.getString("Retry", R.string.Retry), LocaleController.getString("Delete", R.string.Delete)};
                } else if (type == 1) {
                    items = new CharSequence[] {LocaleController.getString("Delete", R.string.Delete)};
                } else if (type == 6) {
                    items = new CharSequence[] {LocaleController.getString("Retry", R.string.Retry), LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Delete", R.string.Delete)};
                } else {
                    if (currentEncryptedChat == null) {
                        if (type == 2) {
                            items = new CharSequence[]{LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 3) {
                            items = new CharSequence[]{LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 4) {
                            items = new CharSequence[]{LocaleController.getString(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? "SaveToDownloads" : "SaveToGallery",
                                    selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? R.string.SaveToDownloads : R.string.SaveToGallery), LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 5) {
                            items = new CharSequence[]{LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile), LocaleController.getString("SaveToDownloads", R.string.SaveToDownloads), LocaleController.getString("Forward", R.string.Forward), LocaleController.getString("Delete", R.string.Delete)};
                        }
                    } else {
                        if (type == 2) {
                            items = new CharSequence[]{LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 3) {
                            items = new CharSequence[]{LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 4) {
                            items = new CharSequence[]{LocaleController.getString(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? "SaveToDownloads" : "SaveToGallery",
                                    selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument ? R.string.SaveToDownloads : R.string.SaveToGallery), LocaleController.getString("Delete", R.string.Delete)};
                        } else if (type == 5) {
                            items = new CharSequence[]{LocaleController.getString("ApplyLocalizationFile", R.string.ApplyLocalizationFile), LocaleController.getString("Delete", R.string.Delete)};
                        }
                    }
                }

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (selectedObject == null) {
                            return;
                        }
                        if (type == 0) {
                            if (i == 0) {
                                processSelectedOption(0);
                            } else if (i == 1) {
                                processSelectedOption(1);
                            }
                        } else if (type == 1) {
                            processSelectedOption(1);
                        } else if (type == 2) {
                            if (currentEncryptedChat == null) {
                                if (i == 0) {
                                    processSelectedOption(2);
                                } else if (i == 1) {
                                    processSelectedOption(1);
                                }
                            } else {
                                processSelectedOption(1);
                            }
                        } else if (type == 3) {
                            if (currentEncryptedChat == null) {
                                if (i == 0) {
                                    processSelectedOption(2);
                                } else if (i == 1) {
                                    processSelectedOption(3);
                                } else if (i == 2) {
                                    processSelectedOption(1);
                                }
                            } else {
                                if (i == 0) {
                                    processSelectedOption(3);
                                } else if (i == 1) {
                                    processSelectedOption(1);
                                }
                            }
                        } else if (type == 4) {
                            if (currentEncryptedChat == null) {
                                if (i == 0) {
                                    processSelectedOption(4);
                                } else if (i == 1) {
                                    processSelectedOption(2);
                                } else if (i == 2) {
                                    processSelectedOption(1);
                                }
                            } else {
                                if (i == 0) {

                                } else if (i == 1) {
                                    processSelectedOption(1);
                                }
                            }
                        } else if (type == 5) {
                            if (i == 0) {
                                processSelectedOption(5);
                            } else {
                                if (currentEncryptedChat == null) {
                                    if (i == 1) {
                                        processSelectedOption(4);
                                    } else if (i == 2) {
                                        processSelectedOption(2);
                                    } else if (i == 3) {
                                        processSelectedOption(1);
                                    }
                                } else {
                                    if (i == 1) {
                                        processSelectedOption(1);
                                    }
                                }
                            }
                        } else if (type == 6) {
                            if (i == 0) {
                                processSelectedOption(0);
                            } else if (i == 1) {
                                processSelectedOption(3);
                            } else if (i == 2) {
                                processSelectedOption(1);
                            }
                        }
                    }
                });

                builder.setTitle(LocaleController.getString("Message", R.string.Message));
                showAlertDialog(builder);
            }
            return;
        }
        actionBarLayer.showActionMode();
        addToSelectedMessages(message);
        updateActionModeTitle();
        updateVisibleRows();
    }

    private void processSelectedOption(int option) {
        if (selectedObject == null) {
            return;
        }
        if (option == 0) {
            if (selectedObject.messageOwner.id < 0) {
                if (selectedObject.type == 0) {
                    if (selectedObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                        MessagesController.getInstance().sendMessage(selectedObject, dialog_id);
                    } else {
                        MessagesController.getInstance().sendMessage(selectedObject.messageOwner.message, dialog_id);
                    }
                } else if (selectedObject.type == 4) {
                    MessagesController.getInstance().sendMessage(selectedObject.messageOwner.media.geo.lat, selectedObject.messageOwner.media.geo._long, dialog_id);
                } else if (selectedObject.type == 1) {
                    if (selectedObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                        MessagesController.getInstance().sendMessage(selectedObject, dialog_id);
                    } else {
                        TLRPC.TL_photo photo = (TLRPC.TL_photo)selectedObject.messageOwner.media.photo;
                        MessagesController.getInstance().sendMessage(photo, selectedObject.messageOwner.attachPath, dialog_id);
                    }
                } else if (selectedObject.type == 3) {
                    if (selectedObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                        MessagesController.getInstance().sendMessage(selectedObject, dialog_id);
                    } else {
                        TLRPC.TL_video video = (TLRPC.TL_video)selectedObject.messageOwner.media.video;
                        video.path = selectedObject.messageOwner.attachPath;
                        MessagesController.getInstance().sendMessage(video, video.path, dialog_id);
                    }
                } else if (selectedObject.type == 12 || selectedObject.type == 13) {
                    TLRPC.User user = MessagesController.getInstance().users.get(selectedObject.messageOwner.media.user_id);
                    MessagesController.getInstance().sendMessage(user, dialog_id);
                } else if (selectedObject.type == 8 || selectedObject.type == 9) {
                    TLRPC.TL_document document = (TLRPC.TL_document)selectedObject.messageOwner.media.document;
                    document.path = selectedObject.messageOwner.attachPath;
                    MessagesController.getInstance().sendMessage(document, document.path, dialog_id);
                } else if (selectedObject.type == 2) {
                    TLRPC.TL_audio audio = (TLRPC.TL_audio)selectedObject.messageOwner.media.audio;
                    audio.path = selectedObject.messageOwner.attachPath;
                    MessagesController.getInstance().sendMessage(audio, dialog_id);
                }
                ArrayList<Integer> arr = new ArrayList<Integer>();
                arr.add(selectedObject.messageOwner.id);
                ArrayList<Long> random_ids = null;
                if (currentEncryptedChat != null && selectedObject.messageOwner.random_id != 0 && selectedObject.type != 10) {
                    random_ids = new ArrayList<Long>();
                    random_ids.add(selectedObject.messageOwner.random_id);
                }
                MessagesController.getInstance().deleteMessages(arr, random_ids, currentEncryptedChat);
                chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
            }
        } else if (option == 1) {
            ArrayList<Integer> ids = new ArrayList<Integer>();
            ids.add(selectedObject.messageOwner.id);
            removeUnreadPlane(true);
            ArrayList<Long> random_ids = null;
            if (currentEncryptedChat != null && selectedObject.messageOwner.random_id != 0 && selectedObject.type != 10) {
                random_ids = new ArrayList<Long>();
                random_ids.add(selectedObject.messageOwner.random_id);
            }
            MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat);
        } else if (option == 2) {
            forwaringMessage = selectedObject;
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("serverOnly", true);
            args.putString("selectAlertString", LocaleController.getString("ForwardMessagesTo", R.string.ForwardMessagesTo));
            MessagesActivity fragment = new MessagesActivity(args);
            fragment.setDelegate(this);
            presentFragment(fragment);
        } else if (option == 3) {
            if(android.os.Build.VERSION.SDK_INT < 11) {
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(selectedObject.messageText);
            } else {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", selectedObject.messageText);
                clipboard.setPrimaryClip(clip);
            }
        } else if (option == 4) {
            String fileName = selectedObject.getFileName();
            if (selectedObject.type == 3) {
                MediaController.saveFile(fileName, selectedObject.messageOwner.attachPath, getParentActivity(), 1, null);
            } else if (selectedObject.type == 1) {
                MediaController.saveFile(fileName, selectedObject.messageOwner.attachPath, getParentActivity(), 0, null);
            } else if (selectedObject.type == 8 || selectedObject.type == 9) {
                MediaController.saveFile(fileName, selectedObject.messageOwner.attachPath, getParentActivity(), 2, selectedObject.messageOwner.media.document.file_name);
            }
        } else if (option == 5) {
            File locFile = null;
            if (selectedObject.messageOwner.attachPath != null && selectedObject.messageOwner.attachPath.length() != 0) {
                File f = new File(selectedObject.messageOwner.attachPath);
                if (f.exists()) {
                    locFile = f;
                }
            }
            if (locFile == null) {
                File f = new File(AndroidUtilities.getCacheDir(), selectedObject.getFileName());
                if (f.exists()) {
                    locFile = f;
                }
            }
            if (locFile != null) {
                if (LocaleController.getInstance().applyLanguageFile(locFile)) {
                    presentFragment(new LanguageSelectActivity());
                } else {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("IncorrectLocalization", R.string.IncorrectLocalization));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    showAlertDialog(builder);
                }
            }
        }
        selectedObject = null;
    }

    @Override
    public void didSelectFile(DocumentSelectActivity activity, String path) {
        activity.finishFragment();
        processSendingDocument(path, path);
    }

    @Override
    public void startDocumentSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("*/*");
            getParentActivity().startActivityForResult(photoPickerIntent, 21);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void processForwardFromMe(MessageObject messageObject, long did) {
        if (messageObject == null) {
            return;
        }
        if (messageObject.messageOwner.media != null && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                MessagesController.getInstance().sendMessage((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did);
            } else if (messageObject.messageOwner.media.audio instanceof TLRPC.TL_audio) {
                messageObject.messageOwner.media.audio.path = messageObject.messageOwner.attachPath;
                MessagesController.getInstance().sendMessage((TLRPC.TL_audio)messageObject.messageOwner.media.audio, did);
            } else if (messageObject.messageOwner.media.video instanceof TLRPC.TL_video) {
                messageObject.messageOwner.media.video.path = messageObject.messageOwner.attachPath;
                MessagesController.getInstance().sendMessage((TLRPC.TL_video)messageObject.messageOwner.media.video, null, did);
            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                messageObject.messageOwner.media.document.path = messageObject.messageOwner.attachPath;
                MessagesController.getInstance().sendMessage((TLRPC.TL_document)messageObject.messageOwner.media.document, null, did);
            } else if (messageObject.messageOwner.media.geo instanceof TLRPC.TL_geoPoint) {
                MessagesController.getInstance().sendMessage(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long, did);
            } else if (messageObject.messageOwner.media.phone_number != null) {
                TLRPC.User user = new TLRPC.TL_userContact();
                user.phone = messageObject.messageOwner.media.phone_number;
                user.first_name = messageObject.messageOwner.media.first_name;
                user.last_name = messageObject.messageOwner.media.last_name;
                user.id = messageObject.messageOwner.media.user_id;
                MessagesController.getInstance().sendMessage(user, did);
            } else {
                MessagesController.getInstance().sendMessage(messageObject, did);
            }
        } else if (messageObject.messageOwner.message != null) {
            MessagesController.getInstance().sendMessage(messageObject.messageOwner.message, did);
        } else {
            MessagesController.getInstance().sendMessage(messageObject, did);
        }
    }

    private void forwardSelectedMessages(long did, boolean fromMyName) {
        if (forwaringMessage != null) {
            if (forwaringMessage.messageOwner.id > 0) {
                if (!fromMyName) {
                    MessagesController.getInstance().sendMessage(forwaringMessage, did);
                } else {
                    processForwardFromMe(forwaringMessage, did);
                }
            }
            forwaringMessage = null;
        } else {
            ArrayList<Integer> ids = new ArrayList<Integer>(selectedMessagesIds.keySet());
            Collections.sort(ids);
            for (Integer id : ids) {
                if (id > 0) {
                    if (!fromMyName) {
                        MessagesController.getInstance().sendMessage(selectedMessagesIds.get(id), did);
                    } else {
                        processForwardFromMe(selectedMessagesIds.get(id), did);
                    }
                }
            }
            selectedMessagesCanCopyIds.clear();
            selectedMessagesIds.clear();
        }
    }

    @Override
    public void didSelectDialog(MessagesActivity activity, long did, boolean param) {
        if (dialog_id != 0 && (forwaringMessage != null || !selectedMessagesIds.isEmpty())) {

            if (did != dialog_id) {
                int lower_part = (int)did;
                if (lower_part != 0) {
                    Bundle args = new Bundle();
                    args.putBoolean("scrollToTopOnResume", scrollToTopOnResume);
                    if (lower_part > 0) {
                        args.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        args.putInt("chat_id", -lower_part);
                    }
                    presentFragment(new ChatActivity(args), true);
                    removeSelfFromStack();
                    forwardSelectedMessages(did, param);
                } else {
                    activity.finishFragment();
                }
            } else {
                activity.finishFragment();
                forwardSelectedMessages(did, param);
                chatListView.setSelectionFromTop(messages.size() - 1, -100000 - chatListView.getPaddingTop());
                scrollToTopOnResume = true;
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (actionBarLayer.isActionModeShowed()) {
            selectedMessagesIds.clear();
            selectedMessagesCanCopyIds.clear();
            actionBarLayer.hideActionMode();
            updateVisibleRows();
            return false;
        } else if (chatActivityEnterView.isEmojiPopupShowing()) {
            chatActivityEnterView.hideEmojiPopup();
            return false;
        }
        return true;
    }

    public boolean isGoogleMapsInstalled() {
        try {
            ApplicationInfo info = ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0);
            return true;
        } catch(PackageManager.NameNotFoundException e) {
            if (getParentActivity() == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage("Install Google Maps?");
            builder.setCancelable(true);
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps"));
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            });
            builder.setNegativeButton(R.string.Cancel, null);
            showAlertDialog(builder);
            return false;
        }
    }

    private void updateVisibleRows() {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            Object tag = view.getTag();
            if (tag instanceof ChatListRowHolderEx) {
                ChatListRowHolderEx holder = (ChatListRowHolderEx)tag;
                holder.update();

                boolean disableSelection = false;
                boolean selected = false;
                if (actionBarLayer.isActionModeShowed()) {
                    if (selectedMessagesIds.containsKey(holder.message.messageOwner.id)) {
                        view.setBackgroundColor(0x6633b5e5);
                        selected = true;
                    } else {
                        view.setBackgroundColor(0);
                    }
                    disableSelection = true;
                } else {
                    view.setBackgroundColor(0);
                }
                updateRowBackground(holder, disableSelection, selected);
            } else if (view instanceof ChatBaseCell) {
                ChatBaseCell cell = (ChatBaseCell)view;

                boolean disableSelection = false;
                boolean selected = false;
                if (actionBarLayer.isActionModeShowed()) {
                    if (selectedMessagesIds.containsKey(cell.getMessageObject().messageOwner.id)) {
                        view.setBackgroundColor(0x6633b5e5);
                        selected = true;
                    } else {
                        view.setBackgroundColor(0);
                    }
                    disableSelection = true;
                } else {
                    view.setBackgroundColor(0);
                }

                cell.setMessageObject(cell.getMessageObject());

                cell.setCheckPressed(!disableSelection, disableSelection && selected);
            }
        }
    }

    private void updateRowBackground(ChatListRowHolderEx holder, boolean disableSelection, boolean selected) {
        int messageType = holder.message.type;
        if (!disableSelection) {
            if (messageType == 12) {
                holder.chatBubbleView.setBackgroundResource(R.drawable.chat_outgoing_text_states);
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(18), 0);
            } else if (messageType == 13) {
                holder.chatBubbleView.setBackgroundResource(R.drawable.chat_incoming_text_states);
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(6), AndroidUtilities.dp(9), 0);
            } else if (messageType == 8) {
                holder.chatBubbleView.setBackgroundResource(R.drawable.chat_outgoing_text_states);
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(18), 0);
            } else if (messageType == 9) {
                holder.chatBubbleView.setBackgroundResource(R.drawable.chat_incoming_text_states);
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9), AndroidUtilities.dp(9), 0);
            }
        } else {
            if (messageType == 12) {
                if (selected) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_selected);
                } else {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out);
                }
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(18), 0);
            } else if (messageType == 13) {
                if (selected) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_selected);
                } else {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in);
                }
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(6), AndroidUtilities.dp(9), 0);
            } else if (messageType == 8) {
                if (selected) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out_selected);
                } else {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_out);
                }
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(18), 0);
            } else if (messageType == 9) {
                if (selected) {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in_selected);
                } else {
                    holder.chatBubbleView.setBackgroundResource(R.drawable.msg_in);
                }
                holder.chatBubbleView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9), AndroidUtilities.dp(9), 0);
            }
        }
    }

    private void alertUserOpenError(MessageObject message) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(R.string.OK, null);
        if (message.type == 3) {
            builder.setMessage(R.string.NoPlayerInstalled);
        } else {
            builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.messageOwner.media.document.mime_type));
        }
        showAlertDialog(builder);
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (messageObject == null) {
            return null;
        }
        int count = chatListView.getChildCount();

        for (int a = 0; a < count; a++) {
            MessageObject messageToOpen = null;
            ImageReceiver imageReceiver = null;
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMediaCell) {
                ChatMediaCell cell = (ChatMediaCell)view;
                MessageObject message = cell.getMessageObject();
                if (message != null && message.messageOwner.id == messageObject.messageOwner.id) {
                    messageToOpen = message;
                    imageReceiver = cell.getPhotoImage();
                }
            } else if (view.getTag() != null) {
                Object tag = view.getTag();
                if (tag instanceof ChatListRowHolderEx) {
                    ChatListRowHolderEx holder = (ChatListRowHolderEx)tag;
                    if (holder.message != null && holder.message.messageOwner.id == messageObject.messageOwner.id) {
                        messageToOpen = holder.message;
                        imageReceiver = holder.photoImage.imageReceiver;
                        view = holder.photoImage;
                    }
                }
            }

            if (messageToOpen != null) {
                int coords[] = new int[2];
                view.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                object.parentView = chatListView;
                object.imageReceiver = imageReceiver;
                object.thumb = object.imageReceiver.getBitmap();
                return object;
            }
        }
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() { }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public void cancelButtonPressed() { }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private class ChatAdapter extends BaseFragmentAdapter {

        private Context mContext;

        public ChatAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            int count = messages.size();
            if (count != 0) {
                if (!endReached) {
                    count++;
                }
                if (!unread_end_reached) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int offset = 1;
            if ((!endReached || !unread_end_reached) && messages.size() != 0) {
                if (!endReached) {
                    offset = 0;
                }
                if (i == 0 && !endReached || !unread_end_reached && i == (messages.size() + 1 - offset)) {
                    if (view == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.chat_loading_layout, viewGroup, false);
                        View progressBar = view.findViewById(R.id.progressLayout);
                        if (isCustomTheme) {
                            progressBar.setBackgroundResource(R.drawable.system_loader2);
                        } else {
                            progressBar.setBackgroundResource(R.drawable.system_loader1);
                        }
                    }
                    return view;
                }
            }
            MessageObject message = messages.get(messages.size() - i - offset);
            int type = message.contentType;
            if (view == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (type == 0) {
                    view = new ChatMessageCell(mContext);
                } if (type == 1) {
                    view = new ChatMediaCell(mContext);
                    ((ChatMediaCell)view).downloadPhotos = downloadPhotos;
                } else if (type == 10) {
                    view = li.inflate(R.layout.chat_action_message_layout, viewGroup, false);
                } else if (type == 11) {
                    view = li.inflate(R.layout.chat_action_change_photo_layout, viewGroup, false);
                } else if (type == 4) {
                    view = li.inflate(R.layout.chat_outgoing_contact_layout, viewGroup, false);
                } else if (type == 5) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_contact_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_contact_layout, viewGroup, false);
                    }
                } else if (type == 7) {
                    view = li.inflate(R.layout.chat_unread_layout, viewGroup, false);
                } else if (type == 8) {
                    view = li.inflate(R.layout.chat_outgoing_document_layout, viewGroup, false);
                } else if (type == 9) {
                    if (currentChat != null) {
                        view = li.inflate(R.layout.chat_group_incoming_document_layout, viewGroup, false);
                    } else {
                        view = li.inflate(R.layout.chat_incoming_document_layout, viewGroup, false);
                    }
                } else if (type == 2) {
                    view = new ChatAudioCell(mContext);
                }
            }

            boolean selected = false;
            boolean disableSelection = false;
            if (actionBarLayer.isActionModeShowed()) {
                if (selectedMessagesIds.containsKey(message.messageOwner.id)) {
                    view.setBackgroundColor(0x6633b5e5);
                    selected = true;
                } else {
                    view.setBackgroundColor(0);
                }
                disableSelection = true;
            } else {
                view.setBackgroundColor(0);
            }

            if (view instanceof ChatBaseCell) {
                ((ChatBaseCell)view).delegate = new ChatBaseCell.ChatBaseCellDelegate() {
                    @Override
                    public void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user) {
                        if (user != null && user.id != UserConfig.getClientUserId()) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user.id);
                            presentFragment(new UserProfileActivity(args));
                        }
                    }

                    @Override
                    public void didPressedCancelSendButton(ChatBaseCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message.messageOwner.send_state != 0) {
                            MessagesController.getInstance().cancelSendingMessage(message);
                        }
                    }

                    @Override
                    public void didLongPressed(ChatBaseCell cell) {
                        createMenu(cell, false);
                    }

                    @Override
                    public boolean canPerformActions() {
                        return actionBarLayer != null && !actionBarLayer.isActionModeShowed();
                    }
                };
                if (view instanceof ChatMediaCell) {
                    ((ChatMediaCell)view).mediaDelegate = new ChatMediaCell.ChatMediaCellDelegate() {
                        @Override
                        public void didPressedImage(ChatMediaCell cell) {
                            MessageObject message = cell.getMessageObject();
                            if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                                createMenu(cell, false);
                                return;
                            } else if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                                return;
                            }
                            if (message.type == 1) {
                                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                                PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                            } else if (message.type == 3) {
                                try {
                                    File f = null;
                                    if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                        f = new File(message.messageOwner.attachPath);
                                    }
                                    if (f == null || f != null && !f.exists()) {
                                        f = new File(AndroidUtilities.getCacheDir(), message.getFileName());
                                    }
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                    getParentActivity().startActivity(intent);
                                } catch (Exception e) {
                                    alertUserOpenError(message);
                                }
                            } else if (message.type == 4) {
                                if (!isGoogleMapsInstalled()) {
                                    return;
                                }
                                LocationActivity fragment = new LocationActivity();
                                fragment.setMessageObject(message);
                                presentFragment(fragment);
                            }
                        }
                    };
                }

                ((ChatBaseCell)view).isChat = currentChat != null;
                ((ChatBaseCell)view).setMessageObject(message);
                ((ChatBaseCell)view).setCheckPressed(!disableSelection, disableSelection && selected);
                if (view instanceof ChatAudioCell && (downloadAudios == 0 || downloadAudios == 2 && ConnectionsManager.isConnectedToWiFi())) {
                    ((ChatAudioCell)view).downloadAudioIfNeed();
                } else if (view instanceof ChatMediaCell) {
                    ((ChatMediaCell)view).downloadPhotos = downloadPhotos;
                }
            } else {
                ChatListRowHolderEx holder = (ChatListRowHolderEx)view.getTag();
                if (holder == null) {
                    holder = new ChatListRowHolderEx(view, message.type);
                    view.setTag(holder);
                }
                holder.message = message;
                updateRowBackground(holder, disableSelection, selected);
                holder.update();
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            int offset = 1;
            if (!endReached && messages.size() != 0) {
                offset = 0;
                if (i == 0) {
                    return 6;
                }
            }
            if (!unread_end_reached && i == (messages.size() + 1 - offset)) {
                return 6;
            }
            MessageObject message = messages.get(messages.size() - i - offset);
            return message.contentType;
        }

        @Override
        public int getViewTypeCount() {
            return 12;
        }

        @Override
        public boolean isEmpty() {
            int count = messages.size();
            if (count != 0) {
                if (!endReached) {
                    count++;
                }
                if (!unread_end_reached) {
                    count++;
                }
            }
            return count == 0;
        }
    }

    public class ChatListRowHolderEx {
        public BackupImageView avatarImageView;
        public TextView nameTextView;
        public TextView messageTextView;
        public MessageActionLayout messageLayoutAction;
        public TextView timeTextView;
        public BackupImageView photoImage;
        public ImageView halfCheckImage;
        public ImageView checkImage;
        public TextView actionAttachButton;
        public TextView videoTimeText;
        public MessageObject message;
        public TextView phoneTextView;
        public BackupImageView contactAvatar;
        public View contactView;
        public ImageView addContactButton;
        public View addContactView;
        public View chatBubbleView;

        public ProgressBar actionProgress;
        public View actionView;
        public ImageView actionCancelButton;

        private PhotoObject photoObjectToSet = null;
        private File photoFile = null;
        private String photoFilter = null;

        public void update() {
            TLRPC.User fromUser = MessagesController.getInstance().users.get(message.messageOwner.from_id);

            int type = message.type;

            if (timeTextView != null) {
                timeTextView.setText(LocaleController.formatterDay.format((long) (message.messageOwner.date) * 1000));
            }

            if (avatarImageView != null && fromUser != null) {
                TLRPC.FileLocation photo = null;
                if (fromUser.photo != null) {
                    photo = fromUser.photo.photo_small;
                }
                int placeHolderId = Utilities.getUserAvatarForId(fromUser.id);
                avatarImageView.setImage(photo, "50_50", placeHolderId);
            }

            if (type != 12 && type != 13 && nameTextView != null && fromUser != null && type != 8 && type != 9) {
                nameTextView.setText(Utilities.formatName(fromUser.first_name, fromUser.last_name));
                nameTextView.setTextColor(Utilities.getColorForId(message.messageOwner.from_id));
            }

            if (type == 11 || type == 10) {
                int width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(30);
                messageTextView.setText(message.messageText);
                messageTextView.setMaxWidth(width);

                if (type == 11) {
                    if (message.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                        photoImage.setImage(message.messageOwner.action.newUserPhoto.photo_small, "50_50", Utilities.getUserAvatarForId(currentUser.id));
                    } else {
                        PhotoObject photo = PhotoObject.getClosestImageWithSize(message.photoThumbs, AndroidUtilities.dp(64), AndroidUtilities.dp(64));
                        if (photo != null) {
                            if (photo.image != null) {
                                photoImage.setImageBitmap(photo.image);
                            } else {
                                photoImage.setImage(photo.photoOwner.location, "50_50", Utilities.getGroupAvatarForId(currentChat.id));
                            }
                        } else {
                            photoImage.setImageResource(Utilities.getGroupAvatarForId(currentChat.id));
                        }
                    }
                    photoImage.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(message), false);
                }
            } else if (type == 12 || type == 13) {
                TLRPC.User contactUser = MessagesController.getInstance().users.get(message.messageOwner.media.user_id);
                if (contactUser != null) {
                    nameTextView.setText(Utilities.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(Utilities.getColorForId(contactUser.id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (!phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.getInstance().format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    TLRPC.FileLocation photo = null;
                    if (contactUser.photo != null) {
                        photo = contactUser.photo.photo_small;
                    }
                    int placeHolderId = Utilities.getUserAvatarForId(contactUser.id);
                    contactAvatar.setImage(photo, "50_50", placeHolderId);
                    if (contactUser.id != UserConfig.getClientUserId() && ContactsController.getInstance().contactsDict.get(contactUser.id) == null) {
                        addContactView.setVisibility(View.VISIBLE);
                    } else {
                        addContactView.setVisibility(View.GONE);
                    }
                } else {
                    nameTextView.setText(Utilities.formatName(message.messageOwner.media.first_name, message.messageOwner.media.last_name));
                    nameTextView.setTextColor(Utilities.getColorForId(message.messageOwner.media.user_id));
                    String phone = message.messageOwner.media.phone_number;
                    if (phone != null && phone.length() != 0) {
                        if (message.messageOwner.media.user_id != 0 && !phone.startsWith("+")) {
                            phone = "+" + phone;
                        }
                        phoneTextView.setText(PhoneFormat.getInstance().format(phone));
                    } else {
                        phoneTextView.setText("Unknown");
                    }
                    contactAvatar.setImageResource(Utilities.getUserAvatarForId(message.messageOwner.media.user_id));
                    addContactView.setVisibility(View.GONE);
                }
            } else if (type == 7) {
                messageTextView.setTextSize(16);
                messageTextView.setText(LocaleController.formatPluralString("NewMessages", unread_to_load));
            } else if (type == 8 || type == 9) {
                TLRPC.Document document = message.messageOwner.media.document;
                if (document instanceof TLRPC.TL_document || document instanceof TLRPC.TL_documentEncrypted) {
                    nameTextView.setText(message.messageOwner.media.document.file_name);

                    String fileName = message.getFileName();
                    int idx = fileName.lastIndexOf(".");
                    String ext = null;
                    if (idx != -1) {
                        ext = fileName.substring(idx + 1);
                    }
                    if (ext == null || ext.length() == 0) {
                        ext = message.messageOwner.media.document.mime_type;
                    }
                    ext = ext.toUpperCase();
                    if (document.size < 1024) {
                        phoneTextView.setText(String.format("%d B %s", document.size, ext));
                    } else if (document.size < 1024 * 1024) {
                        phoneTextView.setText(String.format("%.1f KB %s", document.size / 1024.0f, ext));
                    } else {
                        phoneTextView.setText(String.format("%.1f MB %s", document.size / 1024.0f / 1024.0f, ext));
                    }
                    if (document.thumb instanceof TLRPC.TL_photoSize) {
                        contactAvatar.setImage(document.thumb.location, "50_50", type == 8 ? R.drawable.doc_green : R.drawable.doc_blue);
                    } else if (document.thumb instanceof TLRPC.TL_photoCachedSize) {
                        contactAvatar.setImage(document.thumb.location, "50_50", type == 8 ? R.drawable.doc_green : R.drawable.doc_blue);
                    } else {
                        if (type == 8) {
                            contactAvatar.setImageResource(R.drawable.doc_green);
                        } else {
                            contactAvatar.setImageResource(R.drawable.doc_blue);
                        }
                    }
                } else {
                    nameTextView.setText("Error");
                    phoneTextView.setText("Error");
                    if (type == 8) {
                        contactAvatar.setImageResource(R.drawable.doc_green);
                    } else {
                        contactAvatar.setImageResource(R.drawable.doc_blue);
                    }
                }
            }

            if (message.messageOwner.id < 0 && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SEND_ERROR && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENT) {
                if (MessagesController.getInstance().sendingMessages.get(message.messageOwner.id) == null) {
                    message.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SEND_ERROR;
                }
            }

            if (message.isFromMe()) {
                if (halfCheckImage != null) {
                    if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                        checkImage.setVisibility(View.INVISIBLE);
                        halfCheckImage.setImageResource(R.drawable.msg_clock);
                        halfCheckImage.setVisibility(View.VISIBLE);
                        if (actionView != null) {
                            if (actionView != null) {
                                actionView.setVisibility(View.VISIBLE);
                            }
                            Float progress = null;
                            if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                progress = FileLoader.getInstance().fileProgresses.get(message.messageOwner.attachPath);
                                progressByTag.put((Integer)actionProgress.getTag(), message.messageOwner.attachPath);
                                progressBarMap.put(message.messageOwner.attachPath, actionProgress);
                            }
                            if (progress != null) {
                                actionProgress.setProgress((int)(progress * 100));
                            } else {
                                actionProgress.setProgress(0);
                            }
                        }
                        if (actionAttachButton != null) {
                            actionAttachButton.setVisibility(View.GONE);
                        }
                    } else if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                        halfCheckImage.setVisibility(View.VISIBLE);
                        halfCheckImage.setImageResource(R.drawable.msg_warning);
                        if (checkImage != null) {
                            checkImage.setVisibility(View.INVISIBLE);
                        }
                        if (actionView != null) {
                            actionView.setVisibility(View.GONE);
                        }
                        if (actionAttachButton != null) {
                            actionAttachButton.setVisibility(View.GONE);
                        }
                    } else if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENT) {
                        if (!message.messageOwner.unread) {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.VISIBLE);
                            halfCheckImage.setImageResource(R.drawable.msg_halfcheck);
                        } else {
                            halfCheckImage.setVisibility(View.VISIBLE);
                            checkImage.setVisibility(View.INVISIBLE);
                            halfCheckImage.setImageResource(R.drawable.msg_check);
                        }
                        if (actionView != null) {
                            actionView.setVisibility(View.GONE);
                        }
                        if (actionAttachButton != null) {
                            actionAttachButton.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
            if (message.type == 8 || message.type == 9) {
                Integer tag = (Integer)actionProgress.getTag();
                String file = progressByTag.get(tag);
                if (file != null) {
                    removeFromloadingFile(file, actionProgress);
                }
                if (message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENDING && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                    if (file != null) {
                        progressBarMap.remove(file);
                    }
                    String fileName = message.getFileName();
                    boolean load = false;
                    if (message.type != 2 && message.type != 3 && message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                        File f = new File(message.messageOwner.attachPath);
                        if (f.exists()) {
                            if (actionAttachButton != null) {
                                actionAttachButton.setVisibility(View.VISIBLE);
                                if (message.type == 8 || message.type == 9) {
                                    actionAttachButton.setText(LocaleController.getString("Open", R.string.Open));
                                }
                            }
                            if (actionView != null) {
                                actionView.setVisibility(View.GONE);
                            }
                        } else {
                            load = true;
                        }
                    }
                    if (load && message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0 || !load && (message.messageOwner.attachPath == null || message.messageOwner.attachPath.length() == 0)) {
                        File cacheFile = null;
                        if ((cacheFile = new File(AndroidUtilities.getCacheDir(), fileName)).exists()) {
                            if (actionAttachButton != null) {
                                actionAttachButton.setVisibility(View.VISIBLE);
                                if (message.type == 8 || message.type == 9) {
                                    actionAttachButton.setText(LocaleController.getString("Open", R.string.Open));
                                }
                            }
                            if (actionView != null) {
                                actionView.setVisibility(View.GONE);
                            }
                            load = false;
                        } else {
                            load = true;
                        }
                    }
                    if (load) {
                        Float progress = FileLoader.getInstance().fileProgresses.get(fileName);
                        if (loadingFile.containsKey(fileName) || progress != null) {
                            if (progress != null) {
                                actionProgress.setProgress((int)(progress * 100));
                            } else {
                                actionProgress.setProgress(0);
                            }
                            progressByTag.put((Integer)actionProgress.getTag(), fileName);
                            addToLoadingFile(fileName, actionProgress);
                            if (actionView != null) {
                                actionView.setVisibility(View.VISIBLE);
                            }
                            if (actionAttachButton != null) {
                                actionAttachButton.setVisibility(View.GONE);
                            }
                        } else {
                            if (actionView != null) {
                                actionView.setVisibility(View.GONE);
                            }
                            if (actionAttachButton != null) {
                                actionAttachButton.setVisibility(View.VISIBLE);
                                if (message.type == 8 || message.type == 9) {
                                    actionAttachButton.setText(LocaleController.getString("DOWNLOAD", R.string.DOWNLOAD));
                                }
                            }
                        }
                    }
                }
                if (message.type == 8 || message.type == 9) {
                    int width;
                    if (currentChat != null && type != 8) {
                        if (actionView.getVisibility() == View.VISIBLE) {
                            width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(290);
                        } else {
                            width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(270);
                        }
                    } else {
                        if (actionView.getVisibility() == View.VISIBLE) {
                            width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(240);
                        } else {
                            width = AndroidUtilities.displaySize.x - AndroidUtilities.dp(220);
                        }
                    }
                    nameTextView.setMaxWidth(width);
                    phoneTextView.setMaxWidth(width);
                }
            }
        }

        public ChatListRowHolderEx(View view, int type) {
            avatarImageView = (BackupImageView)view.findViewById(R.id.chat_group_avatar_image);
            nameTextView = (TextView)view.findViewById(R.id.chat_user_group_name);
            messageLayoutAction = (MessageActionLayout)view.findViewById(R.id.message_action_layout);
            timeTextView = (TextView)view.findViewById(R.id.chat_time_text);
            photoImage = (BackupImageView)view.findViewById(R.id.chat_photo_image);
            halfCheckImage = (ImageView)view.findViewById(R.id.chat_row_halfcheck);
            checkImage = (ImageView)view.findViewById(R.id.chat_row_check);
            actionAttachButton = (TextView)view.findViewById(R.id.chat_view_action_button);
            messageTextView = (TextView)view.findViewById(R.id.chat_message_text);
            videoTimeText = (TextView)view.findViewById(R.id.chat_video_time);
            actionView = view.findViewById(R.id.chat_view_action_layout);
            actionProgress = (ProgressBar)view.findViewById(R.id.chat_view_action_progress);
            actionCancelButton = (ImageView)view.findViewById(R.id.chat_view_action_cancel_button);
            phoneTextView = (TextView)view.findViewById(R.id.phone_text_view);
            contactAvatar = (BackupImageView)view.findViewById(R.id.contact_avatar);
            contactView = view.findViewById(R.id.shared_layout);
            addContactButton = (ImageView)view.findViewById(R.id.add_contact_button);
            addContactView = view.findViewById(R.id.add_contact_view);
            chatBubbleView = view.findViewById(R.id.chat_bubble_layout);
            if (messageTextView != null) {
                messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, MessagesController.getInstance().fontSize);
            }

            if (actionProgress != null) {
                actionProgress.setTag(progressTag);
                progressTag++;
            }

            if (type != 2 && type != 3) {
                if (actionView != null) {
                    if (isCustomTheme) {
                        actionView.setBackgroundResource(R.drawable.system_black);
                    } else {
                        actionView.setBackgroundResource(R.drawable.system_blue);
                    }
                }
            }

            if (messageLayoutAction != null) {
                if (isCustomTheme) {
                    messageLayoutAction.setBackgroundResource(R.drawable.system_black);
                } else {
                    messageLayoutAction.setBackgroundResource(R.drawable.system_blue);
                }
            }

            if (addContactButton != null) {
                addContactButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (actionBarLayer.isActionModeShowed()) {
                            processRowSelect(view);
                            return;
                        }
                        Bundle args = new Bundle();
                        args.putInt("user_id", message.messageOwner.media.user_id);
                        args.putString("phone", message.messageOwner.media.phone_number);
                        presentFragment(new ContactAddActivity(args));
                    }
                });

                addContactButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }

            if (contactView != null) {
                contactView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (message.type == 8 || message.type == 9) {
                            processOnClick(view);
                        } else if (message.type == 12 || message.type == 13) {
                            if (actionBarLayer.isActionModeShowed()) {
                                processRowSelect(view);
                                return;
                            }
                            if (message.messageOwner.media.user_id != UserConfig.getClientUserId()) {
                                TLRPC.User user = null;
                                if (message.messageOwner.media.user_id != 0) {
                                    user = MessagesController.getInstance().users.get(message.messageOwner.media.user_id);
                                }
                                if (user != null) {
                                    Bundle args = new Bundle();
                                    args.putInt("user_id", message.messageOwner.media.user_id);
                                    presentFragment(new UserProfileActivity(args));
                                } else {
                                    if (message.messageOwner.media.phone_number == null || message.messageOwner.media.phone_number.length() == 0) {
                                        return;
                                    }
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setItems(new CharSequence[] {LocaleController.getString("Copy", R.string.Copy), LocaleController.getString("Call", R.string.Call)}, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    if (i == 1) {
                                                        try {
                                                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + message.messageOwner.media.phone_number));
                                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                            getParentActivity().startActivity(intent);
                                                        } catch (Exception e) {
                                                            FileLog.e("tmessages", e);
                                                        }
                                                    } else if (i == 0) {
                                                        int sdk = android.os.Build.VERSION.SDK_INT;
                                                        if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                                                            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                            clipboard.setText(message.messageOwner.media.phone_number);
                                                        } else {
                                                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                                            android.content.ClipData clip = android.content.ClipData.newPlainText("label", message.messageOwner.media.phone_number);
                                                            clipboard.setPrimaryClip(clip);
                                                        }
                                                    }
                                                }
                                            }
                                    );
                                    showAlertDialog(builder);
                                }
                            }
                        }
                    }
                });

                contactView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }

            if (contactAvatar != null) {
                contactAvatar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                    }
                });
            }

            if (actionAttachButton != null) {
                actionAttachButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        processOnClick(view);
                    }
                });
            }

            if (avatarImageView != null) {
                avatarImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (actionBarLayer.isActionModeShowed()) {
                            processRowSelect(view);
                            return;
                        }
                        if (message != null) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", message.messageOwner.from_id);
                            presentFragment(new UserProfileActivity(args));
                        }
                    }
                });
            }

            if (actionCancelButton != null) {
                actionCancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (message != null) {
                            Integer tag = (Integer)actionProgress.getTag();
                            if (message.messageOwner.send_state != 0) {
                                MessagesController.getInstance().cancelSendingMessage(message);
                                String file = progressByTag.get(tag);
                                if (file != null) {
                                    progressBarMap.remove(file);
                                }
                            } else if (message.type == 8 || message.type == 9) {
                                String file = progressByTag.get(tag);
                                if (file != null) {
                                    loadingFile.remove(file);
                                    if (message.type == 8 || message.type == 9) {
                                        FileLoader.getInstance().cancelLoadFile(null, null, message.messageOwner.media.document, null);
                                    }
                                    updateVisibleRows();
                                }
                            }
                        }
                    }
                });
            }

            if (photoImage != null) {
                photoImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        processOnClick(view);
                    }
                });

                photoImage.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        createMenu(v, false);
                        return true;
                    }
                });
            }
        }

        private void processOnClick(View view) {
            if (actionBarLayer.isActionModeShowed()) {
                processRowSelect(view);
                return;
            }
            if (message != null) {
                if (message.type == 11) {
                    PhotoViewer.getInstance().setParentActivity(getParentActivity());
                    PhotoViewer.getInstance().openPhoto(message, ChatActivity.this);
                } else if (message.type == 8 || message.type == 9) {
                    File f = null;
                    String fileName = message.getFileName();
                    if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                        f = new File(message.messageOwner.attachPath);
                    }
                    if (f == null || f != null && !f.exists()) {
                        f = new File(AndroidUtilities.getCacheDir(), fileName);
                    }
                    if (f != null && f.exists()) {
                        String realMimeType = null;
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            if (message.type == 8 || message.type == 9) {
                                MimeTypeMap myMime = MimeTypeMap.getSingleton();
                                int idx = fileName.lastIndexOf(".");
                                if (idx != -1) {
                                    String ext = fileName.substring(idx + 1);
                                    realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                                    if (realMimeType != null) {
                                        intent.setDataAndType(Uri.fromFile(f), realMimeType);
                                    } else {
                                        intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                    }
                                } else {
                                    intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                }
                            }
                            if (realMimeType != null) {
                                try {
                                    getParentActivity().startActivity(intent);
                                } catch (Exception e) {
                                    intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                    getParentActivity().startActivity(intent);
                                }
                            } else {
                                getParentActivity().startActivity(intent);
                            }
                        } catch (Exception e) {
                            alertUserOpenError(message);
                        }
                    } else {
                        if (message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SEND_ERROR && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENDING || !message.isOut()) {
                            if (!loadingFile.containsKey(fileName)) {
                                progressByTag.put((Integer)actionProgress.getTag(), fileName);
                                addToLoadingFile(fileName, actionProgress);
                                if (message.type == 8 || message.type == 9) {
                                    FileLoader.getInstance().loadFile(null, null, message.messageOwner.media.document, null);
                                }
                                updateVisibleRows();
                            }
                        } else {
                            if (message.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                                createMenu(view, false);
                            }
                        }
                    }
                }
            }
        }
    }
}
