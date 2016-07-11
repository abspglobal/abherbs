package sk.ab.herbs.backend.util;

import com.google.appengine.repackaged.com.google.gson.JsonArray;
import com.google.appengine.repackaged.com.google.gson.JsonElement;
import com.google.appengine.repackaged.com.google.gson.JsonObject;
import com.google.appengine.repackaged.com.google.gson.JsonParser;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import retrofit.Call;
import sk.ab.common.entity.Plant;
import sk.ab.common.service.HerbCloudClient;

/**
 * Created by adrian on 4.5.2016.
 */
public class Updater {
    public static String PATH = "C:/Development/Projects/abherbs/backend/txt/";
//    public static String PATH = "/home/adrian/Dev/projects/abherbs/backend/txt/";
    public static String PLANTS_FILE = "plants.csv";




    public static String CELL_DELIMITER = ";";
    public static String ALIAS_DELIMITER = ",";

    public static void main(String[] params) {

        //missing();
        botanicjp();
    }

    private static void botanicjp() {
        try {
//            for(int i=97; i<123; i++) { //97 - 123
//
//                Document docList = Jsoup.connect("http://www.botanic.jp/contents/zz"+(char)i+".htm").timeout(10*1000).get();
//
//                Elements tables = docList.getElementsByTag("table");
//                Element table = tables.get(4);
//
//                Elements trs = table.getElementsByTag("tr");
//
//                for (Element tr : trs) {
//                    Elements tds = tr.getElementsByTag("td");
//                    if (tds.size() > 0) {
//                        Elements as = tds.get(0).getElementsByTag("a");
//                        if (as.size() > 0) {
//                            String latinName = as.get(0).text();
//                            String name = tds.get(1).text();
//                            String alias = "";
//
////                            if (latinName.compareTo("Spiraea salicifolia") < 0) {
////                                continue;
////                            }
//
//                            try {
//                                Document docPlant = Jsoup.connect("http://www.botanic.jp" + as.get(0).attr("href").substring(2)).timeout(10 * 1000).get();
//
//                                Elements spans = docPlant.getElementsByTag("span");
//                                if (spans.size() > 0) {
//                                    String txt = spans.get(0).text();
//                                    if (txt.indexOf("(") > -1) {
//                                        alias = txt.substring(txt.indexOf("(") + 1, txt.length() - 1);
//                                    }
//                                }
//                            } catch (HttpStatusException ex) {
//
//                            }
//
//                            System.out.println(latinName + CELL_DELIMITER + name + CELL_DELIMITER + alias);
//                        }
//                    }
//                }
//            }

            update("ja_names.csv", "ja", false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void piantemagiche() {

        Map<String, String> labels = new HashMap<>();

        try {
//            for(int i=1; i<30; i++) {
//
//                Document docList = Jsoup.connect("http://piantemagiche.it/piante-dalla-a-alla-z/"+i).timeout(10*1000).get();
//
//                Elements itemList = docList.getElementsByClass("listing-item");
//                for (Element li : itemList) {
//                    Elements as = li.getElementsByTag("a");
//                    if (as.size() > 0) {
//                        String name = as.get(0).text().replace(" (", ",").replace(")", "");
//
//                        String[] names = name.split(",");
//                        if (names.length == 2) {
//                            labels.put(names[0], names[1]);
//                            System.out.println(names[0] + "," + names[1]);
//                        }
//                    }
//                }
//            }

            File namefile = new File(PATH + "it_names.csv");

            Scanner namescan = new Scanner(namefile);
            while(namescan.hasNextLine()) {
                final String[] plantLine = namescan.nextLine().split(",");

                if (plantLine.length > 1) {
                    labels.put(plantLine[0], plantLine[1]);
                }
            }

            final HerbCloudClient herbCloudClient = new HerbCloudClient();

            File file = new File(PATH + "plants.csv");

            Scanner scan = new Scanner(file);
            while(scan.hasNextLine()) {

                final String[] plantLine = scan.nextLine().split(",");
                String nameLatin = plantLine[0];

                Call<Plant> callCloud = herbCloudClient.getApiService().getDetail(nameLatin);
                Plant plant = callCloud.execute().body();

                String valueLabel = plant.getLabel().get("it");
                if (valueLabel != null) {
                    valueLabel = valueLabel.toLowerCase();
                }

                String label = labels.get(nameLatin);
                if (label != null) {
                    label = label.toLowerCase();
                }

                if (label == null && plant.getSynonyms() != null) {
                    for (String synonym : plant.getSynonyms()) {
                        label = labels.get(synonym);
                        if (label != null) {
                            label = label.toLowerCase();
                            break;
                        }
                    }
                }

                if (label == null && valueLabel != null) {
                    label = valueLabel;
                }

                if (label != null) {
                    callCloud = herbCloudClient.getApiService().update(plantLine[0], "label_it", label, "replace", "string");
                    callCloud.execute();

                    ArrayList<String> aliasToSave = new ArrayList<>();
                    if (valueLabel != null && !label.equals(valueLabel)) {
                        aliasToSave.add(valueLabel);
                    }

                    ArrayList<String> valueAlias = plant.getNames().get("it");
                    if (valueAlias != null) {
                        for (String alias : valueAlias) {
                            alias = alias.toLowerCase();
                            if (!aliasToSave.contains(alias) && !alias.equals(label)) {
                                aliasToSave.add(alias);
                            }
                        }
                    }

                    if (aliasToSave.size() > 0) {
                        StringBuilder aliasSb = new StringBuilder();
                        for (String al : aliasToSave) {
                            if (aliasSb.length() > 0) {
                                aliasSb.append(",");
                            }
                            aliasSb.append(al);
                        }

                        callCloud = herbCloudClient.getApiService().update(plantLine[0], "alias_it", aliasSb.toString(), "replace", "list");
                        callCloud.execute();
                    }

                } else {
                    System.out.println(plantLine[0]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void miljolareSearch() {

        try {
            final HerbCloudClient herbCloudClient = new HerbCloudClient();

            File file = new File(PATH + "no_missing.txt");

            Scanner scan = new Scanner(file);
            while(scan.hasNextLine()){
                final String plantName = scan.nextLine();

                String json = Jsoup.connect("https://www.miljolare.no/sok/?format=json&limit=5&term="+plantName).ignoreContentType(true).execute().body();

                JsonParser jp = new JsonParser();
                JsonElement root = jp.parse(json);

                JsonArray treffs = root.getAsJsonObject().getAsJsonArray("treff");
                if (treffs.size() > 0) {
                    for (JsonElement treff : treffs) {
                        if (treff.getAsJsonObject().get("tittel").getAsString().contains(plantName)) {
                            Document docPlant = Jsoup.connect("https://www.miljolare.no" + treff.getAsJsonObject().get("url").getAsString()).get();

                            Elements taxonTable = docPlant.getElementsByClass("STABELL");
                            if (taxonTable.size() > 0) {
                                Elements taxons = taxonTable.get(0).getElementsByAttributeValue("style", "font-weight: bold;");
                                if (taxons.size() > 0) {
                                    Elements tds = taxons.get(0).getElementsByTag("td");
                                    if (tds.size() > 0) {

                                        if (tds.get(tds.size()-1).text().contains("(")) {
                                            String[] names = tds.get(tds.size() - 1).text().split(" \\(");
                                            names[0] = names[0].toLowerCase();
                                            names[1] = names[1].substring(0, names[1].length() - 1);

                                            Call<Plant> callCloud = herbCloudClient.getApiService().update(plantName, "label_no", names[0], "replace", "string");
                                            callCloud.execute();
                                        } else {
                                            System.out.println(plantName);
                                        }
                                    }
                                }

                            }

                            break;
                        }
                    }
                } else {
                    System.out.println(plantName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void miljolare() {

        Map<String, String> labels = new HashMap<>();

        try {

            Document docList = Jsoup.connect("https://www.miljolare.no/artstre/?or_id=2376&side=arter&start=1&antal=1311").get();

            Elements textList = docList.getElementsByTag("tbody");
            if (textList.size() > 0) {
                Elements trs = textList.get(0).getElementsByTag("tr");
                for(Element tr : trs) {
                    Elements tds = tr.getElementsByTag("td");
                    if (tds.size() > 1) {
                        String name = tds.get(0).text().replace(" (", ",").replace(")", "");

                        String[] names = name.split(",");
                        if (names.length == 2) {
                            labels.put(names[1], names[0]);
                        }
                    }
                }
            }

            final HerbCloudClient herbCloudClient = new HerbCloudClient();

            File file = new File(PATH + "plants.csv");

            Scanner scan = new Scanner(file);
            while(scan.hasNextLine()) {

                final String[] plantLine = scan.nextLine().split(",");
                String nameLatin = plantLine[0];

                Call<Plant> callCloud = herbCloudClient.getApiService().getDetail(nameLatin);
                Plant plant = callCloud.execute().body();

                String valueLabel = plant.getLabel().get("no");
                String label = labels.get(nameLatin);
                if (label == null && plant.getSynonyms() != null) {
                    for (String synonym : plant.getSynonyms()) {
                        label = labels.get(synonym);
                        if (label != null) {
                            label = label.toLowerCase();
                            break;
                        }
                    }
                } else {
                    label = label.toLowerCase();
                }

                if (valueLabel == null) {
                    if (label != null) {
                        callCloud = herbCloudClient.getApiService().update(plantLine[0], "label_no", label, "replace", "string");
                        callCloud.execute();
                    } else {
                        System.out.println(plantLine[0]);
                    }
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void luontoportti(String language) {

        Map<String, String> labels = new HashMap<>();
        Map<String, List<String>> aliases = new HashMap<>();

        try {
            Document docList = Jsoup.connect("http://www.luontoportti.com/suomi/" + language + "/kukkakasvit/?list=9").get();

            Elements textList = docList.getElementsByAttributeValue("id", "textList");
            if (textList.size() > 0) {
                Elements as = textList.get(0).getElementsByTag("a");
                for(Element a : as) {
                    String name = a.text();

                    Document plantDoc = Jsoup.connect(a.attr("href")).get();

                    Elements h4s = plantDoc.getElementsByTag("h4");
                    String nameLatin = null;
                    if (h4s.size() > 0) {
                        nameLatin = h4s.get(0).text();
                    }

                    labels.put(nameLatin, name);

                    String alias = "";
                    Elements texts = plantDoc.getElementsByAttributeValue("id", "teksti");
                    if (texts.size() > 0) {
                        Elements lis = texts.get(0).getElementsByTag("li");
                        for(Element li : lis) {
                            if (li.text().startsWith("Også kalt:")) {
                                alias = li.text().substring(10).trim();

                                aliases.put(nameLatin, Arrays.asList(alias.split(",")));
                                break;
                            }
                        }
                    }

                    System.out.println(nameLatin + ";" + name + ";" + alias);
                }
            }

//            File namefile = new File(PATH + language + "names.csv");
//
//            Scanner namescan = new Scanner(namefile);
//            while(namescan.hasNextLine()) {
//                final String[] plantLine = namescan.nextLine().split(";");
//
//                if (plantLine.length > 1) {
//                    labels.put(plantLine[0], plantLine[1]);
//                    if (plantLine.length > 2 && plantLine[2].length() > 0) {
//                        aliases.put(plantLine[0], Arrays.asList(plantLine[2].split(",")));
//                    }
//                }
//            }
//
//            final HerbCloudClient herbCloudClient = new HerbCloudClient();
//
//            File file = new File(PATH + "plants.csv");
//
//            Scanner scan = new Scanner(file);
//            while(scan.hasNextLine()) {
//
//                final String[] plantLine = scan.nextLine().split(",");
//                String nameLatin = plantLine[0];
//
//                Call<Plant> callCloud = herbCloudClient.getApiService().getDetail(nameLatin);
//                Plant plant = callCloud.execute().body();
//
//                String valueLabel = plant.getLabel().get(language);
//                String label = labels.get(nameLatin);
//                if (label == null && plant.getSynonyms() != null) {
//                    for (String synonym : plant.getSynonyms()) {
//                        label = labels.get(synonym);
//                        if (label != null) {
//                            label = label.toLowerCase();
//                            break;
//                        }
//                    }
//                } else {
//                    label = label.toLowerCase();
//                }
//
//                List<String> aliasesToSave = new ArrayList<>();
//
//                if (label != null && valueLabel != null && !label.equals(valueLabel.toLowerCase())) {
//                    aliasesToSave.add(valueLabel.toLowerCase());
//                }
//
//                ArrayList<String> valueAlias = plant.getNames().get(language);
//                if (valueAlias != null) {
//                    for (String al : valueAlias) {
//                        boolean isSynonym = al.startsWith(nameLatin);
//                        if (!isSynonym) {
//                            for (String synonym : plant.getSynonyms()) {
//                                isSynonym = al.startsWith(synonym);
//                                if (isSynonym) break;
//                            }
//                        }
//                        if (!isSynonym) {
//                            al = al.toLowerCase();
//                            if ((label == null && !valueLabel.equals(al)) || !label.equals(al)) {
//                                aliasesToSave.add(al);
//                            }
//                        }
//                    }
//                }
//                List<String> alias = aliases.get(nameLatin);
//                if (alias != null) {
//                    for (String al : alias) {
//                        al = al.toLowerCase();
//                        if (!aliasesToSave.contains(al)) {
//                            if ((label == null && !valueLabel.equals(al)) || !label.equals(al)) {
//                                aliasesToSave.add(al);
//                            }
//                        }
//                    }
//                }
//
//                StringBuilder aliasSb = new StringBuilder();
//                for (String al : aliasesToSave) {
//                    if (aliasSb.length() > 0) {
//                        aliasSb.append(",");
//                    }
//                    aliasSb.append(al);
//                }
//
//                if (label != null) {
//                    if (!label.equals(valueLabel)) {
//                        callCloud = herbCloudClient.getApiService().update(plantLine[0], "label_" + language, label, "replace", "string");
//                        callCloud.execute();
//                    }
//                } else {
//                    if (valueLabel == null) {
//                        System.out.println(plantLine[0]);
//                    } else {
//                        valueLabel = valueLabel.toLowerCase();
//                        callCloud = herbCloudClient.getApiService().update(plantLine[0], "label_" + language, valueLabel, "replace", "string");
//                        callCloud.execute();
//                    }
//                }
//
//                if (aliasSb.length() > 0) {
//                    StringBuilder valAlias = new StringBuilder();
//                    if (valueAlias != null) {
//                        for (String al : valueAlias) {
//                            if (valAlias.length() > 0) {
//                                valAlias.append(",");
//                            }
//                            valAlias.append(al);
//                        }
//                    }
//
//                    String s = aliasSb.toString();
//                    if (!s.equals(valAlias.toString())) {
//                        callCloud = herbCloudClient.getApiService().update(plantLine[0], "alias_" + language, s, "replace", "list");
//                        callCloud.execute();
//                    }
//                }
//
//            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void plantariumru() {

        try {
            final HerbCloudClient herbCloudClient = new HerbCloudClient();

            File file = new File(PATH + "plants.csv");
            int i = 0;

            Scanner scan = new Scanner(file);
            while (scan.hasNextLine()) {
                i++;
                final String[] plantLine = scan.nextLine().split(",");
                if (i < 425) continue;

                Document docPost = Jsoup.connect("http://www.plantarium.ru/page/search.html?match=begins&type=0&mode=full&sample=" + plantLine[0]).timeout(10 * 1000).get();

                String name = null;
                StringBuilder names = new StringBuilder();
                String nameLatin = null;

                Elements searchResult = docPost.getElementsByTag("a");
                for (Element link : searchResult) {
                    if (link.attr("href").startsWith("/page/view/item/")) {

                        Thread.sleep(3000);
                        Document docPlant = Jsoup.connect("http://www.plantarium.ru" + link.attr("href")).timeout(10 * 1000).get();

                        name = "";
                        names = new StringBuilder();
                        nameLatin = "";

                        Elements taxonNames = docPlant.getElementsByClass("taxon-name");
                        if (taxonNames.size() > 1) {
                            nameLatin = taxonNames.get(0).text() + " " + taxonNames.get(1).text();
                        }

                        System.out.println(plantLine[0] + " ... " + nameLatin);

                        if (plantLine[0].equals(nameLatin)) {
                            Elements ruNames = docPlant.getElementsByAttributeValue("id", "boxRusNamesList");
                            if (ruNames.size() > 0) {
                                Elements nameElements = ruNames.get(0).getElementsByTag("span");
                                if (nameElements.size() > 0) {
                                    name = nameElements.get(0).text();
                                    if (nameElements.size() > 1) {
                                        for (int j = 1; j < nameElements.size(); j++) {
                                            if (names.length() > 0) {
                                                names.append(",");
                                            }
                                            names.append(nameElements.get(j).text());
                                        }
                                    }
                                }
                            }
                        }

                        break;
                    }
                }

                if (name != null && name.length() > 0) {

                    Call<Plant> callCloud = herbCloudClient.getApiService().update(plantLine[0], "label_ru", name, "replace", "string");
                    callCloud.execute();

                    if (names.length() > 0) {
                        callCloud = herbCloudClient.getApiService().update(plantLine[0], "alias_ru", names.toString(), "replace", "list");
                        callCloud.execute();
                    }

                }

                Thread.sleep(3000);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void missing() {
        Map<String, BufferedWriter> missingFiles = new HashMap<>();
        String[] languages = {"la", "sk", "cs", "en", "fr", "pt", "es", "ru", "uk", "de", "no", "da", "fi", "sv", "is", "ja", "zh", "hu", "pl", "nl", "tr", "it", "ro", "lt", "lv"};

        try {
            final HerbCloudClient herbCloudClient = new HerbCloudClient();

            File file = new File(PATH + "plants.csv");

            Scanner scan = new Scanner(file);
            while (scan.hasNextLine()) {

                final String[] plantLine = scan.nextLine().split(",");
                String nameLatin = plantLine[0];
                System.out.println(nameLatin);

                Call<Plant> callCloud = herbCloudClient.getApiService().getDetail(nameLatin);
                Plant plant = callCloud.execute().body();

                for(String language : languages) {
                    String value = plant.getLabel().get(language);
                    if (value == null) {
                        BufferedWriter bw = missingFiles.get(language);
                        if (bw == null) {
                            File f = new File(PATH + language + "_missing.txt");
                            bw = new BufferedWriter(new FileWriter(f));

                            missingFiles.put(language, bw);
                        }
                        bw.write(nameLatin + "\n");
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                for (Map.Entry<String, BufferedWriter> bwEntry : missingFiles.entrySet()) {
                    bwEntry.getValue().close();
                }
            } catch (Exception e) {
            }
        }
    }

    private static void update(String fileWithNames, String language, boolean toLowerCase) throws IOException{
        Map<String, String> labels = new HashMap<>();
        Map<String, String> aliases = new HashMap<>();

        File nameFile = new File(PATH + fileWithNames);

        Scanner nameScan = new Scanner(nameFile);
        while(nameScan.hasNextLine()) {
            final String[] plantLine = nameScan.nextLine().split(CELL_DELIMITER);

            if (plantLine.length > 1) {
                labels.put(plantLine[0], plantLine[1]);
            }
            if (plantLine.length > 2) {
                aliases.put(plantLine[0], plantLine[2]);
            }
        }

        final HerbCloudClient herbCloudClient = new HerbCloudClient();

        File file = new File(PATH + PLANTS_FILE);

        Scanner scan = new Scanner(file);
        while(scan.hasNextLine()) {

            final String[] plantLine = scan.nextLine().split(CELL_DELIMITER);
            String nameLatin = plantLine[0];

            Call<Plant> callCloud = herbCloudClient.getApiService().getDetail(nameLatin);
            Plant plant = callCloud.execute().body();

            String existingLabel = plant.getLabel().get(language);
            if (toLowerCase && existingLabel != null) {
                existingLabel = existingLabel.toLowerCase();
            }

            String newLabel = labels.get(nameLatin);
            if (toLowerCase && newLabel != null) {
                newLabel = newLabel.toLowerCase();
            }

            if (newLabel == null && plant.getSynonyms() != null) {
                for (String synonym : plant.getSynonyms()) {
                    newLabel = labels.get(synonym);
                    if (toLowerCase && newLabel != null) {
                        newLabel = newLabel.toLowerCase();
                        break;
                    }
                }
            }

            if (newLabel == null && existingLabel != null) {
                newLabel = existingLabel;
            }

            if (newLabel != null) {
                callCloud = herbCloudClient.getApiService().update(plantLine[0], "label_"+language, newLabel, "replace", "string");
                callCloud.execute();

                ArrayList<String> aliasToSave = new ArrayList<>();
                if (existingLabel != null && !newLabel.equals(existingLabel)) {
                    aliasToSave.add(existingLabel);
                }

                ArrayList<String> existingAliases = plant.getNames().get(language);
                if (existingAliases != null) {
                    for (String alias : existingAliases) {
                        if (toLowerCase) {
                            alias = alias.toLowerCase();
                        }
                        if (!containsCaseInsensitive(alias, aliasToSave) && !alias.equals(newLabel)) {
                            aliasToSave.add(alias);
                        }
                    }
                }

                String newAliasesString = aliases.get(nameLatin);
                if (newAliasesString != null) {
                    ArrayList<String> newAliases = new ArrayList<>(Arrays.asList(newAliasesString.split(ALIAS_DELIMITER)));
                    for (String alias : newAliases) {
                        if (toLowerCase) {
                            alias = alias.toLowerCase();
                        }
                        if (!containsCaseInsensitive(alias, aliasToSave) && !alias.equals(newLabel)) {
                            aliasToSave.add(alias);
                        }
                    }
                }

                if (aliasToSave.size() > 0) {
                    StringBuilder aliasSb = new StringBuilder();
                    for (String al : aliasToSave) {
                        if (aliasSb.length() > 0) {
                            aliasSb.append(",");
                        }
                        aliasSb.append(al);
                    }

                    callCloud = herbCloudClient.getApiService().update(plantLine[0], "alias"+language, aliasSb.toString(), "replace", "list");
                    callCloud.execute();
                }

            } else {
                System.out.println(plantLine[0]);
            }
        }
    }

    private static boolean containsCaseInsensitive(String strToCompare, ArrayList<String>list)
    {
        for(String str:list)
        {
            if(str.equalsIgnoreCase(strToCompare))
            {
                return(true);
            }
        }
        return(false);
    }
}