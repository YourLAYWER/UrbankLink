package com.example.urbanklink.commuter;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.urbanklink.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ManageCardsActivity extends AppCompatActivity {

    private ManageCardsViewModel viewModel;
    private ViewPager2 viewPagerCards;
    private TabLayout tabDots;
    private CardCarouselAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manage_cards);

        HeaderUtils.setupHeader(this, true, false, true);

        // 1. Initialize UI
        viewPagerCards = findViewById(R.id.viewPagerCards);
        tabDots = findViewById(R.id.tabDots);

        adapter = new CardCarouselAdapter();
        viewPagerCards.setAdapter(adapter);

        new TabLayoutMediator(tabDots, viewPagerCards, (tab, position) -> {
            // Keep empty, binds the animated dots to the swiper
        }).attach();

        // 2. Initialize ViewModel
        viewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(ManageCardsViewModel.class);

        // 3. Setup Listeners and Observers
        setupObservers();
        setupClickListeners();

        // 4. Trigger initial network fetch
        viewModel.loadCards();
    }

    private void setupObservers() {
        viewModel.getSavedCards().observe(this, cards -> {
            if (cards != null && !cards.isEmpty()) {
                adapter.setCards(cards);
                viewPagerCards.setVisibility(View.VISIBLE);
                tabDots.setVisibility(View.VISIBLE);
            } else {
                // Hides the carousel completely if the user has no saved cards
                viewPagerCards.setVisibility(View.GONE);
                tabDots.setVisibility(View.GONE);
            }
        });

        viewModel.getActionSuccess().observe(this, success -> {
            if (success != null && success) {
                showSnackbar("Update successful!");
                // Force a fresh network pull so the UI reflects the new database state
                viewModel.loadCards();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                showErrorSnackbar(error);
            }
        });
    }

    private void setupClickListeners() {
        MaterialCardView btnSetDefault = findViewById(R.id.btnSetDefault);
        MaterialCardView btnDeleteCard = findViewById(R.id.btnDeleteCard);
        MaterialButton btnAddNewCard = findViewById(R.id.btnAddNewCard);

        btnSetDefault.setOnClickListener(v -> {
            if (adapter.getItemCount() > 0) {
                PaymentCard currentCard = adapter.getCardAt(viewPagerCards.getCurrentItem());
                if (!currentCard.isDefault()) {
                    viewModel.setCardAsDefault(currentCard.getCardId());
                } else {
                    showSnackbar("This is already your default card.");
                }
            }
        });

        btnDeleteCard.setOnClickListener(v -> {
            if (adapter.getItemCount() > 0) {
                PaymentCard currentCard = adapter.getCardAt(viewPagerCards.getCurrentItem());
                viewModel.deleteCard(currentCard.getCardId());
            }
        });

        btnAddNewCard.setOnClickListener(v -> {
            startActivity(new Intent(this, AddCardActivity.class));
        });
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show();
    }

    private void showErrorSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(Color.parseColor("#FF4C4C"));
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }
}