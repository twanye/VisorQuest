package pojlib.util;

import android.app.Activity;

public class Constants {

    public static final String MOJANG_META_URL = "https://piston-meta.mojang.com";

    public static final String MOJANG_RESOURCES_URL = "https://resources.download.minecraft.net";

    public static final String FABRIC_META_URL = "https://meta.fabricmc.net/v2";

    public static final String QUILT_META_URL = "https://meta.quiltmc.org/v3";

    public static final String OAUTH_TOKEN_URL = "https://login.live.com/oauth20_token.srf";

    public static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";

    public static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

    public static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

    public static final String MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    public static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    public static final String MINOTAR_URL = "https://minotar.net";
    // TODO(User): decide where VisorQuest will host its mods manifests; keep this upstream URL until then.
    public static final String UPSTREAM_QUESTCRAFT_MANIFEST_BASE_URL =
            "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/refs/heads/QuestCraft-6.0.0/";
    public static String USER_HOME;

    public static void initConstants(Activity activity) {
        USER_HOME = activity.getExternalFilesDir(null).getAbsolutePath();
    }
}