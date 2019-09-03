package com.example.chatapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    //View components variables
    ImageButton mPhotoPickerButton;
    EditText editTextMessage;
    Button buttonSend;
    ProgressBar progressBar;

    // Constants and variables
    public static final Integer DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String ANONYMOUS = "Anonymous";
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2;
    private String mUserName = ANONYMOUS;
    public static final String MSG_LENGTH_KEY = "Message_length_key";

    //RecyclerView variables
    private RecyclerView recyclerViewMessages;
    private ArrayList<Message> messageList;
    private LinearLayoutManager linearLayoutManager;
    private MessageAdapter adapter;


    // Firebase instance variables
    private FirebaseDatabase mFirebaseDatabase;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private DatabaseReference mDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        progressBar = findViewById(R.id.progress_bar);
        progressBar.setVisibility(ProgressBar.INVISIBLE);

        mPhotoPickerButton = findViewById(R.id.image_button_pick_img);
        editTextMessage = findViewById(R.id.edit_text_message_content);

        editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    buttonSend.setEnabled(true);
                    buttonSend.setBackgroundResource(R.drawable.background_redondeado_naranja);
                } else {
                    buttonSend.setEnabled(false);
                    buttonSend.setBackgroundResource(R.drawable.background_redondeado_gris);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        editTextMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});
        initializeRecyclerViewMessages();

        buttonSend = findViewById(R.id.button_send_message);

        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Message mMessage = new Message(editTextMessage.getText().toString(), mUserName, null);
                mDatabaseReference.push().setValue(mMessage);
                editTextMessage.setText(null);
            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    //user is signed in
                    onSignedInInitialize(user.getDisplayName());
                    //Toast.makeText(MainActivity.this, "Welcome user to ChatApp", Toast.LENGTH_SHORT).show();

                } else {
                    onSignedOutCleanup();
                    //user is signed out
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }

            }
        };

        // ImagePickerButton shows an image picker to upload an image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)//booleano que se crea al momento de debuguar (true) y es false cuando estamos en modo release.
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings); // Esta línea me permite extender la configuración también a modo release.

        //Definimos los parámetros de remoteConfig.
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);

        fetchConfig();
    }

    private void fetchConfig() {
        long cacheExpiration = 3600;

        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
            mFirebaseRemoteConfig.fetch(cacheExpiration)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            mFirebaseRemoteConfig.activateFetched();
                            applyRetrievedLengthLimit();

                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            applyRetrievedLengthLimit();
                        }
                    });
        }
    }

    private void applyRetrievedLengthLimit() {
        Long msg_length = mFirebaseRemoteConfig.getLong(MSG_LENGTH_KEY);
        editTextMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(msg_length.intValue()) {
        }});
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) { // si requestCode viene del flujo de signIn ...
            if (resultCode == RESULT_OK) { // y si además el resultado de la autenticación está OK ...
                Toast.makeText(this, "User signed in successfuly!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) { // pero si el resultado es Cancelled
                Toast.makeText(this, "User signed out successfuly!", Toast.LENGTH_SHORT).show();
                finish(); // salimos de la aplicación.
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {

            Uri selectedImageUri = data.getData();
            final StorageReference photoRef = mChatPhotosStorageReference.child(selectedImageUri.getLastPathSegment());
            UploadTask uploadTask = photoRef.putFile(selectedImageUri);

            final Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }

                    return photoRef.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        Message mMessage = new Message(null, mUserName, downloadUri.toString());
                        mDatabaseReference.push().setValue(mMessage);
                        linearLayoutManager.scrollToPosition(messageList.size() - 1);
                        //playMedia();
                    }
                }
            });
        }
    }

    private void initializeRecyclerViewMessages() {
        recyclerViewMessages = findViewById(R.id.messages_recycler_view);
        recyclerViewMessages.setHasFixedSize(true);
        linearLayoutManager = new LinearLayoutManager(this);
        recyclerViewMessages.setLayoutManager(linearLayoutManager);
        messageList = new ArrayList<>();
        adapter = new MessageAdapter(messageList, getApplicationContext());
        recyclerViewMessages.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
        if(messageList.size() > 0){
            linearLayoutManager.scrollToPosition(messageList.size() - 1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListener();
        messageList.clear();
    }

    private void onSignedInInitialize(String userName) {
        mUserName = userName;
        attachDatabaseReadListener();
    }

    private void attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    Message message = dataSnapshot.getValue(Message.class);
                    messageList.add(message);
                    linearLayoutManager.scrollToPosition(messageList.size() - 1);
                    //playMedia();
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            };
            mDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void onSignedOutCleanup() {
        mUserName = ANONYMOUS;
        messageList.clear(); // reemplaza a mMessageAdapter.clear(); del tutorial original.
        detachDatabaseReadListener();
    }

    private void detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_item, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void playMedia (){
        MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.notification);
        mediaPlayer.start();
    }
}
