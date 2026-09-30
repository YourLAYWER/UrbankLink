package com.example.urbanklink;

import androidx.room.TypeConverter;

import java.util.ArrayList;
import java.util.List;

public class RouteIdsConverter {
    @TypeConverter
    public String fromList(List<Integer> list) {
        if (list == null || list.isEmpty()) return "";

        // Pad with commas on both sides so every ID is wrapped (e.g., ",1,2,5,")
        StringBuilder sb = new StringBuilder(",");
        for (int id : list) {
            sb.append(id).append(",");
        }
        return sb.toString();
    }

    @TypeConverter
    public List<Integer> toList(String data) {
        List<Integer> list = new ArrayList<>();
        if (data == null || data.isEmpty()) return list;

        String[] items = data.split(",");
        for (String item : items) {
            if (!item.isEmpty()) {
                list.add(Integer.parseInt(item));
            }
        }
        return list;
    }
}
