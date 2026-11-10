package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pack index written by tools/packs/make_packs.py (0.5). */
class RegionPacksTest {
    private val text = """{"format":1,"version":"2026-11-11","packs":[
        {"id":"JP-15/manhole","kind":"catalogue","type":"MANHOLE_CARD","core":true,"code":"JP-15","prefecture":"新潟県","prefecture_en":"Niigata","items":48,"file":"JP-15-manhole.csv.gz","bytes":4800,"sha256":"aa"},
        {"id":"JP-15/roadside","kind":"catalogue","type":"ROADSIDE_STATION","core":false,"code":"JP-15","prefecture":"新潟県","prefecture_en":"Niigata","items":42,"file":"JP-15-roadside.csv.gz","bytes":2500,"sha256":"bb"},
        {"id":"JP-15/map","kind":"map","core":true,"code":"JP-15","prefecture":"新潟県","prefecture_en":"Niigata","file":"JP-15-map.pmtiles","bytes":36159127,"sha256":"cc","rings":[[[138.0,37.0],[139.0,37.0],[139.0,38.0],[138.0,37.0]]]},
        {"id":"JP-13/manhole","kind":"catalogue","type":"MANHOLE_CARD","core":true,"code":"JP-13","prefecture":"東京都","prefecture_en":"Tokyo","items":101,"file":"JP-13-manhole.csv.gz","bytes":11800,"sha256":"dd"}
    ]}"""

    @Test fun readsPacksByPrefecture() {
        val index = RegionPacks.parse(text)
        assertEquals(listOf("新潟県", "東京都"), index.prefectures)
        val niigata = index.of("新潟県")
        assertEquals(3, niigata.size)
        val map = niigata.single { it.isMap }
        assertEquals(36159127L, map.bytes)
        assertEquals(1, map.rings.size)
        assertEquals(8, map.rings[0].size) // four points as x,y pairs
        assertFalse(niigata.single { it.type == ItemType.ROADSIDE_STATION }.core)
        assertTrue(niigata.single { it.type == ItemType.MANHOLE_CARD }.core)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAnUnknownFormat() {
        RegionPacks.parse("""{"format":2,"version":"x","packs":[]}""")
    }
}
