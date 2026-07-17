package com.megatech.fms.model;

import com.megatech.fms.helpers.HttpClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 */
public class RefuelViewModel {

    /**
     * An array of sample (dummy) items.
     */
    public static final List<RefuelItemData> ITEMS = new ArrayList<RefuelItemData>();

    /**
     * A map of sample (dummy) items, by ID.
     */
    public static final Map<Integer, RefuelItemData> ITEM_MAP = new HashMap<Integer, RefuelItemData>();

    private static final int COUNT = 25;

    static {
        // Add some sample items.
        loadItems();

    }

    public static  void loadItems()
    {
        HttpClient client = new HttpClient();
        List<RefuelItemData> list = client.getRefuelList();
        if (list !=null){
            ITEMS.clear();
            ITEM_MAP.clear();
            ITEMS.add(new RefuelItemData());
            for(int i=0; i<list.size(); i++)
                addItem(list.get(i));
        }



    }
    private static void addItem(RefuelItemData item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.getId(), item);
    }

    private static DummyItem createDummyItem(int position) {
        return new DummyItem(String.valueOf(position), "Item " + position, makeDetails(position));
    }

    private static String makeDetails(int position) {
        StringBuilder builder = new StringBuilder();
        builder.append("Details about Item: ").append(position);
        for (int i = 0; i < position; i++) {
            builder.append("\nMore details information here.");
        }
        return builder.toString();
    }

    /**
     * A dummy item representing a piece of content.
     */
    public static class DummyItem {
        public final String id;
        public final String content;
        public final String details;

        public DummyItem(String id, String content, String details) {
            this.id = id;
            this.content = content;
            this.details = details;
        }

        @Override
        public String toString() {
            return content;
        }
    }
}
