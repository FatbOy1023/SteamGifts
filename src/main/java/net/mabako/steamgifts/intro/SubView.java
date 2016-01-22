package net.mabako.steamgifts.intro;

import net.mabako.steamgifts.R;

/**
 * Created by mabako on 14.01.2016.
 */
public enum SubView {
    MAIN_WELCOME(R.layout.intro_main_welcome),
    MAIN_GIVEAWAY_1(R.layout.intro_main_giveaway_1),
    MAIN_GIVEAWAY_2(R.layout.intro_main_giveaway_2);

    private final int layout;

    SubView(int layout) {
        this.layout = layout;
    }

    public int getLayout() {
        return layout;
    }
}