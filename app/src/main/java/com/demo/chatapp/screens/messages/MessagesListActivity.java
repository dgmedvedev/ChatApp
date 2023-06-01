package com.demo.chatapp.screens.messages;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.demo.chatapp.R;
import com.demo.chatapp.adapters.MessagesAdapter;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

public class MessagesListActivity extends AppCompatActivity implements MessagesListView {

    private MessagesListPresenter presenter;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            result -> {
                presenter.onSignInResult(result);
            });
    private final ActivityResultLauncher<Intent> getImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                presenter.getImageResult(result);
            });

    private RecyclerView recyclerViewMessages;
    private MessagesAdapter adapter;
    private EditText editTextMessage;
    private FloatingActionButton floatingActionButtonMessages;

    private FirebaseAuth mAuth;
    private Toast toastMessage;
    private Menu optionsMenu;

    @Override
    protected void onResume() {
        super.onResume();
        presenter.displayList(recyclerViewMessages, adapter);
        if (optionsMenu != null) {
            onCreateOptionsMenu(optionsMenu);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        optionsMenu = menu;
        menu.clear();
        if (presenter.isAuth()) {
            getMenuInflater().inflate(R.menu.main_menu, menu);
        } else {
            getMenuInflater().inflate(R.menu.main_menu_sign_in, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (isNetworkConnected()) {
            if (item.getItemId() == R.id.itemSignOut || item.getItemId() == R.id.itemSignIn) {
                mAuth.signOut();
                signOut();
            }
        } else {
            showToastMessage(getString(R.string.network_error));
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_message);
        presenter = new MessagesListPresenter(this, this);
        mAuth = FirebaseAuth.getInstance();

        ImageView imageViewSendMessage = findViewById(R.id.imageViewSendMessage);
        ImageView imageViewAddImage = findViewById(R.id.imageViewAddImage);
        floatingActionButtonMessages = findViewById(R.id.floatingActionButtonMessages);
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        editTextMessage = findViewById(R.id.editTextMessage);
        adapter = new MessagesAdapter(this, presenter);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        //presenter.displayList(recyclerViewMessages, adapter);
        recyclerViewMessages.setAdapter(adapter);

        if (!presenter.verificationAuth()) {
            if (isNetworkConnected()) {
                signOut();
            } else {
                showToastMessage(getString(R.string.network_error));
            }
        }

        presenter.swipe(recyclerViewMessages);

        imageViewSendMessage.setOnClickListener(view -> {
            if (presenter.isAuth()) {
                String textOfMessage = editTextMessage.getText().toString().trim();
                if (isNetworkConnected()) {
                    if (!textOfMessage.isEmpty()) {
                        presenter.sendMessage(textOfMessage, null);
                    }
                } else {
                    showToastMessage(getString(R.string.network_error));
                }
            } else {
                showToastMessage(getString(R.string.autorization));
            }
        });

        imageViewAddImage.setOnClickListener(view -> {
            if (presenter.isAuth()) {
                if (isNetworkConnected()) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT); // получаем контент
                    intent.setType("image/jpeg"); // указываем какой именно контент необходимо получить
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true); // контент только с локального хранилища
                    getImageLauncher.launch(intent);
                } else {
                    showToastMessage(getString(R.string.network_error));
                }
            } else {
                showToastMessage(getString(R.string.autorization));
            }
        });

        adapter.setOnReachEndListener(new MessagesAdapter.OnReachEndListener() {
            @Override
            public void onReachEnd() {
                floatingActionButtonMessages.hide();
            }

            @Override
            public void onReachNotEnd() {
                floatingActionButtonMessages.show();
            }
        });

        floatingActionButtonMessages.setOnClickListener(view -> {
            scrollDownRecyclerView();
            floatingActionButtonMessages.hide();
        });
    }

    @Override
    public void scrollDownRecyclerView() {
        editTextMessage.setText("");
        recyclerViewMessages.scrollToPosition(adapter.getItemCount() - 1);
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

    private void signOut() {
        presenter.signOut(signInLauncher);
        adapter.clearMessages();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
}