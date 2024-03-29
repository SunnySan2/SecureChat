package com.taisys.sc.securechat;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.Utility;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class UpdateProfileActivity extends AppCompatActivity {
    private static final String TAG = "SecureChat";

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_IMAGE_PICK = 2;

    private ImageView mUserPhotoImageView;
    private EditText mUserNameEdit;
    private Switch mAutoDecryptMessage;
    private Switch mBurnAfterReading;
    private Button mUpdateProfileBtn;
    private Button mCancelBtn;

    private Uri outputFileUri;
    private Bitmap imageBitmap = null;
    private byte[] byteArray = null;
    private DatabaseReference mUserDBRef;
    private StorageReference mStorageRef;
    private String mCurrentUserID;

    private ProgressDialog pg = null;
    private Context myContext = null;

    private boolean isPhotoChanged = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_profile);


        //init
        mUserPhotoImageView = (ImageView)findViewById(R.id.userPhotoUpdate);
        mUserNameEdit = (EditText)findViewById(R.id.userNameUpdate);
        mAutoDecryptMessage = (Switch)findViewById(R.id.autoDecryptMessageUpdate);
        mBurnAfterReading = (Switch)findViewById(R.id.burnAfterReadingUpdate);
        mUpdateProfileBtn = (Button)findViewById(R.id.updateUserProfileBtn);
        mCancelBtn = (Button)findViewById(R.id.cancelUpdateUserProfileBtn);

        mCurrentUserID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        //init firebase
        mUserDBRef = FirebaseDatabase.getInstance().getReference().child("Users");
        mStorageRef = FirebaseStorage.getInstance().getReference().child("Photos").child("Users");

        /**populate views initially**/
        populateTheViews();

        myContext = this;

        initSwitchValue();

        /**listen to imageview click**/
        mUserPhotoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(UpdateProfileActivity.this);
                builder.setTitle(getString(R.string.labelChangePhoto));
                builder.setMessage(getString(R.string.labelChooseAMethodToChangePhoto));
                builder.setPositiveButton(getString(R.string.labelUpload), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pickPhotoFromGallery();
                    }
                });
                builder.setNegativeButton(getString(R.string.labelCamera), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dispatchTakePictureIntent();
                    }
                });
                builder.create().show();

            }
        });

        /**listen to update btn click**/
        mUpdateProfileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userDisplayName = mUserNameEdit.getText().toString().trim();
                if (userDisplayName==null || userDisplayName.length()<1){
                    Utility.showMessage(myContext, getString(R.string.labelEnterYourName));
                    return;
                }

                /**Call the Firebase methods**/
                try {
                    showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgUpdateYourProfile));

                    if (mAutoDecryptMessage.isChecked()) {
                        Utility.setMySetting(myContext, "autoDecryptMessagge", "Y");
                    }else{
                        Utility.setMySetting(myContext, "autoDecryptMessagge", "N");
                    }

                    if (mBurnAfterReading.isChecked()) {
                        Utility.setMySetting(myContext, "burnAfterReading", "Y");
                    }else{
                        Utility.setMySetting(myContext, "burnAfterReading", "N");
                    }

                    updateUserName(userDisplayName);
                    if (byteArray!=null) {
                        updateUserPhoto(byteArray);
                    }else{
                        disWaiting();
                        Utility.showToast(myContext, getString(R.string.msgSuccess));
                        //closeActivity();
                    }
                } catch (Exception e) {
                    disWaiting();
                    e.printStackTrace();
                    Utility.showMessage(myContext, getString(R.string.msgFailedToUpdateYourProfile));
                }


            }
        });

        mCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        /**populate views initially**/
        populateTheViews();
    }

    private void showWaiting(final String title, final String msg) {
        disWaiting();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pg = new ProgressDialog(myContext);
                // }
                pg.setIndeterminate(true);
                pg.setCancelable(false);
                pg.setCanceledOnTouchOutside(false);
                pg.setTitle(title);
                pg.setMessage(msg);
                pg.show();
            }
        });
    }

    private void disWaiting() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pg != null && pg.isShowing()) {
                    pg.dismiss();
                }
            }
        });
    }

    private void populateTheViews(){
        mUserDBRef.child(FirebaseAuth.getInstance().getCurrentUser().getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                User currentuser = dataSnapshot.getValue(User.class);
                try {
                    String userPhoto = currentuser.getImage();
                    String userName = currentuser.getDisplayName();

                    //Log.d(TAG, "Picasso load image from firebase");
                    //如果不判斷 isPhotoChanged 的話，當用戶拍照或選完檔案後，onDataChange 又會被 trigger 一次，造成 Picasso 又 load 回舊的圖案
                    if (!isPhotoChanged) Picasso.with(UpdateProfileActivity.this).load(userPhoto).placeholder(R.mipmap.ic_launcher).into(mUserPhotoImageView);

                    mUserNameEdit.setText(userName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    //設定兩個 Switch 的值
    private void initSwitchValue(){
        String sAuto = Utility.getMySetting(myContext, "autoDecryptMessagge");
        String sBurn = Utility.getMySetting(myContext, "burnAfterReading");
        if (sAuto!=null && sAuto.equals("Y")) {
            mAutoDecryptMessage.setChecked(true);
        }else{
            mAutoDecryptMessage.setChecked(false);
        }

        if (sBurn!=null && sBurn.equals("Y")) {
            mBurnAfterReading.setChecked(true);
        }else{
            mBurnAfterReading.setChecked(false);
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void pickPhotoFromGallery(){
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        photoPickerIntent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(photoPickerIntent, REQUEST_IMAGE_PICK);
    }

    private void setMyImage(){
        mUserPhotoImageView.setImageBitmap(imageBitmap);
        mUserPhotoImageView.post(new Runnable() {
            @Override
            public void run() {
                mUserPhotoImageView.setVisibility(View.GONE);
                mUserPhotoImageView.setVisibility(View.VISIBLE);
            }
        });

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Log.d(TAG, "requestCode= " + String.valueOf(requestCode));
        //Log.d(TAG, "resultCode= " + String.valueOf(resultCode));
        //Log.d(TAG, "data= " + data.toString());
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Log.d(TAG, "camera image ok");
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            //imageBitmap = data.getExtras().getParcelable("data");
            Log.d(TAG, "camera image ok, imageBitmap= " + imageBitmap.toString());
            //Bitmap resized = Bitmap.createScaledBitmap(imageBitmap, 100, 100, true);
            mUserPhotoImageView.setImageBitmap(imageBitmap);
            isPhotoChanged = true;

            /**convert bitmap to byte array to store in firebase storage**/
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byteArray = stream.toByteArray();

        }else if(requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK){
            try{
                InputStream inputStream = this.getContentResolver().openInputStream(data.getData());
                imageBitmap = BitmapFactory.decodeStream(inputStream);
            }catch (FileNotFoundException e){
                Log.d(TAG, "Failed to get image stream");
                Utility.showMessage(myContext, getString(R.string.msgFailedToGetYourImage));
                return;
            }

            try {
                /*
                File filePhotoDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ "/SecureChat/");
                if (!filePhotoDir.exists()) filePhotoDir.mkdirs();
                File filePhoto = new File (filePhotoDir.getAbsolutePath()+"/myphoto.jpg");
                FileOutputStream fos = new FileOutputStream(filePhoto);

                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
                Picasso.with(UpdateProfileActivity.this).load(filePhoto).placeholder(R.mipmap.ic_launcher).into(mUserPhotoImageView);
                */
                mUserPhotoImageView.setImageBitmap(imageBitmap);
                isPhotoChanged = true;
                //Log.d(TAG, "imageBitmap byte count= " + String.valueOf(imageBitmap.getByteCount()) + ", X=" + String.valueOf(imageBitmap.getWidth()));
            }catch (Exception e){
                Utility.showMessage(myContext, getString(R.string.msgFailedToGetYourImage) + ", error: " + e.toString());
                isPhotoChanged = false;
            }

            /**convert bitmap to byte array to store in firebase storage**/
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byteArray = stream.toByteArray();
        }
    }

    private void updateUserName(String newDisplayName){
        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("displayName", newDisplayName);
        mUserDBRef.child(mCurrentUserID).updateChildren(childUpdates);
    }

    private void updateUserPhoto(byte[] photoByteArray){
        // Create file metadata with property to delete
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(null)
                .setContentLanguage("en")
                .build();

        mStorageRef.child(mCurrentUserID).putBytes(photoByteArray, metadata).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                if(!task.isSuccessful()){
                    //error saving photo
                    disWaiting();
                    Utility.showMessage(myContext, getString(R.string.msgFailedToUpdateYourProfile));
                }else{
                    //success saving photo
                    String userPhotoLink = task.getResult().getDownloadUrl().toString();
                    //now update the database with this user photo
                    Map<String, Object> childUpdates = new HashMap<>();
                    childUpdates.put("image", userPhotoLink);
                    mUserDBRef.child(mCurrentUserID).updateChildren(childUpdates);
                    Utility.showToast(myContext, getString(R.string.msgSuccess));
                    disWaiting();
                    //closeActivity();
                }

            }
        });
    }



}
