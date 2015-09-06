package sk.ab.herbs.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import java.util.Locale;

import sk.ab.herbs.Constants;
import sk.ab.herbs.HerbsApp;
import sk.ab.herbs.Plant;
import sk.ab.herbs.R;
import sk.ab.herbs.activities.DisplayPlantActivity;
import sk.ab.tools.Margin;
import sk.ab.tools.Utils;


/**
 * Created with IntelliJ IDEA.
 * User: adrian
 * Date: 11/26/14
 * Time: 9:23 PM
 * <p/>
 */
public class InfoFragment extends Fragment {

    private static final int INFO_SECTIONS = 6;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.plant_card_info, null);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getView() != null) {
            ImageView translateView = (ImageView) getView().findViewById(R.id.plant_translate);

            SharedPreferences preferences = getActivity().getSharedPreferences("sk.ab.herbs", Context.MODE_PRIVATE);
            String sLanguage = preferences.getString(Constants.LANGUAGE_DEFAULT_KEY, Locale.getDefault().getLanguage());

            if (((DisplayPlantActivity) getActivity()).getPlant().isTranslated(Constants.getLanguage(sLanguage))) {
                translateView.setVisibility(View.GONE);
            }

            setInfo(((DisplayPlantActivity) getActivity()).getPlant(), Constants.getLanguage(sLanguage));
        }
    }

    public void setInfo(Plant plant, int language) {
        TextView firstRow = (TextView) getView().findViewById(R.id.first_row);
        StringBuilder firstRowText = new StringBuilder();

        firstRowText.append(getResources().getString(R.string.plant_height_from));
        firstRowText.append(" <b>" + plant.getHeight_from()  + "</b>");
        firstRowText.append(" " + getResources().getString(R.string.plant_height_to));
        firstRowText.append(" "  + "<b>" + plant.getHeight_to() + "</b>");
        firstRowText.append(" " + Constants.HEIGHT_UNIT+". <br/>");

        firstRowText.append(getResources().getString(R.string.plant_flowering_from));
        firstRowText.append(" <b>" + Utils.getMonthName(plant.getFlowering_from()-1)  + "</b>");
        firstRowText.append(" " + getResources().getString(R.string.plant_flowering_to));
        firstRowText.append(" "  + "<b>" + Utils.getMonthName(plant.getFlowering_to() - 1) + "</b>.");

        firstRow.setText(Html.fromHtml(firstRowText.toString()));

        if (plant.getDescription() != null) {
            TextView upImage = (TextView) getView().findViewById(R.id.up_image);
            upImage.setText(Html.fromHtml(plant.getDescription().getText(language)));
        }

        final int[][] spanIndex = new int[2][INFO_SECTIONS];
        final StringBuilder text = new StringBuilder();
        String[][] sections = { {getResources().getString(R.string.plant_flowers), plant.getFlower().getText(language)},
                {getResources().getString(R.string.plant_inflorescences), plant.getInflorescence().getText(language)},
                {getResources().getString(R.string.plant_fruits), plant.getFruit().getText(language)},
                {getResources().getString(R.string.plant_leaves), plant.getLeaf().getText(language)},
                {getResources().getString(R.string.plant_stem), plant.getStem().getText(language)},
                {getResources().getString(R.string.plant_habitat), plant.getHabitat().getText(language)}
        };

        for(int i = 0; i < INFO_SECTIONS; i++ ) {
            spanIndex[0][i] = text.length();
            spanIndex[1][i] = text.length() + sections[i][0].length();
            text.append(sections[i][0]);
            text.append(": ");
            text.append(sections[i][1]);
            text.append(" ");
            //text.append("\n");
        }

        final TextView nextToImage = (TextView) getView().findViewById(R.id.next_to_image);
        final ImageView drawing = (ImageView) getView().findViewById(R.id.plant_background);
        final SpannableString ss = new SpannableString(text.toString());
        for(int i = 0; i < INFO_SECTIONS; i++ ) {
            ss.setSpan(new StyleSpan(Typeface.BOLD), spanIndex[0][i], spanIndex[1][i],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final DisplayMetrics dm = getActivity().getResources().getDisplayMetrics();
        final int orientation = getActivity().getResources().getConfiguration().orientation;

        if (plant.getBack_url() != null) {
            ImageLoader.getInstance().displayImage(plant.getBack_url(), drawing,
                    ((HerbsApp)getActivity().getApplication()).getOptions(), new SimpleImageLoadingListener() {
                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {

                    int width = (dm.widthPixels - Utils.convertDpToPx(25, dm))/2;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        width = width/2;
                    }
                    double ratio = (double)loadedImage.getWidth()/(double)loadedImage.getHeight();

                    drawing.getLayoutParams().width = width;
                    drawing.getLayoutParams().height = (int)(drawing.getLayoutParams().width/ratio);

                    int leftMargin = drawing.getLayoutParams().width;
                    int height = drawing.getLayoutParams().height;
                    ss.setSpan(new Margin(height / (int) (nextToImage.getLineHeight() * nextToImage
                            .getLineSpacingMultiplier() + nextToImage.getLineSpacingExtra()),
                            leftMargin), 0, ss.length(), Spanned.SPAN_PARAGRAPH);
                    nextToImage.setText(ss);
                }
            });

        } else {
            nextToImage.setText(ss);
        }
    }
}

