package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CardNameTest {
    @Test fun englishDesignBecomesTheTitle() = assertEquals(
        "20 years as one city" to "Tsubame · 15-213-E001",
        CardName.split("燕市 合併20周年", "Tsubame · 20 years as one city", "燕市", "15-213-E001", japanese = false),
    )

    @Test fun japaneseDesignBecomesTheTitle() = assertEquals(
        "合併20周年" to "燕市 · 15-213-E001",
        CardName.split("燕市 合併20周年", "Tsubame · 20 years as one city", "燕市", "15-213-E001", japanese = true),
    )

    @Test fun japaneseCardWithNoDesignNameShowsItsCodeOnce() = assertEquals(
        "新潟市" to "15-100-B001",
        CardName.split("新潟市 B001", "Niigata-shi B001", "新潟市", "15-100-B001", japanese = true),
    )

    @Test fun aCardWithNoDesignNameShowsItsCodeOnceUnderTheName() = assertEquals(
        "Niigata-shi" to "15-100-B001",
        CardName.split("新潟市 B001", "Niigata-shi B001", "新潟市", "15-100-B001", japanese = false),
    )

    @Test fun noRomajiFallsBackToJapanese() = assertEquals(
        "アルビレックスBB" to "長岡市 · 15-202-C001",
        CardName.split("長岡市 アルビレックスBB", null, "長岡市", "15-202-C001", japanese = false),
    )
}
