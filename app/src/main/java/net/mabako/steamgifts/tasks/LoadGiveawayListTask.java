package net.mabako.steamgifts.tasks;

import android.os.AsyncTask;
import android.util.Log;

import net.mabako.steamgifts.data.Giveaway;
import net.mabako.steamgifts.fragments.GiveawayListFragment;
import net.mabako.steamgifts.web.WebUserData;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class LoadGiveawayListTask extends AsyncTask<Void, Void, List<Giveaway>> {
    private static final String TAG = LoadGiveawayListTask.class.getSimpleName();

    private final GiveawayListFragment fragment;
    private final int page;
    private final GiveawayListFragment.Type type;
    private final String searchQuery;

    public LoadGiveawayListTask(GiveawayListFragment activity, int page, GiveawayListFragment.Type type, String searchQuery) {
        this.fragment = activity;
        this.page = page;
        this.type = type;
        this.searchQuery = searchQuery;
    }

    @Override
    protected List<Giveaway> doInBackground(Void... params) {
        Log.d(TAG, "Fetching giveaways for page " + page);

        try {
            // Fetch the Giveaway page

            Connection jsoup = Jsoup.connect("http://www.steamgifts.com/giveaways/search");
            jsoup.data("page", Integer.toString(page));

            if(searchQuery != null)
                jsoup.data("q", searchQuery);

            if(type != GiveawayListFragment.Type.ALL)
                jsoup.data("type", type.name().toLowerCase());

            if (WebUserData.getCurrent().isLoggedIn())
                jsoup.cookie("PHPSESSID", WebUserData.getCurrent().getSessionId());
            Document document = jsoup.get();

            WebUserData.extract(document);

            // Do away with pinned giveaways.
            document.select(".pinned-giveaways__outer-wrap").html("");

            // Parse all rows of giveaways
            Elements giveaways = document.select(".giveaway__row-inner-wrap");
            Log.d(TAG, "Found inner " + giveaways.size() + " elements");

            List<Giveaway> giveawayList = new ArrayList<>();
            for (Element element : giveaways) {
                Element link = element.select("h2 a").first();
                Element icon = element.select("h2 a").last();

                // Basic information
                String title = link.text();
                String giveawayLink = link.attr("href").substring(10, 15);
                String giveawayName = link.attr("href").substring(16);

                String iconSplit = icon.attr("href");
                int gameId = iconSplit == null || iconSplit.length() < 5 ? -1 : Integer.parseInt(iconSplit.split("/")[4]);
                Giveaway.Type type = "app".equals(iconSplit != null && iconSplit.length() >= 4 ? iconSplit.split("/")[3] : "") ? Giveaway.Type.APP : Giveaway.Type.SUB;

                // Entries & Comments
                Elements links = element.select(".giveaway__links a span");
                int entries = Integer.parseInt(links.first().text().split(" ")[0].replace(",", ""));
                int comments = Integer.parseInt(links.last().text().split(" ")[0].replace(",", ""));

                String creator = element.select(".giveaway__username").text();

                // Copies & Points. They do not have separate markup classes, it's basically "if one thin markup element exists, it's one copy only"
                Elements hints = element.select(".giveaway__heading__thin");
                String copiesT = hints.first().text();
                String pointsT = hints.last().text();
                int copies = hints.size() == 1 ? 1 : Integer.parseInt(copiesT.replace("(", "").replace(" Copies)", ""));
                int points = Integer.parseInt(pointsT.replace("(", "").replace("P)", ""));

                // Time remaining
                Element timeRemaining = element.select(".giveaway__columns > div span").first();

                Log.v(TAG, "GIVEAWAY for " + title + ", " + giveawayLink + "/" + giveawayName + " is " + gameId);

                Giveaway giveaway = new Giveaway(title, giveawayLink, giveawayName, type, gameId, creator, entries, comments, copies, points, timeRemaining.text(), timeRemaining.attr("title"));
                giveaway.setEntered(element.hasClass("is-faded"));
                giveawayList.add(giveaway);
            }

            return giveawayList;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(List<Giveaway> result) {
        super.onPostExecute(result);
        fragment.addItems(result, page == 1);
    }
}