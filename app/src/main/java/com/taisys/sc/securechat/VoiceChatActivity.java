package com.taisys.sc.securechat;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.LinphoneMiniManager;
import com.taisys.sc.securechat.util.Utility;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;

public class VoiceChatActivity extends AppCompatActivity{
    private static final String TAG = "SecureChat";
    //private static final String mSIPDomain = "taisys.com";
    //private static final String mSIPDomain = "sip.linphone.org";
    //private static final String mSIPDomain = "iptel.org";
    private String mSIPDomain = "";

    private ImageView mUserPhotoImageView;
    private TextView mContactNameTextView;
    private TextView mStatusTextView;
    private Button mDoVoiceChatBtn;
    private Button mCancelVoiceChatBtn;

    private String mReceiverId;
    private String mReceiverName;
    private String mReceiverImageUrl;
    private String mReceiverIccid;
    private String mMyId;
    private String mCallerAddress;

    private Context myContext = null;

    private DatabaseReference mUserDBRef;

    private LinphoneMiniManager mLinphoneMiniManager;
    private LinphoneCore mLinphoneCore;
    private LinphoneCall mCall;
    private boolean mIsCalling;
    private boolean mIsConnected;

    //顯示畫面的UI Thread上的Handler
    private Handler mUI_Handler = new Handler();
    private Handler mThreadHandler;
    private HandlerThread mThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat);

        mUserPhotoImageView = (ImageView)findViewById(R.id.userPhotoVoiceChat);
        mContactNameTextView = (TextView) findViewById(R.id.labelVoiceChatContactName);
        mStatusTextView = (TextView) findViewById(R.id.labelVoiceChatStatus);
        mDoVoiceChatBtn = (Button)findViewById(R.id.doVoiceChatBtn);
        mCancelVoiceChatBtn = (Button)findViewById(R.id.cancelVoiceChatBtn);

        myContext = this;

        //get receiverId from intent
        mMyId = getIntent().getStringExtra("MY_USER_ID");
        mReceiverId = getIntent().getStringExtra("RECEIVER_USER_ID");
        mReceiverName = getIntent().getStringExtra("RECEIVER_NAME");
        mReceiverImageUrl = getIntent().getStringExtra("RECEIVER_IMAGE_URL");
        mReceiverIccid = getIntent().getStringExtra("RECEIVER_ICCID");
        mCallerAddress = getIntent().getStringExtra("CALLER_ADDRESS");

        Log.d(TAG, "mMyId=" + mMyId);
        Log.d(TAG, "mReceiverId=" + mReceiverId);
        Log.d(TAG, "mReceiverName=" + mReceiverName);
        Log.d(TAG, "mReceiverImageUrl=" + mReceiverImageUrl);
        Log.d(TAG, "mReceiverIccid=" + mReceiverIccid);
        Log.d(TAG, "mCallerAddress=" + mCallerAddress);

        mSIPDomain = Utility.getMySetting(this, "sipDomain");
        mLinphoneMiniManager = App.getLinphoneManager();
        //mLinphoneCore = mLinphoneMiniManager.getLinphoneCore();
        mLinphoneCore = LinphoneMiniManager.getInstance().getLinphoneCore();

        mThread = new HandlerThread("name");
        mThread.start();
        mThreadHandler=new Handler(mThread.getLooper());

        //init firebase
        mUserDBRef = FirebaseDatabase.getInstance().getReference().child("Users");

        initView();
        initLinphone();
        mUI_Handler.post(updateRegistrationState);
    }

    @Override
    public void onBackPressed() {
        if (mIsConnected){
            mIsCalling = false;
            mIsConnected = false;
            mLinphoneCore.terminateCall(mCall);
        }
        finish();
    }

    @Override
    public void onDestroy() {
        try {
            mIsCalling = false;
            mIsConnected = false;
            //if (mIsCalling)mLinphoneCore.terminateCall(mCall);
        }
        catch (RuntimeException e) {
        }
        finally {
            mCall = null;
        }
        super.onDestroy();
    }

    private void initView(){
        if (mCallerAddress!=null && mCallerAddress.length()>0){ //這是 incoming call
            getCallerInfo();
            if (mReceiverIccid==null || mReceiverIccid.length()<1){
                Utility.showMessage(myContext, getString(R.string.msgCannotFindCallerInformation));
                return;
            }
            mStatusTextView.setText(getString(R.string.labrlVoiceCallRinging));
            mDoVoiceChatBtn.setText(getString(R.string.labelVoiceChatAcceptCall));
            mCancelVoiceChatBtn.setText(getString(R.string.labelVoiceChatRejectCall));

        }else{
            displayUserNameAndPicture();
        }

        mCancelVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            if (mIsConnected){
                mIsCalling = false;
                mIsConnected = false;
                mLinphoneCore.terminateCall(mCall);
            }
            finish();
            }
        });
    }

    private void getCallerInfo(){
        mCall = mLinphoneCore.getCurrentCall();
        if (mCall==null || LinphoneCall.State.IncomingReceived != mCall.getState()){
            Log.e(TAG, "Couldn\'t find incoming call");
            Utility.showMessage(myContext, getString(R.string.msgCoudntFindIncomingCall));
            mDoVoiceChatBtn.setEnabled(false);
            mCall = null;
            mReceiverIccid = "";
            return;
        }

        String s = mCall.getRemoteAddress().asString().toLowerCase();
        s = s.replaceAll("sip:", "");
        //s = s.substring(1, s.indexOf("@")); //從 1 開始是因為SIP帳號是 8 + iccid，所以要把 8 去掉
        s = s.substring(0, s.indexOf("@")); //從 1 開始是因為SIP帳號是 8 + iccid，所以要把 8 去掉
        mReceiverIccid = s;
        Log.d(TAG, "incoming call, look up caller ICCID: " + s);
        if (mReceiverIccid==null || mReceiverIccid.length()<1) return;

        mUserDBRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.getChildrenCount() > 0){
                    for(DataSnapshot snap: dataSnapshot.getChildren()){
                        User user = snap.getValue(User.class);
                        try {
                            if(user.getIccid()!=null && user.getIccid().equals(mReceiverIccid)){
                                mReceiverId = snap.getKey();
                                mReceiverName = user.getDisplayName();
                                mReceiverImageUrl = user.getImage();
                                Log.d(TAG, "incoming call, look up caller name: " + mReceiverName);
                                displayUserNameAndPicture();
                            }
                        } catch (Exception e) {
                            Toast.makeText(myContext, "Error reading contacts information: " + e.toString(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(myContext, App.getContext().getResources().getString(R.string.msgFailedToRetrieveDataFromFirebase), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayUserNameAndPicture(){
        //mUserPhotoImageView.setImageURI(Uri.parse(mReceiverImageUrl));
        if (mReceiverImageUrl!=null && mReceiverImageUrl.length()>0){
            Picasso.with(myContext).load(mReceiverImageUrl).placeholder(R.mipmap.ic_launcher).into(mUserPhotoImageView);
        }
        if (mReceiverName!=null && mReceiverName.length()>0) {
            mContactNameTextView.setText(mReceiverName);
        }else{
            mContactNameTextView.setText(getString(R.string.labelUnknown));
        }

    }

    //初始化 Linphone VoIP
    private void initLinphone(){
        if (mCallerAddress!=null && mCallerAddress.length()>0) { //這是 incoming call
            mIsCalling = true;
        }
        mIsConnected = false;
        mDoVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "mIsCalling = " + mIsCalling + ", mIsConnected = " + mIsConnected);
                if (mIsCalling) {   //撥號中，若為incoming call則接聽，否則為outgoing call，將撥號中斷
                    if (mCallerAddress!=null && mCallerAddress.length()>0) { //這是 incoming call
                        try {
                            if (mCall!=null){
                                Log.d(TAG, "OnClick, this is incoming call, user accept call");
                                mIsCalling = true;
                                mIsConnected = true;
                                mLinphoneCore.acceptCall(mCall);
                                mThreadHandler.post(doVoiceChat);
                            }
                        }catch (Exception e){
                            if (mCall!=null) mLinphoneCore.terminateCall(mCall);
                            mIsCalling = false;
                            mIsConnected = false;
                            Utility.showMessage(myContext, getString(R.string.msgFailedToPickUpTheCall));
                            finish();
                        }
                    }else{
                        if (mCall!=null){
                            Log.d(TAG, "OnClick, this is outgoing call, user terminate call");
                            mLinphoneCore.terminateCall(mCall);
                        }
                        mIsCalling = false;
                        mIsConnected = false;
                        finish();
                    }
                    mUI_Handler.post(setVoiceChatButtonText);
                }else{
                    if (mIsConnected) { //通話中，將電話掛斷
                        if (mCall!=null){
                            Log.d(TAG, "OnClick, in chatting, terminate call");
                            mLinphoneCore.terminateCall(mCall);
                        }
                        mIsCalling = false;
                        mIsConnected = false;
                        //mUI_Handler.post(setVoiceChatButtonText);
                        finish();
                    }else{  //idle狀態，進行撥號
                        Log.d(TAG, "OnClick, idle state, make call");
                        mIsCalling = true;
                        mIsConnected = false;
                        mThreadHandler.post(doVoiceChat);
                    }
                }
            }
        });

    }

    private Runnable setVoiceChatButtonText=new Runnable () {
        @Override
        public void run() {
            if (mIsConnected) { //通話中，將電話掛斷
                mStatusTextView.setText(getString(R.string.labrlVoiceCallChatting));
                mDoVoiceChatBtn.setText(getString(R.string.msgVoiceChatEndCall));
            }else{  //idle狀態或撥號中或等待接聽，進行撥號或掛斷
                if (mCallerAddress!=null && mCallerAddress.length()>0) { //這是 incoming call
                    if (mIsCalling) {   //等待接聽，顯示 Accept
                        mStatusTextView.setText(getString(R.string.labrlVoiceCallRinging));
                        mDoVoiceChatBtn.setText(getString(R.string.labelVoiceChatAcceptCall));
                        mCancelVoiceChatBtn.setText(getString(R.string.labelBack));
                    }else{
                        mStatusTextView.setText(getString(R.string.labrlVoiceCallEnd));
                        mDoVoiceChatBtn.setText(getString(R.string.labelVoiceCall));
                    }
                }else{  //這是outgoing call
                    if (mIsCalling) {   //撥號中，顯示 Abort
                        mStatusTextView.setText(getString(R.string.labrlVoiceCallRinging));
                        mDoVoiceChatBtn.setText(getString(R.string.msgVoiceChatAbort));
                    }else{
                        mStatusTextView.setText(getString(R.string.labrlVoiceCallEnd));
                        mDoVoiceChatBtn.setText(getString(R.string.labelVoiceCall));
                    }
                }
            }

            /*
            if (mIsCalling) {   //撥號中，顯示 Abort
                mStatusTextView.setText(getString(R.string.labrlVoiceCallRinging));
                mDoVoiceChatBtn.setText(getString(R.string.msgVoiceChatAbort));
            }else{
                if (mIsConnected) { //通話中，將電話掛斷
                    mStatusTextView.setText(getString(R.string.labrlVoiceCallChatting));
                    mDoVoiceChatBtn.setText(getString(R.string.msgVoiceChatEndCall));
                }else{  //idle狀態，進行撥號
                    mStatusTextView.setText(getString(R.string.labrlVoiceCallEnd));
                    mDoVoiceChatBtn.setText(getString(R.string.labelVoiceCall));
                }
            }
            */
        }
    };

    private Runnable doVoiceChat=new Runnable () {
        @Override
        public void run() {
            String receiverId = "";
            //if (!receiverId.equals("h1E5YDjxhURJcDUO4m1eOJBpbXQ2")) receiverId = "886986123101"; else receiverId = "886986123102";
            try {
                if (mCallerAddress==null || mCallerAddress.length()<1) { //這是 outgoing call
                    receiverId = mReceiverIccid;
                    //開始撥打電話
                    //mCall = mLinphoneCore.invite("sip:" + receiverId + "@" + mSIPDomain);
                    String sipAccountPrefix = Utility.getMySetting(myContext, "sipAccountPrefix");
                    if (sipAccountPrefix!=null && sipAccountPrefix.length()>0) receiverId = sipAccountPrefix + receiverId;
                    LinphoneAddress la = LinphoneCoreFactory.instance().createLinphoneAddress(receiverId, mSIPDomain, receiverId + "@" + mSIPDomain);
                    mCall = mLinphoneCore.invite(la);
                    mIsConnected = false;
                }

                long iterateIntervalMs = 50L;

                if (mCall == null) {
                    Log.d(TAG, "Could not place call to");
                    //mStatusTextView.setText(getString(R.string.msgFailedToMakeVoiceCall));
                    mIsCalling = false;
                    mIsConnected = false;
                    mUI_Handler.post(setVoiceChatButtonText);
                    return;
                } else {
                    if (mCallerAddress==null || mCallerAddress.length()<1) { //這是 outgoing call
                        Log.d(TAG, "Call to: " + receiverId + "@" + mSIPDomain);
                    }
                    mIsCalling = true;
                    //mIsConnected = false;
                    mUI_Handler.post(setVoiceChatButtonText);

                    while (mIsCalling) {
                        if (mLinphoneMiniManager.getRegistrationStatus()!=1 || mLinphoneCore==null || mCall==null){
                            mIsCalling = false;
                            mIsConnected = false;
                            return;
                        }

                        mLinphoneCore.iterate();

                        try {
                            Thread.sleep(iterateIntervalMs);
                            mUI_Handler.post(updateRegistrationState);

                            if (mCall.getState().equals(LinphoneCall.State.CallEnd)
                                    || mCall.getState().equals(LinphoneCall.State.CallReleased)) {
                                Log.d(TAG, "mCall.getState()=CallEnd or CallReleased");
                                mIsCalling = false;
                                mIsConnected = false;
                                mUI_Handler.post(setVoiceChatButtonText);
                            }

                            if (mCall.getState().equals(LinphoneCall.State.StreamsRunning)) {
                                //Log.d(TAG, "mCall.getState()=StreamsRunning");
                                mIsCalling = true;
                                mIsConnected = true;
                                mUI_Handler.post(setVoiceChatButtonText);
                            }

                            if (mCall.getState().equals(LinphoneCall.State.OutgoingRinging)) {
                                //Log.d(TAG, "mCall.getState()=OutgoingRinging");
                                mIsCalling = true;
                                mIsConnected = false;
                                mUI_Handler.post(setVoiceChatButtonText);
                            }


                        } catch (InterruptedException var8) {
                            Log.d(TAG, "Interrupted! Aborting");
                            mIsCalling = false;
                            mIsConnected = false;
                            //mStatusTextView.setText(getString(R.string.labrlVoiceCallInterrupted));
                            mUI_Handler.post(setVoiceChatButtonText);
                        }
                    }
                    if (!LinphoneCall.State.CallEnd.equals(mCall.getState())) {
                        Log.d(TAG, "Terminating the call");
                        mIsCalling = false;
                        mIsConnected = false;
                        mUI_Handler.post(setVoiceChatButtonText);
                        mLinphoneCore.terminateCall(mCall);
                    }
                }
            }catch (Exception e){
                Log.d(TAG, getString(R.string.msgFailedToMakeVoiceCall) + ", error= " + e.toString());
                Utility.showMessage(myContext, getString(R.string.msgFailedToMakeVoiceCall));
                mIsCalling = false;
                mIsConnected = false;
                mUI_Handler.post(setVoiceChatButtonText);
            }
        }
    };

    private Runnable updateRegistrationState=new Runnable () {
        @Override
        public void run() {
            try {
                int iState = mLinphoneMiniManager.getRegistrationStatus();
                String s = getString(R.string.labelRegistrationRetry);
                if (iState==0) s = getString(R.string.labelRegistrationInProgress);
                if (iState==1) s = getString(R.string.labelRegistrationOk);
                if (iState==5) s = getString(R.string.labelRegistrationFailed);
                getSupportActionBar().setTitle(s);
                getActionBar().setTitle(s);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    };


}
