package com.demo.chatapp.screens.messages;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.demo.chatapp.adapters.MessagesAdapter;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MessagesListPresenter {
    private MessagesListView view;
    private Context context;

    private final String COLLECTION_NAME = "messages";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    public boolean isAuth;

    public MessagesListPresenter(Context context, MessagesListView view) {
        this.context = context;
        this.view = view;
    }

    public void signOut(ActivityResultLauncher<Intent> signInLauncher, MessagesAdapter adapter) {
        isAuth = false;
        AuthUI.getInstance()
                .signOut(context)
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

    public void onSignInResult(FirebaseAuthUIAuthenticationResult result, SharedPreferences preferences) {
        IdpResponse response = result.getIdpResponse();
        if (result.getResultCode() == RESULT_OK) {
            // Successfully signed in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                view.showToastMessage("Добро пожаловать " + user.getEmail());
                preferences.edit().putString("author", user.getEmail()).apply();
                isAuth = true;
            }
        } else {
            if (response != null) {
                view.showToastMessage("Error: " + response.getError());
            } else {
                view.showToastMessage("Авторизуйтесь");
            }
        }
    }

    public void deleteMessage(int position) {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
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
                                    view.showToastMessage("Удаляйте только свои сообщения");
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
                    view.showToastMessage("Авторизуйтесь");
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }
}