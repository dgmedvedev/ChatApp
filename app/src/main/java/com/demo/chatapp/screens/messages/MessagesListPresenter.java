package com.demo.chatapp.screens.messages;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.demo.chatapp.R;
import com.demo.chatapp.adapters.MessagesAdapter;
import com.demo.chatapp.pojo.Message;
import com.firebase.ui.auth.AuthUI;
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

public class MessagesListPresenter {
    private final MessagesListView VIEW;
    private final Context CONTEXT;
    private final String COLLECTION_NAME = "messages";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage; // создаем экземпляр FirebaseStorage (хранилище файлов)
    private StorageReference reference; // создаем ссылку для добавления/удаления файлов

    private SharedPreferences preferences;
    private boolean isAuth;

    public MessagesListPresenter(Context context, MessagesListView view) {
        this.CONTEXT = context;
        this.VIEW = view;
        loadData();
    }

    public boolean isAuth() {
        return isAuth;
    }

    private void loadData() {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance(); // создаем экземпляр FirebaseStorage (хранилище файлов)
        reference = storage.getReference();
        preferences = PreferenceManager.getDefaultSharedPreferences(CONTEXT);
    }

    public boolean verificationAuth() {
        if (mAuth.getCurrentUser() != null) {
            isAuth = true;
            String author = mAuth.getCurrentUser().getEmail();
            preferences.edit().putString("author", author).apply();
            return true;
        }
        return false;
    }

    public void displayList(RecyclerView recyclerView, MessagesAdapter adapter) {
        if (isAuth) {
            db.collection(COLLECTION_NAME).orderBy("date").addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                    if (value != null) {
                        List<Message> messages = value.toObjects(Message.class);
                        adapter.setMessages(messages);
                        //recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                    }
                }
            });
        }
    }

    public void signOut(ActivityResultLauncher<Intent> signInLauncher) {
        isAuth = false;
        AuthUI.getInstance()
                .signOut(CONTEXT)
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
    }

    public void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        IdpResponse response = result.getIdpResponse();
        if (result.getResultCode() == RESULT_OK) {
            // Successfully signed in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                VIEW.showToastMessage(CONTEXT.getString(R.string.welcome) + user.getEmail());
                preferences.edit().putString("author", user.getEmail()).apply();
                isAuth = true;
            }
        } else {
            if (response != null) {
                VIEW.showToastMessage(CONTEXT.getString(R.string.error) + response.getError());
            } else {
                VIEW.showToastMessage(CONTEXT.getString(R.string.autorization));
            }
        }
    }

    public void getImageResult(ActivityResult result) {
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
                                        VIEW.showToastMessage(CONTEXT.getString(R.string.error_loading_image));
                                    }
                                }
                            });
                }
            }
        } else {
            VIEW.showToastMessage(CONTEXT.getString(R.string.image_not_selected));
        }
    }

    public void sendMessage(String textOfMessage, String urlToImage) {
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
                            VIEW.scrollDownRecyclerView();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            VIEW.showToastMessage(CONTEXT.getString(R.string.message_not_sent));
                        }
                    });
        }
    }

    public void deleteMessage(int position) {
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
                                    VIEW.showToastMessage(CONTEXT.getString(R.string.delete_only_your_message));
                                }
                                return;
                            }
                        }
                    }
                });
    }

    public void swipe(RecyclerView recyclerView) {
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
                    VIEW.showToastMessage(CONTEXT.getString(R.string.autorization));
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }
}