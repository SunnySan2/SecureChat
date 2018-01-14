package com.taisys.sc.securechat.util;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.squareup.picasso.Picasso;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.ChatMessagesActivity;
import com.taisys.sc.securechat.R;
import com.taisys.sc.securechat.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by sunny.sun on 2018/1/10.
 */

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {
    public static final int ITEM_TYPE_SENT = 0;
    public static final int ITEM_TYPE_RECEIVED = 1;

    private List<ChatMessage> mMessagesList;
    private Context mContext;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        //這是 Sent 訊息的 ViewHolder
        // each data item is just a string in this case
        public TextView messageTextView;
        public ImageView imageImageView;
        public TextView nameTextView;
        public TextView timeTextView;
        public String originalMessage;  //儲存原始訊息內容
        public boolean bDecrypted;  //判斷此訊息是否已被解密
        public  String dbKey;   //firebase 中此筆資料的 key

        public View layout;

        public ViewHolder(View v) {
            super(v);
            layout = v;
            messageTextView = (TextView) v.findViewById(R.id.chatMsgTextView);
            imageImageView = (ImageView) v.findViewById(R.id.chatImage);
            nameTextView = (TextView) v.findViewById(R.id.chatNameTextView);
            timeTextView = (TextView) v.findViewById(R.id.chatTimeTextView);

            messageTextView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            //int position = getLayoutPosition();
            String msgWaiting = App.getContext().getResources().getString(R.string.msgDecryptingMessage);
            if (bDecrypted == true) return;  //已經解密完成，不用再處理了
            if (originalMessage == null || originalMessage.length()<1) return;  //這個條件應該不會發生，只是以防萬一
            messageTextView.setText(msgWaiting);
            Log.d("SecureChat", "Try to decrypt message: " + originalMessage);
            //如果 nameTextView==null，則這個是 Sent 的訊息，若nameTextView!=null，則這個是 Receive 的訊息
            if (nameTextView==null) Log.d("SecureChat", "nameTextView is null ");
            //暫時先簡單這樣做吧
            Handler handler = new Handler();
            handler.postDelayed(new Runnable(){

                @Override
                public void run() {
                    String decryptedMessage = "";
                    decryptedMessage = originalMessage;
                    messageTextView.setText(decryptedMessage);
                    updateDecryptStatusFromDb(dbKey);
                    bDecrypted = true;
                }}, 500);
        }
    }

    public class ViewHolder2 extends RecyclerView.ViewHolder {
        //這是 Received 訊息的 ViewHolder
        // each data item is just a string in this case
        public TextView messageTextView;
        public ImageView imageImageView;
        public TextView nameTextView;
        public TextView timeTextView;
        public String originalMessage;  //儲存原始訊息內容
        public boolean bDecrypted;  //判斷此訊息是否已被解密

        public View layout;

        public ViewHolder2(View v) {
            super(v);
            layout = v;
            messageTextView = (TextView) v.findViewById(R.id.chatMsgTextView);
            imageImageView = (ImageView) v.findViewById(R.id.chatImage);
            nameTextView = (TextView) v.findViewById(R.id.chatNameTextView);
            timeTextView = (TextView) v.findViewById(R.id.chatTimeTextView);

        }
    }


    public void add(int position, ChatMessage message) {
        mMessagesList.add(position, message);
        notifyItemInserted(position);
    }

    public void remove(int position) {
        mMessagesList.remove(position);
        notifyItemRemoved(position);
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MessagesAdapter(List<ChatMessage> myDataset, Context context) {
        mMessagesList = myDataset;
        mContext = context;

    }

    @Override
    public int getItemViewType(int position) {
        if (mMessagesList.get(position).getSenderId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
            return ITEM_TYPE_SENT;
        } else {
            return ITEM_TYPE_RECEIVED;
        }
    }



    // Create new views (invoked by the layout manager)
    @Override
    public MessagesAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
        View v = null;
        if (viewType == ITEM_TYPE_SENT) {
            v = LayoutInflater.from(mContext).inflate(R.layout.sent_msg_row, null);
        } else if (viewType == ITEM_TYPE_RECEIVED) {
            v = LayoutInflater.from(mContext).inflate(R.layout.received_msg_row, null);
        }
        return new ViewHolder(v); // view holder for header items
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        ChatMessage msg = mMessagesList.get(position);
        if (msg.getDecrypted()) {
            holder.messageTextView.setText(msg.getMessage());
        }else{
            holder.messageTextView.setText(App.getContext().getResources().getString(R.string.msgClickMeToDecryptMessage));
        }
        if (holder.imageImageView!=null) {
            //Log.d("SecureChat", "msg.getSenderImage()= " + msg.getSenderImage());
            if (msg.getSenderImage() != null && msg.getSenderImage().length() > 0) {
                Picasso.with(mContext)
                        .load(msg.getSenderImage())
                        .into(holder.imageImageView);
                //holder.imageImageView.setImageURI(Uri.parse(msg.getSenderImage()));
            }
        }
        if (holder.nameTextView!=null) holder.nameTextView.setText(msg.getSenderName());
        if (holder.timeTextView!=null) holder.timeTextView.setText(changeTimeMillisToDateTime(msg.getCreatedAt()));
        holder.originalMessage = msg.getMessage();
        holder.bDecrypted = msg.getDecrypted();
        holder.dbKey = msg.getDbKey();

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mMessagesList.size();
    }


    private String changeTimeMillisToDateTime(long timeMillis){
        if (timeMillis<1) return "";
        //SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
        sdf.setTimeZone(TimeZone.getDefault());
        Date resultdate = new Date(timeMillis);
        return sdf.format(resultdate);
    }

    //將 DB 中的 decrypted 更新為 true
    private void updateDecryptStatusFromDb(String dbKey){
        DatabaseReference mMessagesDBRef;
        try {
            //init Firebase
            mMessagesDBRef = FirebaseDatabase.getInstance().getReference().child("Messages").child(dbKey);
            Map<String, Object> statusUpdates = new HashMap<>();
            statusUpdates.put("decrypted", true);

            mMessagesDBRef.updateChildren(statusUpdates);
        }catch (Exception e){
            Log.d("SecureChat", "Failed to update message decrypted staus from DB: " + e.toString());
        }
    }


}
