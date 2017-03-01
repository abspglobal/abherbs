package sk.ab.herbsbase.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.ViewTarget;

import java.util.List;
import java.util.Locale;

import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Call;
import sk.ab.common.Constants;
import sk.ab.common.entity.Plant;
import sk.ab.common.entity.PlantTaxon;
import sk.ab.common.entity.Taxonomy;
import sk.ab.common.service.HerbCloudClient;
import sk.ab.herbsbase.AndroidConstants;
import sk.ab.herbsbase.R;
import sk.ab.herbsbase.activities.DisplayPlantActivity;
import sk.ab.herbsbase.entity.PlantParcel;
import sk.ab.herbsbase.tools.Utils;


/**
 * Created with IntelliJ IDEA.
 * User: adrian
 * Date: 11/26/14
 * Time: 9:23 PM
 * <p/>
 */
public class TaxonomyFragment extends Fragment {

    private static String TAXON_LANGUAGE = "la";
    private static String TAXON_GENUS = "Genus";
    private static String TAXON_ORDO = "Ordo";
    private static String TAXON_FAMILIA = "Familia";

    private ImageView toxicityClass1;
    private ImageView toxicityClass2;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return View.inflate(getActivity().getBaseContext(), R.layout.plant_card_taxonomy, null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getView() != null) {
            toxicityClass1 = (ImageView) getView().findViewById(R.id.plant_toxicity_class1);
            toxicityClass2 = (ImageView) getView().findViewById(R.id.plant_toxicity_class2);
        }

