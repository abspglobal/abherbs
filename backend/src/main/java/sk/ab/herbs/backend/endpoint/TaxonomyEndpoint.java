package sk.ab.herbs.backend.endpoint;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Named;
import javax.naming.InvalidNameException;

import sk.ab.common.Constants;
import sk.ab.common.entity.Count;
import sk.ab.common.entity.PlantHeader;
import sk.ab.common.entity.Plant;
import sk.ab.common.entity.request.ListRequest;
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
            name = "count",
            path = "plant/count",
            httpMethod = ApiMethod.HttpMethod.POST)
    public Count count(ListRequest listRequest) {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query.Filter filter = null;
        for (Map.Entry<String, String> filterAttribute: listRequest.getFilterAttributes().entrySet()) {
            if (filter == null) {
              filter = new Query.FilterPredicate(filterAttribute.getKey(), Query.FilterOperator.EQUAL, filterAttribute.getValue());
            } else {
               filter = Query.CompositeFilterOperator.and(filter, new Query.FilterPredicate(filterAttribute.getKey(),
                       Query.FilterOperator.EQUAL, filterAttribute.getValue()));
            }
        }
        Query query = new Query(listRequest.getEntity());
        if (filter != null) {
            query.setFilter(filter);
        }
        return new Count(datastore.prepare(query).countEntities(FetchOptions.Builder.withDefaults()));
    }

    @ApiMethod(
            name = "list",
            path = "plant/list",
            httpMethod = ApiMethod.HttpMethod.POST)
    public List<PlantHeader> list(ListRequest listRequest) throws InvalidNameException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Query.Filter filter = null;
        for (Map.Entry<String, String> filterAttribute: listRequest.getFilterAttributes().entrySet()) {
            if (filter == null) {
                filter = new Query.FilterPredicate(filterAttribute.getKey(), Query.FilterOperator.EQUAL, filterAttribute.getValue());
            } else {
                filter = Query.CompositeFilterOperator.and(filter, new Query.FilterPredicate(filterAttribute.getKey(),
                        Query.FilterOperator.EQUAL, filterAttribute.getValue()));
            }
        }
        Query query = new Query(listRequest.getEntity());
        if (filter != null) {
            query.setFilter(filter);
        }

        Map<String, String> families = new HashMap<>();
        List<PlantHeader> plantHeaders = new ArrayList<>();
        List<Entity> plants = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
        for (Entity plant : plants) {
            PlantHeader plantHeader = new PlantHeader();
            plantHeader.setId((String)plant.getProperty("label_" + Constants.LANGUAGE_LA));

            String label = (String)plant.getProperty("label_" + listRequest.getLanguage());
            if (label == null) {
                label = (String)plant.getProperty("label_" + Constants.LANGUAGE_LA);
            }
            plantHeader.setLabel(label);

            List<String> photoUrls = (List<String>) plant.getProperty("photoUrl");
            plantHeader.setUrl(photoUrls.get(0));

            try {
                Key taxonomyKey = (Key)plant.getProperty("taxonomyKey");
                if (taxonomyKey == null) {
                    throw new InvalidNameException("Invalid taxonomy key for " + plantHeader.getLabel());
                }

                Key familiaKey = taxonomyKey;
                do {
                    familiaKey = familiaKey.getParent();
                } while (familiaKey != null && !familiaKey.getKind().equals("Familia"));

                if (familiaKey == null) {
                    throw new InvalidNameException("Invalid key: " + taxonomyKey.toString());
                }

                String family = families.get(familiaKey.toString());
                String familyLatin = families.get(familiaKey.toString() + Constants.LANGUAGE_LA);
                if (family == null) {
                    Entity familia = datastore.get(familiaKey);
                    familyLatin = (String) familia.getProperty(Constants.LANGUAGE_LA);

                    family = familyLatin;
                    Object property = familia.getProperty(listRequest.getLanguage());
                    if (property != null) {
                        if (property instanceof String) {
                            family = (String) property;
                        } else if (property instanceof List && ((List) property).size() > 0) {
                            family = ((List<String>) property).get(0);
                        }
                    }
                    families.put(familiaKey.toString(), family);
                    families.put(familiaKey.toString() + Constants.LANGUAGE_LA, familyLatin);
                }
                plantHeader.setFamily(family);
                plantHeader.setFamilyLatin(familyLatin);
            } catch (EntityNotFoundException e) {
                e.printStackTrace();
            }

            plantHeaders.add(plantHeader);
        }

        return plantHeaders;
    }

    @ApiMethod(
            name = "detail",
            path = "plant/{plantName}",
            httpMethod = ApiMethod.HttpMethod.GET)
    public Plant detail(@Named("plantName") String plantName) throws EntityNotFoundException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        Key key = KeyFactory.createKey(Constants.PLANT, plantName);
        Entity plantEntity = datastore.get(key);

        Plant plant = new Plant();
        plant.setName(plantName);

        for(Map.Entry<String, Object> propertyEntry : plantEntity.getProperties().entrySet()) {
            String propertyName = propertyEntry.getKey();
            switch (propertyName) {
                case "id":
                    plant.setPlantId(safeLongToInt((long)propertyEntry.getValue()));
                    break;
                case "heightFrom":
                    plant.setHeightFrom(safeLongToInt((long)propertyEntry.getValue()));
                    break;
                case "heightTo":
                    plant.setHeightTo(safeLongToInt((long)propertyEntry.getValue()));
                    break;
                case "floweringFrom":
                    plant.setFloweringFrom(safeLongToInt((long)propertyEntry.getValue()));
                    break;
                case "floweringTo":
                    plant.setFloweringTo(safeLongToInt((long)propertyEntry.getValue()));
                    break;
                case "toxicityClass":
                    plant.setToxicityClass(safeLongToInt((long)propertyEntry.getValue()));
                    break;
                case "wikiName":
                    plant.setWikiName((String)propertyEntry.getValue());
                    break;
                case "illustrationUrl":
                    plant.setIllustrationUrl((String)propertyEntry.getValue());
                    break;
                case "photoUrl":
                    plant.setPhotoUrls((List<String>)propertyEntry.getValue());
                    break;
                case "synonym":
                    plant.setSynonyms((List<String>)propertyEntry.getValue());
                    break;
            }

            if (propertyName.startsWith("label_")) {
                plant.getLabel().put(propertyName.substring(propertyName.indexOf("_") + 1), (String) propertyEntry.getValue());
            } else if (propertyName.startsWith("alias_")) {
                plant.getNames().put(propertyName.substring(propertyName.indexOf("_")+1), (List<String>)propertyEntry.getValue());
            } else if (propertyName.startsWith("description_")) {
                plant.getDescription().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("flower_")) {
                plant.getFlower().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("inflorescence_")) {
                plant.getInflorescence().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("fruit_")) {
                plant.getFruit().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("leaf_")) {
                plant.getLeaf().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("stem_")) {
                plant.getStem().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("habitat_")) {
                plant.getHabitat().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("toxicity_")) {
                plant.getToxicity().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("trivia_")) {
                plant.getTrivia().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("herbalism_")) {
                plant.getHerbalism().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("wiki_")) {
                plant.getWikilinks().put(propertyName.substring(propertyName.indexOf("_")+1), (String)propertyEntry.getValue());
            } else if (propertyName.startsWith("sourceUrl_")) {
                plant.getSourceUrls().put(propertyName.substring(propertyName.indexOf("_")+1), (List<String>)propertyEntry.getValue());
            }
        }

        return plant;
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
                new Query.FilterPredicate(Constants.LANGUAGE_LA, Query.FilterOperator.EQUAL, genus);
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
        if (plant.getFilterInflorescence() != null && plant.getFilterInflorescence().size() > 0) {
            plantEntity.setProperty("filterInflorence", plant.getFilterInflorescence());
        }
        if (plant.getFilterSepal() != null && plant.getFilterSepal().size() > 0) {
            plantEntity.setProperty("filterSepal", plant.getFilterSepal());
        }
        if (plant.getFilterStem() != null && plant.getFilterStem().size() > 0) {
            plantEntity.setProperty("filterStem", plant.getFilterStem());
        }
        if (plant.getFilterLeafShape() != null && plant.getFilterLeafShape().size() > 0) {
            plantEntity.setProperty("filterLeafShape", plant.getFilterLeafShape());
        }
        if (plant.getFilterLeafMargin() != null && plant.getFilterLeafMargin().size() > 0) {
            plantEntity.setProperty("filterLeafMargin", plant.getFilterLeafMargin());
        }
        if (plant.getFilterLeafVenetation() != null && plant.getFilterLeafVenetation().size() > 0) {
            plantEntity.setProperty("filterLeafVenetation", plant.getFilterLeafVenetation());
        }
        if (plant.getFilterLeafArrangement() != null && plant.getFilterLeafArrangement().size() > 0) {
            plantEntity.setProperty("filterLeafArrangement", plant.getFilterLeafArrangement());
        }
        if (plant.getFilterRoot() != null && plant.getFilterRoot().size() > 0) {
            plantEntity.setProperty("filterRoot", plant.getFilterRoot());
        }

        for(Map.Entry<String, String> description : plant.getDescription().entrySet()) {
            plantEntity.setProperty("description_"+description.getKey(), description.getValue());
        }
        for(Map.Entry<String, String> flower : plant.getFlower().entrySet()) {
            plantEntity.setProperty("flower_"+flower.getKey(), flower.getValue());
        }
        for(Map.Entry<String, String> inflorescence : plant.getInflorescence().entrySet()) {
            plantEntity.setProperty("inflorescence_"+inflorescence.getKey(), inflorescence.getValue());
        }
        for(Map.Entry<String, String> fruit : plant.getFruit().entrySet()) {
            plantEntity.setProperty("fruit_"+fruit.getKey(), fruit.getValue());
        }
        for(Map.Entry<String, String> leaf : plant.getLeaf().entrySet()) {
            plantEntity.setProperty("leaf_"+leaf.getKey(), leaf.getValue());
        }
        for(Map.Entry<String, String> stem : plant.getStem().entrySet()) {
            plantEntity.setProperty("stem_"+stem.getKey(), stem.getValue());
        }
        for(Map.Entry<String, String> habitat : plant.getHabitat().entrySet()) {
            plantEntity.setProperty("habitat_"+habitat.getKey(), habitat.getValue());
        }
        for(Map.Entry<String, String> trivia : plant.getTrivia().entrySet()) {
            plantEntity.setProperty("trivia_"+trivia.getKey(), trivia.getValue());
        }
        for(Map.Entry<String, String> toxicity : plant.getToxicity().entrySet()) {
            plantEntity.setProperty("toxicity_"+toxicity.getKey(), toxicity.getValue());
        }
        for(Map.Entry<String, String> herbalism : plant.getHerbalism().entrySet()) {
            plantEntity.setProperty("herbalism_"+herbalism.getKey(), herbalism.getValue());
        }
        for(Map.Entry<String, List<String>> sourceUrl : plant.getSourceUrls().entrySet()) {
            if (sourceUrl.getValue() != null && sourceUrl.getValue().size() > 0) {
                plantEntity.setProperty("sourceUrl_" + sourceUrl.getKey(), sourceUrl.getValue());
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

                Object latinProperty = entity.getProperty(Constants.LANGUAGE_LA);
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

    private void modifyEntityWikiSpeciesAfterWikidata(Entity entity, String latinName) {
        try {
            List<String> latinAliases = (List<String>) entity.getProperty("alias_" + Constants.LANGUAGE_LA);
            if (latinAliases == null) {
                latinAliases = new ArrayList<>();
            }
            List<String> latinAliasesLower = new ArrayList<>();
            for (String latinAlias : latinAliases) {
                latinAliasesLower.add(latinAlias.toLowerCase());
            }

            for(String key: entity.getProperties().keySet()) {
                if (key.startsWith("alias_")) {
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

                    if (entity.getProperty("label_"+language) == null) {
                        if (speciesValues.size() > 0) {
                            entity.setProperty("label_"+language, speciesValues.get(0));
                            speciesValues.remove(0);
                            if (speciesValues.size() > 0) {
                                entity.setProperty("alias_"+language, speciesValues);
                            }
                        }
                    } else {
                        String label = entity.getProperty("label_"+language).toString();

                        if (label.equals(latinName) && !language.equals(Constants.LANGUAGE_LA)) {
                            if (speciesValues.size() > 0) {
                                entity.setProperty("label_" + language, speciesValues.get(0));
                                speciesValues.remove(0);
                            }
                        }

                        if (entity.getProperty("alias_"+language) != null) {
                            List<String> aliasesOld = (List<String>) entity.getProperty("alias_" + language);
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
                                entity.setProperty("alias_" + language, aliases);
                            } else {
                                entity.removeProperty("alias_" + language);
                            }
                        } else if (speciesValues.size() > 0) {
                            entity.setProperty("alias_"+language, speciesValues);
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
                        entity.setProperty("label_"+language, speciesValues.get(0));
                        speciesValues.remove(0);
                        if (speciesValues.size() > 0) {
                            entity.setProperty("alias_"+language, speciesValues);
                        }
                    } else {
                        entity.setProperty("label_"+language, hlp[1].trim());
                    }
                }
            }

            String[] lines = wikiPage.split("\n");

            Set<String> synonymSet = new TreeSet<>();
            String key = "synonym";
            boolean isSynonyms = false;
            for(String line : lines) {
                if (line.contains("Synonym") || line.contains("{{SN")) {
                    isSynonyms = true;
                }

                if (!isSynonyms) {
                    continue;
                }

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

                    if (line.contains("References") || line.contains("Vernacular names")
                            || (line.contains("Hybrids") && line.contains("=="))
                            || (line.contains("Notes") && line.contains("=="))) {
                        break;
                    }

                    if (line.contains("''") && line.substring(line.indexOf("''")+2).contains("''")) {
                        String synonym = line.substring(line.indexOf("''")+2, line.indexOf("''", line.indexOf("''   ")+2));

                        if (synonym.startsWith("[[")) {
                            synonym = synonym.substring(2);
                        }
                        if (synonym.endsWith("]]")) {
                            synonym = synonym.substring(0,synonym.length()-2);
                        }

                        synonymSet.add(synonym.replace("'", ""));
                    }
                }
            }
            synonymSet.remove(name);
            synonymSet.remove("{{BASEPAGENAME}}");
            synonymSet.remove("Homotypic");
            synonymSet.remove("Heterotypic");
            synonymSet.remove("Basionym");
            synonymSet.remove("Homonyms");
            synonymSet.remove("vide");
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

                entity.setProperty("label_"+value.get("language").getAsString(), value.get("value").getAsString());
            }

            for (Map.Entry<String,JsonElement> entry : aliases.entrySet()) {
                JsonArray value = entry.getValue().getAsJsonArray();

                List<String> aliasList = new ArrayList<>();
                for(JsonElement v : value) {
                    aliasList.add(v.getAsJsonObject().get("value").getAsString());
                }

                entity.setProperty("alias_"+entry.getKey(), aliasList);
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
                site = site.substring(0, site.length()-4);

                entity.setProperty("wiki_"+site, value.get("url").getAsString());
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

            return href.substring(href.indexOf("oldid=")+6);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


    public static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }
}