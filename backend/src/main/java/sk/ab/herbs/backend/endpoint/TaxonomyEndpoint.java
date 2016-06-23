package sk.ab.herbs.backend.endpoint;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.repackaged.com.google.gson.JsonArray;
import com.google.appengine.repackaged.com.google.gson.JsonElement;
import com.google.appengine.repackaged.com.google.gson.JsonObject;
import com.google.appengine.repackaged.com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Named;

import sk.ab.herbs.backend.entity.Plant;
import sk.ab.herbs.backend.entity.Taxon;

/** An endpoint class we are exposing */
@Api(
        name = "taxonomyApi",
        version = "v1",
        namespace = @ApiNamespace(
                ownerDomain = "backend.herbs.ab.sk",
                ownerName = "backend.herbs.ab.sk",
                packagePath="endpoint"
        )
)
public class TaxonomyEndpoint {

    @ApiMethod(
            name = "insert",
            path = "{taxonomyName}",
            httpMethod = ApiMethod.HttpMethod.POST)
    public Entity insert(@Named("taxonomyName") String taxonomyName,
                         @Named("taxonomyPath") String taxonomyPath,
                         @Named("parentPath") String parentPath,
                         @Named("name") String name,
                         @Named("wikiName") String wikiName) {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        String[] path = taxonomyPath.split(",");
        String[] parent = parentPath.split(",");
        KeyFactory.Builder builder = new KeyFactory.Builder(path[0], parent[0]);
        if (path.length > 1) {
            for(int i=1; i < path.length; i++) {
                builder.addChild(path[i], parent[i]);
            }
        }

        Entity taxonomyEntity = new Entity(taxonomyName, name, builder.getKey());
        if (wikiName == null) {
            wikiName = name;
        }
        modifyEntityWikiSpecies(taxonomyEntity, wikiName);
        datastore.put(taxonomyEntity);

        return taxonomyEntity;
    }

