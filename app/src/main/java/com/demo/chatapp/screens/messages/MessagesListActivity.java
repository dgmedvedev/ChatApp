package com.demo.chatapp.screens.messages;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.demo.chatapp.pojo.Message;
import com.demo.chatapp.R;
import com.demo.chatapp.adapters.MessagesAdapter;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.List;

public class MessagesListActivity extends AppCompatActivity implements MessagesListView {

    private MessagesListPresenter presenter;

    private SharedPreferences preferences;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            result -> {
                presenter.onSignInResult(result, preferences);
            });

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

    private Menu optionsMenu;


    @Override
    protected void onResume() {
        super.onResume();
        if (presenter.isAuth) {
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
        if (presenter.isAuth) {
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
            presenter.signOut(signInLauncher, adapter);
        }
        if (item.getItemId() == R.id.itemSignIn) {
            mAuth.signOut();
            presenter.signOut(signInLauncher, adapter);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        presenter = new MessagesListPresenter(this, this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        reference = storage.getReference();
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        editTextMessage = findViewById(R.id.editTextMessage);
        imageViewSendMessage = findViewById(R.id.imageViewSendMessage);
        imageViewAddImage = findViewById(R.id.imageViewAddImage);
        adapter = new MessagesAdapter(this, presenter);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMessages.setAdapter(adapter);

        if (mAuth.getCurrentUser() != null) {
            presenter.isAuth = true;
            String author = mAuth.getCurrentUser().getEmail();
            preferences.edit().putString("author", author).apply();
        } else {
            presenter.signOut(signInLauncher, adapter);
        }

        presenter.swipe(recyclerViewMessages);

        imageViewSendMessage.setOnClickListener(view -> {
            if (presenter.isAuth) {
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
            if (presenter.isAuth) {
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

    @Override
    public void showToastMessage(String textToastMessage) {
        if (toastMessage != null) {
            toastMessage.cancel();
        }
        toastMessage = Toast.makeText(MessagesListActivity.this, textToastMessage, Toast.LENGTH_SHORT);
        toastMessage.show();
        adapter.notifyDataSetChanged();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
}