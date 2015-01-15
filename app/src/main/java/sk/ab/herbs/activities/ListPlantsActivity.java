package sk.ab.herbs.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.WindowManager;

import java.util.List;

import sk.ab.commons.BaseActivity;
import sk.ab.herbs.Plant;
import sk.ab.herbs.PlantHeader;
import sk.ab.herbs.R;
import sk.ab.herbs.fragments.PlantListFragment;
import sk.ab.herbs.fragments.rest.HerbDetailResponderFragment;

/**
 * User: adrian
 * Date: 10.1.2015
 * <p/>
 * Activity for displaying list of plants according to filter
 */
public class ListPlantsActivity extends BaseActivity {
    private List<PlantHeader> plants;
    private PlantListFragment mResultList;

    private HerbDetailResponderFragment detailResponder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.list_activity);

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.list_info, R.string.list_info) {
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        detailResponder = (HerbDetailResponderFragment) fm.findFragmentByTag("RESTDetailResponder");
        if (detailResponder == null) {
            detailResponder = new HerbDetailResponderFragment();
            ft.add(detailResponder, "RESTDetailResponder");
        }

        mResultList = new PlantListFragment();
        ft.replace(R.id.list_content, mResultList);

        ft.commit();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        mDrawerToggle.syncState();
    }

    @Override
    public void onStart() {
        super.onStart();
        plants = getIntent().getExtras().getParcelableArrayList("results");
        getSupportActionBar().setTitle(R.string.list_info);
        mResultList.recreateList(plants);
    }

    public void selectPlant(int position) {
        detailResponder.getDetail(plants.get(position).getPlantId());
    }

    public void setPlant(Plant plant) {
        Intent intent = new Intent(getBaseContext(), DisplayPlantActivity.class);
        intent.putExtra("plant", plant);
        startActivity(intent);
        invalidateOptionsMenu();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}