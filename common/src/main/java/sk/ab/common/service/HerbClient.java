package sk.ab.common.service;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 *
 * Created by adrian on 1.9.2015.
 */
public class HerbClient {
    public static final String API_URL = "http://appsresource.appspot.com";

    private HerbService herbService;

    public HerbClient() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        herbService = retrofit.create(HerbService.class);
    }

    public HerbService getApiService() {
        return herbService;
    }

}