    @ApiMethod(
            name = "plant",
            path = "plant/{taxonomyName}",
            httpMethod = ApiMethod.HttpMethod.POST)
    public Plant plant(@Named("taxonomyName") String taxonomyName,
                       @Named("taxonomyWiki") String taxonomyWiki,
                       Plant plant) {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        String[] hlp = taxonomyName.split(" ");
        String genus = hlp[0];

        Entity plantEntity = new Entity("Plant", taxonomyName);

        Query.Filter propertyFilter =
                new Query.FilterPredicate("la", Query.FilterOperator.EQUAL, genus);
        Query q = new Query("Genus").setFilter(propertyFilter);

        List<Entity> genuses =
                datastore.prepare(q).asList(FetchOptions.Builder.withDefaults());
        if (genuses.size() == 1) {
            Entity entity = genuses.get(0);

            plantEntity.setProperty("taxonomyKey", entity.getKey());
        }

        plantEntity.setProperty("wikidata", getWikidata(taxonomyWiki));

        getNamesFromWikiSpecies(plantEntity, taxonomyWiki);

        modifyEntityAddLinks(plantEntity);

        plantEntity.setProperty("id", plant.getPlantId());
        plantEntity.setProperty("wikiName", plant.getWikiName());
        plantEntity.setProperty("illustrationUrl", plant.getIllustrationUrl());
        plantEntity.setProperty("heightFrom", plant.getHeightFrom());
        plantEntity.setProperty("heightTo", plant.getHeightTo());
        plantEntity.setProperty("floweringFrom", plant.getFloweringFrom());
        plantEntity.setProperty("floweringTo", plant.getFloweringTo());
        plantEntity.setProperty("photoUrl", plant.getPhotoUrls());

        if (plant.getToxicityClass() != null) {
            plantEntity.setProperty("toxicityClass", plant.getToxicityClass());
        }

        plantEntity.setProperty("filterColor", plant.getFilterColor());
        plantEntity.setProperty("filterHabitat", plant.getFilterHabitat());
        plantEntity.setProperty("filterPetal", plant.getFilterPetal());

        for(Map.Entry<String, String> description : plant.getDescription().entrySet()) {
            plantEntity.setProperty("description-"+description.getKey(), description.getValue());
        }
        for(Map.Entry<String, String> flower : plant.getFlower().entrySet()) {
            plantEntity.setProperty("flower-"+flower.getKey(), flower.getValue());
        }
        for(Map.Entry<String, String> inflorescence : plant.getInflorescence().entrySet()) {
            plantEntity.setProperty("inflorescence-"+inflorescence.getKey(), inflorescence.getValue());
        }
        for(Map.Entry<String, String> fruit : plant.getFruit().entrySet()) {
            plantEntity.setProperty("fruit-"+fruit.getKey(), fruit.getValue());
        }
        for(Map.Entry<String, String> leaf : plant.getLeaf().entrySet()) {
            plantEntity.setProperty("leaf-"+leaf.getKey(), leaf.getValue());
        }
        for(Map.Entry<String, String> stem : plant.getStem().entrySet()) {
            plantEntity.setProperty("stem-"+stem.getKey(), stem.getValue());
        }
        for(Map.Entry<String, String> habitat : plant.getHabitat().entrySet()) {
            plantEntity.setProperty("habitat-"+habitat.getKey(), habitat.getValue());
        }
        for(Map.Entry<String, String> trivia : plant.getTrivia().entrySet()) {
            plantEntity.setProperty("trivia-"+trivia.getKey(), trivia.getValue());
        }
        for(Map.Entry<String, String> toxicity : plant.getToxicity().entrySet()) {
            plantEntity.setProperty("toxicity-"+toxicity.getKey(), toxicity.getValue());
        }
        for(Map.Entry<String, String> herbalism : plant.getHerbalism().entrySet()) {
            plantEntity.setProperty("herbalism-"+herbalism.getKey(), herbalism.getValue());
        }
        for(Map.Entry<String, List<String>> sourceUrl : plant.getSourceUrls().entrySet()) {
            if (sourceUrl.getValue() != null && sourceUrl.getValue().size() > 0) {
                plantEntity.setProperty("sourceUrl-" + sourceUrl.getKey(), sourceUrl.getValue());
            }
        }

        datastore.put(plantEntity);

        return plant;
    }

    @ApiMethod(
            name = "getTaxonomy",
            path = "find/{taxonLang}/{taxonName}/{taxonValue}",
            httpMethod = ApiMethod.HttpMethod.GET)
    public List<Taxon> getTaxonomy(@Named("taxonLang") String taxonLang,
                                   @Named("taxonName") String taxonName,
                                   @Named("taxonValue") String taxonValue,
                                   @Named("lang") String language) {

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query.Filter propertyFilter =
                new Query.FilterPredicate(taxonLang, Query.FilterOperator.EQUAL, taxonValue);
        Query q = new Query(taxonName).setFilter(propertyFilter);

        List<Taxon> results = new ArrayList<>();

        List<Entity> families =
                datastore.prepare(q).asList(FetchOptions.Builder.withDefaults());
        if (families.size() == 1) {
            Entity entity = families.get(0);

            do {
                Taxon taxon = new Taxon();
                taxon.setType(entity.getKind());

                Object latinProperty = entity.getProperty("la");
                List<String> latinName = new ArrayList<>();
                if (latinProperty != null) {
                    if (latinProperty instanceof String) {
                        latinName.add((String)latinProperty);
                    } else if (latinProperty instanceof List) {
                        latinName.addAll((List<String>)latinProperty);
                    }
                }
                taxon.setLatinName(latinName);

                List<String> name = new ArrayList<>();
                Object property = entity.getProperty(language);
                if (property != null) {
                  if (property instanceof String) {
                      name.add((String)property);
                  } else if (property instanceof List) {
                      name.addAll((List<String>)property);
                  }
                }
                taxon.setName(name);

                results.add(taxon);

                if (entity.getParent() != null) {
                    try {
                        entity = datastore.get(entity.getParent());
                    } catch (EntityNotFoundException e) {
                        entity = null;
                    }
                } else {
                    entity = null;
                }
            }
            while (entity != null);
        }

        return results;
    }

