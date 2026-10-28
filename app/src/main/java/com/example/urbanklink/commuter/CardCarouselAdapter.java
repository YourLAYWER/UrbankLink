package com.example.urbanklink.commuter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.urbanklink.R;

import java.util.ArrayList;
import java.util.List;

public class CardCarouselAdapter extends RecyclerView.Adapter<CardCarouselAdapter.CardViewHolder> {

    private List<PaymentCard> cards = new ArrayList<>();

    public void setCards(List<PaymentCard> newCards) {
        this.cards = newCards;
        notifyDataSetChanged();
    }

    public PaymentCard getCardAt(int position) {
        return cards.get(position);
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_carousel_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        PaymentCard card = cards.get(position);

        // 1. Safely handle nulls AND empty strings from C#
        String rawName = card.getCardHolderName();
        String holderName = (rawName != null && !rawName.trim().isEmpty()) ? rawName.toUpperCase() : "CARD HOLDER";

        String rawNumber = card.getMaskedCardNumber();
        String displayNum = (rawNumber != null && !rawNumber.trim().isEmpty()) ? rawNumber : "•••• •••• •••• XXXX";

        String rawExpiry = card.getExpiryDate();
        String expiry = (rawExpiry != null && !rawExpiry.trim().isEmpty()) ? rawExpiry : "MM/YY";

        String rawBrand = card.getBrand();
        String brandName = (rawBrand != null && !rawBrand.trim().isEmpty()) ? rawBrand.toUpperCase() : "URBANLINK";

        // 2. Bind the safe values to the UI
        holder.txtCardHolder.setText(holderName);
        holder.txtCardNumber.setText(displayNum);
        holder.txtExpDate.setText(expiry);

        if (card.isDefault()) {
            holder.txtBankName.setText(brandName + " (DEFAULT)");
        } else {
            holder.txtBankName.setText(brandName);
        }
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        TextView txtBankName, txtCardNumber, txtCardHolder, txtExpDate;
        ImageView imgCardType, imgEmvChip;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            txtBankName = itemView.findViewById(R.id.txtBankName);
            txtCardNumber = itemView.findViewById(R.id.txtCardNumber);
            txtCardHolder = itemView.findViewById(R.id.txtCardHolder);
            txtExpDate = itemView.findViewById(R.id.txtExpDate);
            imgCardType = itemView.findViewById(R.id.imgCardType);
            imgEmvChip = itemView.findViewById(R.id.imgEmvChip);
        }
    }
}