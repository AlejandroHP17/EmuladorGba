package pelkidev.com.mx.emuladorgba

import androidx.compose.runtime.Composable
import pelkidev.com.mx.emuladorgba.presentation.EmuladorStartScreen
import pelkidev.com.mx.emuladorgba.ui.theme.EmuladorGbaTheme
import pelkidev.com.mx.minijuegos.sdk.GameModule

object EmuladorGbaGameModule : GameModule {
    override val id: String = "emuladorgba"
    override val title: String = "Emulador Game Boy Advance"
    override val description: String = "Juega un archivo .gba en el dispositivo"

    @Composable
    override fun Entry(onExit: () -> Unit) {
        EmuladorGbaTheme {
            EmuladorStartScreen(onExit = onExit)
        }
    }
}
