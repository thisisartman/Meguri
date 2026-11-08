package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.ItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class TileNameTest {
    @Test fun stationsLoseStation() = assertEquals("Echigo-Horinouchi", TileName.short("Echigo-Horinouchi Station", ItemType.EKI_STAMP, false))
    @Test fun anEkitagHasNoSuffix() = assertEquals("Urasa", TileName.short("Urasa Station", ItemType.EKITAG, false))
    @Test fun roadsideStationsLoseThePrefix() = assertEquals("Kurosu Ten Tsuki", TileName.short("Michi-no-Eki Kurosu Ten Tsuki", ItemType.ROADSIDE_STATION, false))
    @Test fun japaneseRoadsideLosesItsPrefix() = assertEquals("ゆのたに", TileName.short("道の駅 ゆのたに", ItemType.ROADSIDE_STATION, true))
    @Test fun japaneseStationsKeep駅() = assertEquals("浦佐駅", TileName.short("浦佐駅", ItemType.EKI_STAMP, true))
    @Test fun cardsAreUntouched() = assertEquals("Nagaoka · Kaen doki", TileName.short("Nagaoka · Kaen doki", ItemType.MANHOLE_CARD, false))
}
