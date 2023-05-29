package com.demo.chatapp;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.demo.chatapp.adapters.MessagesAdapter;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            this::onSignInResult);

    private final ActivityResultLauncher<Intent> getImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::getImageResult);

    private final String COLLECTION_NAME = "messages";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage; // создаем экземпляр FirebaseStorage (хранилище файлов)
    private StorageReference reference; // создаем ссылку для добавления/удаления файлов

    private RecyclerView recyclerViewMessages;
    private MessagesAdapter adapter;
    private EditText editTextMessage;
    private ImageView imageViewSendMessage;
    private ImageView imageViewAddImage;

    private Toast toastMessage;
    private boolean isAuth;

    private Menu optionsMenu;

    private SharedPreferences preferences;

    @Override
    protected void onResume() {
        super.onResume();
        if (isAuth) {
            db.collection(COLLECTION_NAME).orderBy("date").addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                    if (value != null) {
                        List<Message> messages = value.toObjects(Message.class);
                        adapter.setMessages(messages);
                        recyclerViewMessages.scrollToPosition(adapter.getItemCount() - 1);
                    }
                }
            });
        }
        if (optionsMenu != null) {
            onCreateOptionsMenu(optionsMenu);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        optionsMenu = menu;
        menu.clear();
        if (isAuth) {
            getMenuInflater().inflate(R.menu.main_menu, menu);
        } else {
            getMenuInflater().inflate(R.menu.main_menu_sign_in, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.itemSignOut) {
            mAuth.signOut();
            signOut();
        }
        if (item.getItemId() == R.id.itemSignIn) {
            mAuth.signOut();
            signOut();
        }
        return super.onOptionsItemSelected(item);
    }

    private void showPopapMenu(View view, int position) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.inflate(R.menu.popupmenu);
        popupMenu
                .setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.itemShare:
                                Message message = adapter.getMessages().get(position);
                                if (message != null) {
                                    String imageUrl = message.getImageUrl();
                                    String textOfMessage = message.getTextOfMessage();
                                    String result;
                                    if (imageUrl != null) {
                                        result = imageUrl;
                                    } else {
                                        result = textOfMessage;
                                    }
                                    Intent intent = new Intent(Intent.ACTION_SEND);
                                    intent.setType("text/plain");
                                    intent.putExtra(Intent.EXTRA_TEXT, result);
                                    startActivity(intent);
                                    return true;
                                }
                            case R.id.itemDelete:
                                deleteMessage(position);
                                return true;
                            default:
                                return false;
                        }
                    }
                });

        popupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu menu) {
            }
        });
        popupMenu.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        reference = storage.getReference();
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        editTextMessage = findViewById(R.id.editTextMessage);
        imageViewSendMessage = findViewById(R.id.imageViewSendMessage);
        imageViewAddImage = findViewById(R.id.imageViewAddImage);
        adapter = new MessagesAdapter(this);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMessages.setAdapter(adapter);

        if (mAuth.getCurrentUser() != null) {
            isAuth = true;
            String author = mAuth.getCurrentUser().getEmail();
            preferences.edit().putString("author", author).apply();
        } else {
            signOut();
        }

        swipe();

        adapter.setOnMessageClickListener(new MessagesAdapter.OnMessageClickListener() {
            @Override
            public void onMessageClick(View view, int position) {
            }

            @Override
            public void onMessageLongClick(View view, int position) {
                showPopapMenu(view, position);
            }
        });

        imageViewSendMessage.setOnClickListener(view -> {
            if (isAuth) {
                String textOfMessage = editTextMessage.getText().toString().trim();
                if (isNetworkConnected()) {
                    if (!textOfMessage.isEmpty()) {
                        sendMessage(textOfMessage, null);
                    }
                } else {
                    showToastMessage("Проверьте подключение к интернету");
                }
            } else {
                showToastMessage("Авторизуйтесь");
            }
        });

        imageViewAddImage.setOnClickListener(view -> {
            if (isAuth) {
                if (isNetworkConnected()) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT); // получаем контент
                    intent.setType("image/jpeg"); // указываем какой именно контент необходимо получить
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true); // контент только с локального хранилища
                    getImageLauncher.launch(intent);
                } else {
                    showToastMessage("Проверьте подключение к интернету");
                }
            } else {
                showToastMessage("Авторизуйтесь");
            }
        });
    }

    private void sendMessage(String textOfMessage, String urlToImage) {
        Message message = null;
        String author = preferences.getString("author", "Anonym");
        if (textOfMessage != null && !textOfMessage.isEmpty()) {
            message = new Message(author, textOfMessage, System.currentTimeMillis(), null);
        } else if (urlToImage != null && !urlToImage.isEmpty()) {
            message = new Message(author, null, System.currentTimeMillis(), urlToImage);
        }
        if (message != null) {
            db.collection(COLLECTION_NAME).add(message)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                            editTextMessage.setText("");
                            recyclerViewMessages.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            editTextMessage.setText("");
                            showToastMessage("Сообщение не отправлено");
                        }
                    });
        }
    }

    private void signOut() {
        isAuth = false;
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        List<AuthUI.IdpConfig> providers = Arrays.asList(
                                new AuthUI.IdpConfig.EmailBuilder().build(),
                                new AuthUI.IdpConfig.GoogleBuilder().build());
                        Intent signInIntent = AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setAvailableProviders(providers)
                                .build();
                        signInLauncher.launch(signInIntent);
                    }
                });
        adapter.clearMessages();
    }

    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        IdpResponse response = result.getIdpResponse();
        if (result.getResultCode() == RESULT_OK) {
            // Successfully signed in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                showToastMessage("Добро пожаловать " + user.getEmail());
                preferences.edit().putString("author", user.getEmail()).apply();
                isAuth = true;
            }
        } else {
            if (response != null) {
                showToastMessage("Error: " + response.getError());
            } else {
                showToastMessage("Авторизуйтесь");
            }
        }
    }

    private void getImageResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                Uri uri = data.getData(); // Получаем uri из Галереи
                if (uri != null) {
                    final StorageReference referenceToImage = reference.child("images/" + uri.getLastPathSegment());
                    referenceToImage.putFile(uri)
                            .continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                                @Override
                                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                                    if (!task.isSuccessful()) {
                                        throw task.getException();
                                    }
                                    return referenceToImage.getDownloadUrl();
                                }
                            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                                @Override
                                public void onComplete(@NonNull Task<Uri> task) {
                                    if (task.isSuccessful()) {
                                        Uri downloadUri = task.getResult();
                                        if (downloadUri != null) {
                                            sendMessage(null, downloadUri.toString());
                                        }
                                    } else {
                                        showToastMessage("Error getImageResult");
                                    }
                                }
                            });
                }
            }
        } else {
            showToastMessage("Изображение не выбрано");
        }
    }

    private void swipe() {
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

                if (isAuth) {
                    deleteMessage(viewHolder.getAdapterPosition());
                } else {
                    showToastMessage("Авторизуйтесь");
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerViewMessages);
    }

    private void deleteMessage(int position) {
        db.collection(COLLECTION_NAME).orderBy("date").get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot querySnapshots) {
                        for (int i = 0; i < querySnapshots.getDocuments().size(); i++) {
                            if (i == position) {
                                String messageId = querySnapshots.getDocuments().get(i).getId();
                                String author = querySnapshots.getDocuments().get(i).getString("author");
                                String thisAuthor = Objects.requireNonNull(mAuth.getCurrentUser()).getEmail();
                                if (author != null && author.equals(thisAuthor)) {
                                    db.collection(COLLECTION_NAME).document(messageId).delete();
                                } else {
                                    showToastMessage("Удаляйте только свои сообщения");
                                }
                                return;
                            }
                        }
                    }
                });
    }

    private void showToastMessage(String textToastMessage) {
        if (toastMessage != null) {
            toastMessage.cancel();
        }
        toastMessage = Toast.makeText(MainActivity.this, textToastMessage, Toast.LENGTH_SHORT);
        toastMessage.show();
        adapter.notifyDataSetChanged();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
}