    private void modifyEntityWikiSpeciesAfterWikidata(Entity entity, String latinName) {
        try {
            List<String> latinAliases = (List<String>) entity.getProperty("aliases-la");
            if (latinAliases == null) {
                latinAliases = new ArrayList<>();
            }
            List<String> latinAliasesLower = new ArrayList<>();
            for (String latinAlias : latinAliases) {
                latinAliasesLower.add(latinAlias.toLowerCase());
            }

            for(String key: entity.getProperties().keySet()) {
                if (key.startsWith("aliases-")) {
                    List<String> aliasesOld = (List<String>) entity.getProperty(key);
                    List<String> aliases = new ArrayList<>();
                    for(String alias : aliasesOld) {
                        if (!alias.toLowerCase().equals(latinName.toLowerCase()) && !latinAliasesLower.contains(alias.toLowerCase())) {
                            aliases.add(alias);
                        }
                    }
                    if (aliases.size() > 0) {
                        entity.setProperty(key, aliases);
                    } else {
                        entity.removeProperty(key);
                    }
                }
            }

            Document doc = Jsoup.connect("https://species.wikimedia.org/w/index.php?title=" + latinName + "&action=edit").get();

            String wikiPage = doc.getElementsByTag("textarea").val();
            String vn = wikiPage.substring(wikiPage.indexOf("{{VN"), wikiPage.indexOf("}}", wikiPage.indexOf("{{VN"))).replace("\n", "");
            String[] vnItems = vn.substring(5, vn.length()-2).split("\\|");
            for(String vnItem : vnItems) {
                String[] hlp = vnItem.split("=");
                if(hlp.length > 1) {
                    String language = hlp[0].trim();

                    String[] names = hlp[1].trim().split(", ");
                    if (names.length == 1) {
                        names = hlp[1].split(" / ");
                    }

                    // remove latin names from species values
                    List<String> speciesValuesOld = new ArrayList<>(Arrays.asList(names));
                    List<String> speciesValues = new ArrayList<>();
                    for(String speciesValue : speciesValuesOld) {
                        if (!speciesValue.toLowerCase().equals(latinName.toLowerCase()) && !latinAliasesLower.contains(speciesValue.toLowerCase())) {
                            speciesValues.add(speciesValue);
                        }
                    }

                    if (entity.getProperty("label-"+language) == null) {
                        if (speciesValues.size() > 0) {
                            entity.setProperty("label-"+language, speciesValues.get(0));
                            speciesValues.remove(0);
                            if (speciesValues.size() > 0) {
                                entity.setProperty("aliases-"+language, speciesValues);
                            }
                        }
                    } else {
                        String label = entity.getProperty("label-"+language).toString();

                        if (label.equals(latinName) && !language.equals("la")) {
                            if (speciesValues.size() > 0) {
                                entity.setProperty("label-" + language, speciesValues.get(0));
                                speciesValues.remove(0);
                            }
                        }

                        if (entity.getProperty("aliases-"+language) != null) {
                            List<String> aliasesOld = (List<String>) entity.getProperty("aliases-" + language);
                            List<String> aliases = new ArrayList<>();
                            List<String> aliasesLower = new ArrayList<>();
                            for(String alias : aliasesOld) {
                                if (!alias.toLowerCase().equals(latinName.toLowerCase()) && !latinAliasesLower.contains(alias.toLowerCase())) {
                                    aliases.add(alias);
                                    aliasesLower.add(alias.toLowerCase());
                                }
                            }

                            for(String value : speciesValues) {
                                if (!aliasesLower.contains(value.toLowerCase()) && !label.toLowerCase().equals(value.toLowerCase())) {
                                    aliases.add(value);
                                    aliasesLower.add(value.toLowerCase());
                                }
                            }
                            if (aliases.size() > 0) {
                                entity.setProperty("aliases-" + language, aliases);
                            } else {
                                entity.removeProperty("aliases-" + language);
                            }
                        } else if (speciesValues.size() > 0) {
                            entity.setProperty("aliases-"+language, speciesValues);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void getNamesFromWikiSpecies(Entity entity, String name) {
        try {
            String oldRevision = getMyRevision(name);

            Document doc = Jsoup.connect("https://species.wikimedia.org/w/index.php?title=" + name + "&action=edit&oldid=" + oldRevision).get();

            String wikiPage = doc.getElementsByTag("textarea").val();
            String vn = wikiPage.substring(wikiPage.indexOf("{{VN"), wikiPage.indexOf("}}", wikiPage.indexOf("{{VN"))).replace("\n", "");
            String[] vnItems = vn.substring(5, vn.length()).split("\\|");
            for(String vnItem : vnItems) {
                String[] hlp = vnItem.split("=");
                if(hlp.length > 1) {
                    String language = hlp[0];

                    String[] names = hlp[1].trim().split(", ");
                    if (names.length == 1) {
                        names = hlp[1].split(" / ");
                    }
                    List<String> speciesValuesOld = new ArrayList<>(Arrays.asList(names));
                    List<String> speciesValues = new ArrayList<>();
                    for(String speciesValue : speciesValuesOld) {
                        speciesValues.add(speciesValue.trim());
                    }
                    if (speciesValues.size() > 1) {
                        entity.setProperty("label-"+language, speciesValues.get(0));
                        speciesValues.remove(0);
                        if (speciesValues.size() > 0) {
                            entity.setProperty("aliases-"+language, speciesValues);
                        }
                    } else {
                        entity.setProperty("label-"+language, hlp[1].trim());
                    }
                }
            }

            String synonyms = "";
            if (wikiPage.contains("==Name==")){
                synonyms = wikiPage.substring(wikiPage.indexOf("==Name=="), wikiPage.indexOf("==", wikiPage.indexOf("==Name==") + 8));
            } else if (wikiPage.contains("== Name ==")) {
                synonyms = wikiPage.substring(wikiPage.indexOf("== Name =="), wikiPage.indexOf("==", wikiPage.indexOf("== Name ==") + 10));
            }
            String[] lines = synonyms.split("\n");

            Set<String> synonymSet = new TreeSet<>();
            String key = "Synonyms";
            for(String line : lines) {
                if (line.trim().length() > 0) {
//                    if (line.trim().equals("{{HOT}}")) {
//                        if (synonymSet.size() > 0) {
//                            entity.setProperty(key, synonymList);
//                        }
//                        key = "Homotypic";
//                        synonymSet = new TreeSet<>();
//                        continue;
//                    } else if (line.trim().equals("{{HET}}")) {
//                        if (synonymSet.size() > 0) {
//                            entity.setProperty(key, synonymList);
//                        }
//                        key = "Heterotypic";
//                        synonymSet = new TreeSet<>();
//                        continue;
//                    } else if (line.trim().equals("{{BA}}")) {
//                        if (synonymSet.size() > 0) {
//                            entity.setProperty(key, synonymList);
//                        }
//                        key = "Basionym";
//                        synonymSet = new TreeSet<>();
//                        continue;
//                    }

                    if (line.contains("''")) {
                        String synonym = line.substring(line.indexOf("''")+2, line.indexOf("''", line.indexOf("''")+2));
                        synonymSet.add(synonym);
                    }
                }
            }
            synonymSet.remove(name);
            List<String> synonymList = new ArrayList<>();
            for(String synonym : synonymSet) {
                synonymList.add(synonym);
            }
            if (synonymList.size() > 0) {
                entity.setProperty(key, synonymList);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void modifyEntityWikiSpecies(Entity entity, String name) {
        try {
            Document doc = Jsoup.connect("https://species.wikimedia.org/w/index.php?title=" + name + "&action=edit").get();

            String wikiPage = doc.getElementsByTag("textarea").val();
            String vn = wikiPage.substring(wikiPage.indexOf("{{VN"), wikiPage.indexOf("}}", wikiPage.indexOf("{{VN"))).replace("\n", "");
            String[] languages = vn.substring(5, vn.length()).split("\\|");
            for(String language : languages) {
                String[] hlp = language.split("=");
                if(hlp.length > 1) {
                    String[] multiValue = hlp[1].trim().split(", ");
                    if (multiValue.length > 1) {
                        entity.setProperty(hlp[0].trim(), Arrays.asList(multiValue));
                    } else {
                        multiValue = hlp[1].split(" / ");
                        if (multiValue.length > 1) {
                            entity.setProperty(hlp[0].trim(), Arrays.asList(multiValue));
                        } else {
                            entity.setProperty(hlp[0].trim(), hlp[1].trim());
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void modifyEntityWikiData(Entity entity) {
        try {
            String id = entity.getProperty("wikidata").toString();

            URL url = new URL("https://www.wikidata.org/wiki/Special:EntityData/" + id + ".json");
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            request.connect();

            JsonParser jp = new JsonParser();
            JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent()));
            JsonObject wikidata = root.getAsJsonObject().getAsJsonObject("entities").getAsJsonObject(id);

            JsonObject labels = wikidata.getAsJsonObject("labels");
            JsonObject aliases = wikidata.getAsJsonObject("aliases");

            for (Map.Entry<String,JsonElement> entry : labels.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();

                entity.setProperty("label-"+value.get("language").getAsString(), value.get("value").getAsString());
            }

            for (Map.Entry<String,JsonElement> entry : aliases.entrySet()) {
                JsonArray value = entry.getValue().getAsJsonArray();

                List<String> aliasList = new ArrayList();
                for(JsonElement v : value) {
                    aliasList.add(v.getAsJsonObject().get("value").getAsString());
                }

                entity.setProperty("aliases-"+entry.getKey(), aliasList);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void modifyEntityAddLinks(Entity entity) {
        try {
            String id = entity.getProperty("wikidata").toString();

            URL url = new URL("https://www.wikidata.org/wiki/Special:EntityData/" + id + ".json");
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            request.connect();

            JsonParser jp = new JsonParser();
            JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent()));
            JsonObject wikidata = root.getAsJsonObject().getAsJsonObject("entities").getAsJsonObject(id);

            JsonObject sitelinks = wikidata.getAsJsonObject("sitelinks");

            for (Map.Entry<String,JsonElement> entry : sitelinks.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();

                String site = value.get("site").getAsString();
                site = site.substring(0, site.length()-4).replace("_", "-");

                entity.setProperty("wiki-"+site, value.get("url").getAsString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String getWikidata(String name) {
        try {
            Document doc = Jsoup.connect("https://species.wikimedia.org/wiki/" + name).get();

            String wikiPage = doc.getElementsByAttributeValue("title", "Edit interlanguage links").attr("href");

            return wikiPage.substring(wikiPage.lastIndexOf("/")+1, wikiPage.indexOf("#"));


        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getMyRevision(String name) {
        try {
            Document doc = Jsoup.connect("https://species.wikimedia.org/w/index.php?title=" + name + "&action=history").get();

            Elements links = doc.getElementsByClass("mw-changeslist-date");
            Elements users = doc.getElementsByClass("mw-userlink");

            int i = 0;
            for (Element user : users) {
                String href = user.attr("href");
                String userName = href.substring(href.indexOf("User:")+5);
                if (userName.startsWith("Adrian")) {
                    break;
                }
                i++;
            }

            Element link = links.get(i);
            String href = link.attr("href");

            String revisionId = href.substring(href.indexOf("oldid=")+6);
            return revisionId;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

}
