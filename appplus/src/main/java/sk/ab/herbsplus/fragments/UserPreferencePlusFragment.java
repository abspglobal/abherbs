package sk.ab.herbsplus.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Locale;

import sk.ab.common.util.Utils;
import sk.ab.herbsbase.AndroidConstants;
import sk.ab.herbsbase.fragments.UserPreferenceBaseFragment;
import sk.ab.herbsplus.R;
import sk.ab.herbsplus.SpecificConstants;
import sk.ab.herbsplus.StorageLoading;

/**
 * @see UserPreferenceBaseFragment
 *
 * plus preferences_plus:
 * - offline mode
 *
 * Created by adrian on 12. 3. 2017.
 */

public class UserPreferencePlusFragment extends UserPreferenceBaseFragment {

    private CheckBoxPreference prefOfflineMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_plus);

        final SharedPreferences preferences = getSharedPreferences();

        Boolean offlineMode = preferences.getBoolean(SpecificConstants.OFFLINE_MODE_KEY, false);
        prefOfflineMode = (CheckBoxPreference)findPreference("offlineMode");
        prefOfflineMode.setChecked(offlineMode);

        prefOfflineMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean newOfflineMode = (Boolean) newValue;
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(SpecificConstants.OFFLINE_MODE_KEY, newOfflineMode);
                editor.apply();

                DatabaseReference plantsRef = FirebaseDatabase.getInstance().getReference(AndroidConstants.FIREBASE_PLANTS);
                plantsRef.keepSynced(newOfflineMode);
                DatabaseReference taxonomyRef = FirebaseDatabase.getInstance().getReference(AndroidConstants.FIREBASE_APG_III);
                taxonomyRef.keepSynced(newOfflineMode);
                String language = preferences.getString(AndroidConstants.LANGUAGE_DEFAULT_KEY, Locale.getDefault().getLanguage());
                DatabaseReference searchInLanguageRef = FirebaseDatabase.getInstance().getReference(AndroidConstants.FIREBASE_SEARCH
                        + AndroidConstants.FIREBASE_SEPARATOR + language);
                searchInLanguageRef.keepSynced(newOfflineMode);
                DatabaseReference searchInLatinRef = FirebaseDatabase.getInstance().getReference(AndroidConstants.FIREBASE_SEARCH
                        + AndroidConstants.FIREBASE_SEPARATOR + AndroidConstants.LANGUAGE_LA);
                searchInLatinRef.keepSynced(newOfflineMode);
                DatabaseReference translationsInLanguage = FirebaseDatabase.getInstance().getReference(AndroidConstants.FIREBASE_TRANSLATIONS
                        + AndroidConstants.FIREBASE_SEPARATOR + language);
                translationsInLanguage.keepSynced(newOfflineMode);
                DatabaseReference translationsInEnglish = FirebaseDatabase.getInstance().getReference(AndroidConstants.FIREBASE_TRANSLATIONS
                        + AndroidConstants.FIREBASE_SEPARATOR + AndroidConstants.LANGUAGE_EN);
                translationsInEnglish.keepSynced(newOfflineMode);

                if (newOfflineMode) {
                    editor.remove(SpecificConstants.LAST_UPDATE_TIME_KEY);
                    editor.apply();
                    StorageLoading storageLoading = new StorageLoading(getActivity(), null);
                    storageLoading.downloadOfflineFiles();
                } else {
                    // delete offline files
                    Utils.deleteRecursive(getActivity().getFilesDir());
                }
                return true;
            }
        });
    }

    @Override
    protected SharedPreferences getSharedPreferences() {
        return getActivity().getSharedPreferences(SpecificConstants.PACKAGE, Context.MODE_PRIVATE);
    }

    @Override
    protected void updateViews() {
        super.updateViews();

        Resources resources = getResources();

        prefOfflineMode.setTitle(resources.getString(R.string.offline_mode));
        prefOfflineMode.setSummary(resources.getString(R.string.offline_mode_summary));
    }
}
