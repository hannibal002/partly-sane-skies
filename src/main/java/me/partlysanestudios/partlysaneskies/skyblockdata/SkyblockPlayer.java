package me.partlysanestudios.partlysaneskies.skyblockdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.partlysanestudios.partlysaneskies.PartlySaneSkies;
import me.partlysanestudios.partlysaneskies.utils.StringUtils;
import me.partlysanestudios.partlysaneskies.utils.Utils;
import me.partlysanestudios.partlysaneskies.system.requests.PolyfrostUrsaMajorRequest;
import me.partlysanestudios.partlysaneskies.system.requests.Request;
import me.partlysanestudios.partlysaneskies.system.requests.RequestsManager;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.apache.logging.log4j.Level;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

public class SkyblockPlayer {
    private String username;
    private String playerSkyblockJsonString;
    private JsonObject playerSkyblockJsonObject;
    private String playerHypixelJsonString;
    private JsonObject playerHypixelJsonObject;
    private String selectedProfileUUID;
    public String uuid;
    public long lastUpdateTime;
    private static final class Lock { }
    private final Lock lock = new Lock();


    public float skyblockLevel;
    public float catacombsLevel;
    public float combatLevel;
    public float miningLevel;
    public float foragingLevel;
    public float farmingLevel;
    public float enchantingLevel;
    public float fishingLevel;
    public float alchemyLevel;
    public float tamingLevel;
    public float averageSkillLevel;

    public String[] armorName;
    public int arrowCount;
    public String petName;
    public String selectedDungeonClass;
    public int[] normalRunCount;
    public int[] masterModeRunCount;

    public int totalRuns;

