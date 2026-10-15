package com.moakiee.ae2lt.packaged.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

import appeng.client.gui.style.ScreenStyle;

class PackagedProviderScreenStyleTest {
    @Test
    void copiesLoadedStyleWithoutResourceDeserializersOrSharedPositions() {
        var original = ScreenStyle.GSON.fromJson("""
                {
                  "background": {
                    "texture": "ae2lt:textures/guis/overloaded_pattern_provider.png",
                    "srcRect": [0, 0, 176, 245]
                  },
                  "palette": {"DEFAULT_TEXT_COLOR": "#112233"},
                  "slots": {"ENCODED_PATTERN": {"left": 8, "top": 42}},
                  "text": {"dialog_title": {
                    "text": {"translate": "container.inventory"},
                    "position": {"left": 8, "top": 6}
                  }},
                  "widgets": {"adapterCard": {"right": 2, "top": 72}}
                }
                """, ScreenStyle.class);
        var copy = PackagedPatternProviderScreen.copyStyle(original);

        assertNotSame(original, copy);
        assertEquals(176, copy.getBackground().getSrcWidth());
        assertEquals(245, copy.getBackground().getSrcHeight());
        assertEquals(ScreenStyle.GSON.toJson(original), ScreenStyle.GSON.toJson(copy));
        copy.getSlots().get("ENCODED_PATTERN").setTop(32);
        copy.getText().get("dialog_title").getPosition().setTop(16);
        copy.getWidget("adapterCard").setTop(62);
        assertEquals(42, original.getSlots().get("ENCODED_PATTERN").getTop());
        assertEquals(6, original.getText().get("dialog_title").getPosition().getTop());
        assertEquals(72, original.getWidget("adapterCard").getTop());
    }
}