        final DisplayPlantActivity displayPlantActivity = (DisplayPlantActivity) getActivity();
        final SharedPreferences preferences = displayPlantActivity.getSharedPreferences("sk.ab.herbs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        Boolean showWizard = !preferences.getBoolean(AndroidConstants.SHOWCASE_DISPLAY_KEY + AndroidConstants.VERSION_1_3_1, false)
                && preferences.getBoolean(Constants.SHOWCASE_DISPLAY_KEY + Constants.VERSION_1_2_7, false);
        final TextView nameView = (TextView) getView().findViewById(R.id.plant_species);

        if (showWizard) {
            new ShowcaseView.Builder(getActivity())
                    .withMaterialShowcase()
                    .setStyle(R.style.CustomShowcaseTheme)
                    .setTarget(new ViewTarget(nameView))
                    .hideOnTouchOutside()
                    .setContentTitle(R.string.showcase_taxonomy_title)
                    .setContentText(R.string.showcase_taxonomy_message)
                    .build();
            editor.putBoolean(AndroidConstants.SHOWCASE_DISPLAY_KEY + AndroidConstants.VERSION_1_3_1, true);
            editor.apply();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getView() != null) {
            setNames(getView());

            getView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getTaxonomy(((DisplayPlantActivity) getActivity()).getPlant(), getView());
                    Utils.setVisibility(v, R.id.plant_taxonomy);
                    Utils.setVisibility(v, R.id.agpiii);
                }
            });
        }
    }

    private void getTaxonomy(Plant plant, View view) {
        final DisplayPlantActivity displayPlantActivity = (DisplayPlantActivity) getActivity();
        final LinearLayout layout = (LinearLayout) view.findViewById(R.id.plant_taxonomy);

        if (layout.isShown() || layout.getChildCount() > 0) {
            return;
        }

        final HerbCloudClient herbCloudClient = new HerbCloudClient();

        displayPlantActivity.startLoading();
        displayPlantActivity.countButton.setVisibility(View.VISIBLE);
        String[] latinName = plant.getName().split(" ");
        herbCloudClient.getApiService().getTaxonomy(TAXON_LANGUAGE, TAXON_GENUS, latinName[0], Locale.getDefault().getLanguage())
                .enqueue(new Callback<Taxonomy>() {
                    @Override
                    public void onResponse(Call<Taxonomy> call, Response<Taxonomy> response) {
                        if (response != null && response.body() != null && response.body().getItems() != null) {
                            LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            for(PlantTaxon taxon : response.body().getItems()) {
                                View view = inflater.inflate(R.layout.taxon, null);
                                TextView textType = (TextView)view.findViewById(R.id.taxonType);
                                textType.setText(Utils.getId(AndroidConstants.RES_TAXONOMY_PREFIX + taxon.getType().toLowerCase(),R.string.class));

                                TextView textName = (TextView)view.findViewById(R.id.taxonName);
                                StringBuilder sbName = new StringBuilder();
                                if (taxon.getName() != null) {
                                    for (String s : taxon.getName()) {
                                        if (sbName.length() > 0) {
                                            sbName.append(", ");
                                        }
                                        sbName.append(s);
                                    }
                                }

                                TextView textLatinName = (TextView)view.findViewById(R.id.taxonLatinName);
                                StringBuilder sbLatinName = new StringBuilder();
                                if (taxon.getLatinName() != null) {
                                    for (String s : taxon.getLatinName()) {
                                        if (sbLatinName.length() > 0) {
                                            sbLatinName.append(", ");
                                        }
                                        sbLatinName.append(s);
                                    }
                                }

                                if (sbName.length() > 0) {
                                    textName.setText(sbName.toString());
                                    if (sbLatinName.length() > 0) {
                                        textLatinName.setText(sbLatinName.toString());
                                    } else {
                                        textLatinName.setVisibility(View.GONE);
                                    }
                                } else {
                                    if (sbLatinName.length() > 0) {
                                        textName.setText(sbLatinName.toString());
                                    } else {
                                        textName.setVisibility(View.GONE);
                                    }
                                    textLatinName.setVisibility(View.GONE);
                                }

                                if (taxon.getType().equals(TAXON_ORDO) || taxon.getType().equals(TAXON_FAMILIA)) {
                                    textName.setTypeface(Typeface.DEFAULT_BOLD);
                                }

                                layout.addView(view);
                            }

                        }
                        displayPlantActivity.stopLoading();
                        displayPlantActivity.countButton.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFailure(Call<Taxonomy> call, Throwable t) {
                        Log.e(this.getClass().getName(), "Failed to load data. Check your internet settings.", t);
                        displayPlantActivity.stopLoading();
                        displayPlantActivity.countButton.setVisibility(View.GONE);
                    }
                });
    }

    private void setNames(View view) {
        PlantParcel plant = ((DisplayPlantActivity) getActivity()).getPlant();
        Integer toxicityClass = plant.getToxicityClass();
        if (toxicityClass == null) {
            toxicityClass = 0;
        }
        switch (toxicityClass) {
            case 1:
                toxicityClass1.setVisibility(View.VISIBLE);
                toxicityClass2.setVisibility(View.GONE);
                break;
            case 2:
                toxicityClass1.setVisibility(View.GONE);
                toxicityClass2.setVisibility(View.VISIBLE);
                break;
            default:
                toxicityClass1.setVisibility(View.GONE);
                toxicityClass2.setVisibility(View.GONE);
        }


        String language = Locale.getDefault().getLanguage();

        boolean isLatinName = false;
        String label = plant.getLabel().get(language);
        if (label == null) {
            label = plant.getLabel().get(Constants.LANGUAGE_LA);
            isLatinName = true;
        }

        TextView species = (TextView) view.findViewById(R.id.plant_species);
        species.setText(label);
        if (!isLatinName) {
            TextView species_latin = (TextView) view.findViewById(R.id.plant_species_latin);
            species_latin.setText(plant.getLabel().get(Constants.LANGUAGE_LA));
        }
        TextView namesView = (TextView) view.findViewById(R.id.plant_alt_names);

        List<String> names = plant.getNames().get(language);
        if (names != null) {
            int i = 0;
            StringBuilder namesText = new StringBuilder();
            for (String name : names) {
                if (namesText.length() > 0) {
                    namesText.append(", ");
                }
                namesText.append(name);
                i++;
                if (i > Constants.NAMES_TO_DISPLAY) {
                    break;
                }
            }
            if (namesText.length() > 0) {
                namesView.setText(namesText.toString());
            } else {
                namesView.setVisibility(View.GONE);
            }
        } else {
            namesView.setVisibility(View.GONE);
        }
    }
}
