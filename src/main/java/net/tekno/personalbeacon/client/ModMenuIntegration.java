package net.tekno.personalbeacon.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Opens {@link PersonalBeaconConfigScreen} from Mod Menu's "Configure" button. Loaded only when Mod Menu is installed. */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PersonalBeaconConfigScreen::new;
    }
}