    public int secretsCount;
    public float secretsPerRun;
    public float baseHealth;
    public float baseDefense;
    public float baseIntelligence;
    public float baseEffectedHealth;



//    Create new player data by username by requesting the UUID in the same thread
    public SkyblockPlayer(String name) {
        this.username = name;

    }

//    Instantiates the player and data while freezing the current thread until complete
//    Running on the main thread will freeze indefinitely
    public void instantiatePlayer() throws MalformedURLException {
        Utils.log(Level.INFO, "Creating Player");

        if (uuid == null) {
            String requestURL = "https://api.mojang.com/users/profiles/minecraft/" + username;

            RequestsManager.newRequest(new Request(requestURL, request -> {
                if (!request.hasSucceeded()) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
                if (new JsonParser().parse(request.getResponse()).getAsJsonObject().get("id") == null) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }

                    throw new IllegalArgumentException("Player " + username + " is not registered in the Mojang API");
                }
                this.uuid = new JsonParser().parse(request.getResponse()).getAsJsonObject().get("id").getAsString();
                Utils.log(Level.INFO, "Created Player. Requesting Data");
                try {
                    this.requestData();
                } catch (MalformedURLException e) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                    e.printStackTrace();
                }

            }, false, false));
        }
        else {
            Utils.log(Level.INFO, "Created Player. Requesting Data");
            try {
                this.requestData();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        try {
            Utils.log(Level.INFO, "Waiting....");
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Utils.log(Level.INFO, "Done waiting");
    }

    public boolean isExpired() {
        return !Utils.onCooldown(lastUpdateTime, PartlySaneSkies.config.playerDataCacheTime * 60 * 1000L);
    }

//    Creates new player data by UUID
    public SkyblockPlayer(UUID uuid) {
        this.uuid = uuid.toString();
    }

    private void requestData() throws MalformedURLException {
        String hypixelPlayerRequestURL = "https://api.polyfrost.cc/ursa/v1/hypixel/player/" + this.uuid;
        RequestsManager.newRequest(new PolyfrostUrsaMajorRequest(hypixelPlayerRequestURL, request -> {
            this.playerHypixelJsonString = request.getResponse();
            this.playerHypixelJsonObject = new JsonParser().parse(this.playerHypixelJsonString).getAsJsonObject();
        }, false, false));
        String profileRequestURL = "https://api.polyfrost.cc/ursa/v1/hypixel/skyblock/profiles/" + this.uuid;

        RequestsManager.newRequest(new PolyfrostUrsaMajorRequest(profileRequestURL, request -> {
            this.playerSkyblockJsonString = request.getResponse();

            this.playerSkyblockJsonObject = new JsonParser().parse(this.playerSkyblockJsonString).getAsJsonObject();
            this.selectedProfileUUID = playerSkyblockJsonObject.getAsJsonArray("profiles").get(0).getAsJsonObject().get("profile_id").getAsString();

            for (JsonElement element : playerSkyblockJsonObject.getAsJsonArray("profiles")) {
                JsonObject profile = element.getAsJsonObject();
                if (profile.get("selected").getAsBoolean()) {
                    selectedProfileUUID = profile.get("profile_id").getAsString();
                    break;
                }
            }
            Utils.log(Level.INFO, "Recieved Data. Populating Stats");
            this.populateStats();
            Utils.log(Level.INFO, "Player data successfully created and initiated");

        }, false, false));
    }

    private JsonObject getProfileById(String uuid) {
        for (JsonElement element : playerSkyblockJsonObject.getAsJsonArray("profiles")) {
            JsonObject profile = element.getAsJsonObject();
            if (profile.get("profile_id").getAsString().equals(uuid)) {
                return profile;
            }
        }

        return null;
    }

    private void populateStats() {
        JsonObject selectedProfile = getProfileById(selectedProfileUUID);
        if (selectedProfile == null) {
            synchronized (lock) {
                lock.notifyAll();
            }
            return;
        }
        if (Utils.getJsonFromPath(selectedProfile, "members/" + uuid + "/") == null) {
            synchronized (lock) {
                lock.notifyAll();
            }
            return;
        }
        JsonObject playerProfile = Utils.getJsonFromPath(selectedProfile, "members/" + uuid + "/").getAsJsonObject();


        skyblockLevel = Utils.getJsonFromPath(playerProfile, "/leveling/experience") == null ? 0: Utils.getJsonFromPath(playerProfile, "/leveling/experience").getAsFloat()/100;
        catacombsLevel = Utils.getJsonFromPath(playerProfile, "dungeons/dungeon_types/catacombs/experience") == null ? 0: catacombsLevelToExperience(Utils.getJsonFromPath(playerProfile, "dungeons/dungeon_types/catacombs/experience").getAsFloat());
        combatLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_combat") == null ? 0: SkyblockDataManager.getSkill("COMBAT").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_combat").getAsFloat());
        miningLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_mining") == null ? 0: SkyblockDataManager.getSkill("MINING").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_mining").getAsFloat());
        foragingLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_foraging") == null ? 0: SkyblockDataManager.getSkill("FORAGING").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_foraging").getAsFloat());
        farmingLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_farming") == null ? 0: SkyblockDataManager.getSkill("FARMING").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_farming").getAsFloat());
        enchantingLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_enchanting") == null ? 0: SkyblockDataManager.getSkill("ENCHANTING").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_enchanting").getAsFloat());
        fishingLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_fishing") == null ? 0: SkyblockDataManager.getSkill("FISHING").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_fishing").getAsFloat());
        alchemyLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_alchemy") == null ? 0: SkyblockDataManager.getSkill("ALCHEMY").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_alchemy").getAsFloat());
        tamingLevel = Utils.getJsonFromPath(playerProfile, "experience_skill_taming") == null ? 0: SkyblockDataManager.getSkill("TAMING").getLevelFromExperience(Utils.getJsonFromPath(playerProfile, "experience_skill_taming").getAsFloat());
        averageSkillLevel = (combatLevel + miningLevel + foragingLevel + farmingLevel + enchantingLevel + fishingLevel + alchemyLevel + tamingLevel) / 8;



        try {
            if (Utils.getJsonFromPath(playerProfile, "inv_armor/data") == null) {
                armorName = new String[4];
            }
            else {
                NBTTagList armorNBT = base64ToNbt(Utils.getJsonFromPath(playerProfile, "inv_armor/data").getAsString()).getTagList("i", 10);
                armorName = new String[armorNBT.tagCount()];
                for (int i = 0; i < armorNBT.tagCount(); i++) {

                    armorName[i] = armorNBT.getCompoundTagAt(i).getCompoundTag("tag").getCompoundTag("display").getString("Name");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (Utils.getJsonFromPath(playerProfile, "quiver/data") == null) {
            arrowCount = -1;
        }
        else {
            try {
                NBTTagList arrowNBT = base64ToNbt(Utils.getJsonFromPath(playerProfile, "quiver/data").getAsString()).getTagList("i", 10);

                int sum = 0;
                for (int i = 0; i < arrowNBT.tagCount(); i++) {
                    NBTTagCompound item = arrowNBT.getCompoundTagAt(i);
                    NBTTagCompound itemDisplayTag = arrowNBT.getCompoundTagAt(i).getCompoundTag("tag").getCompoundTag("display");

                    if (!itemDisplayTag.hasKey("Lore")) {
                        continue;
                    }
                    NBTTagList loreList = itemDisplayTag.getTagList("Lore", 8);
                    for (int k = 0; k < loreList.tagCount(); k++) {
                        String loreLine = loreList.getStringTagAt(k);
                        if (loreLine.contains("ARROW")) {
                            if (item.hasKey("Count")) {
                                sum += item.getInteger("Count");
                            }
                            else {
                                sum += 1;
                            }
                            break;
                        }
                    }
                }
                arrowCount = sum;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



        if (Utils.getJsonFromPath(playerProfile, "pets") == null) {
            petName = "";
        }
        else {
            JsonArray petsArray = Utils.getJsonFromPath(playerProfile, "pets").getAsJsonArray();
            String selectedPetName = "";
            for (JsonElement element : petsArray) {
                JsonObject petObject = element.getAsJsonObject();
                if (petObject.get("active").getAsBoolean()) {
                    selectedPetName = (petObject.get("tier") + " " + petObject.get("type")).replace("\"", "").replace("_", " ");
                    break;
                }
            }
            selectedPetName = StringUtils.titleCase(selectedPetName);
            petName = selectedPetName;
        }


        selectedDungeonClass = Utils.getJsonFromPath(playerProfile, "dungeons/selected_dungeon_class") == null ? "":StringUtils.titleCase(Utils.getJsonFromPath(playerProfile, "dungeons/selected_dungeon_class").getAsString());

        totalRuns = 0;

        normalRunCount = new int[8];

        for (int i = 0; i < normalRunCount.length; i++) {
            String path = "dungeons/dungeon_types/catacombs/times_played/" + i;
            if (Utils.getJsonFromPath(playerProfile, path) != null) {
                normalRunCount[i] = Utils.getJsonFromPath(playerProfile, path).getAsInt();
            }
            else {
                normalRunCount[i] = 0;
            }

            totalRuns += normalRunCount[i];
        }

        masterModeRunCount = new int[8];
        for (int i = 0; i < masterModeRunCount.length; i++) {
            String path = "dungeons/dungeon_types/master_catacombs/tier_completions/" + i;
            if (Utils.getJsonFromPath(playerProfile, path) != null) {
                masterModeRunCount[i] = Utils.getJsonFromPath(playerProfile, path).getAsInt();
            }
            else {
                masterModeRunCount[i] = 0;
            }

            totalRuns += masterModeRunCount[i];
        }

        secretsCount =  Utils.getJsonFromPath(this.playerHypixelJsonObject, "/player/achievements/skyblock_treasure_hunter") == null ? 0: Utils.getJsonFromPath(this.playerHypixelJsonObject, "/player/achievements/skyblock_treasure_hunter").getAsInt();

        secretsPerRun = (float) secretsCount / totalRuns;

        baseHealth = 100;
        baseDefense = 0;
        baseIntelligence = 100;
        baseEffectedHealth = 100;




        this.lastUpdateTime = PartlySaneSkies.getTime();
        synchronized (lock) {
            lock.notifyAll();
        }

    }

    public void refresh() {
        lastUpdateTime = 0;
    }

    int[] catacombsExperiencePerLevel = {
        50,
        75,
        110,
        160,
        230,
        330,
        470,
        670,
        950,
        1340,
        1890,
        2665,
        3760,
        5260,
        7380,
        10300,
        14400,
        20000,
        27600,
        38000,
        52500,
        71500,
        97000,
        132000,
        180000,
        243000,
        328000,
        445000,
        600000,
        800000,
        1065000,
        1410000,
        1900000,
        2500000,
        3300000,
        4300000,
        5600000,
        7200000,
        9200000,
        12000000,
        15000000,
        19000000,
        24000000,
        30000000,
        38000000,
        48000000,
        60000000,
        75000000,
        93000000,
        116250000
    };
    private float catacombsLevelToExperience(float experience) {
        float level = 0;

        if (experience >= catacombsExperiencePerLevel[catacombsExperiencePerLevel.length - 1]) {
            return 50;
        }

        for (int i = 0; i < catacombsExperiencePerLevel.length; i++) {
            if (experience > catacombsExperiencePerLevel[i]) {
                level = i - 1;
            }
        }

        level += (experience - catacombsExperiencePerLevel[(int) level]) / (catacombsExperiencePerLevel[(int) level + 1] - catacombsExperiencePerLevel[(int) level]);

        return level;
    }

    private NBTTagCompound base64ToNbt(String base64String) throws IOException {

        byte[] bytes = Base64.getDecoder().decode(base64String);

        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
    }

    @Override
    public String toString() {
        return "SkyblockPlayer{" +
                "username='" + username + '\'' +
                ", selectedProfileUUID='" + selectedProfileUUID + '\'' +
                ", uuid='" + uuid + '\'' +
                ", lastUpdateTime=" + lastUpdateTime +
                ", skyblockLevel=" + skyblockLevel +
                ", catacombsLevel=" + catacombsLevel +
                ", combatLevel=" + combatLevel +
                ", miningLevel=" + miningLevel +
                ", foragingLevel=" + foragingLevel +
                ", farmingLevel=" + farmingLevel +
                ", enchantingLevel=" + enchantingLevel +
                ", fishingLevel=" + fishingLevel +
                ", alchemyLevel=" + alchemyLevel +
                ", tamingLevel=" + tamingLevel +
                ", averageSkillLevel=" + averageSkillLevel +
                ", armorName=" + Arrays.toString(armorName) +
                ", arrowCount=" + arrowCount +
                ", petName='" + petName + '\'' +
                ", selectedDungeonClass='" + selectedDungeonClass + '\'' +
                ", normalRunCount=" + Arrays.toString(normalRunCount) +
                ", masterModeRunCount=" + Arrays.toString(masterModeRunCount) +
                ", totalRuns=" + totalRuns +
                ", secretsCount=" + secretsCount +
                ", secretsPerRun=" + secretsPerRun +
                ", baseHealth=" + baseHealth +
                ", baseDefense=" + baseDefense +
                ", baseIntelligence=" + baseIntelligence +
                ", baseEffectedHealth=" + baseEffectedHealth +
                '}';
    }
}
