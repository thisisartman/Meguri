package io.github.thisisartman.collectibles.photo

import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.PhotoBundle
import io.github.thisisartman.collectibles.data.PhotoKind

/**
 * Which slot an added photo goes to and how it is cut, from what the photo is and not only from what it was filed
 * as (owner, 2026-11-27: a photo file filed 24 manhole card photos as covers, so they were cut to circles and
 * became the cards' display pictures; "as soon as you detect that it's a card, automatically assign it to the
 * card", and "screenshots should be sorted as Ekitags"). Used by every way a photo comes in: a photo file, photos
 * shared into Meguri, the item page's Add photo and the camera. Pure, for tests.
 *
 * The rules:
 *  - A card item (manhole or dam card): a photo filed as the card (`card`, `stamp`, or 0.3's `proof`) goes to the
 *    Card slot ([PhotoKind.STAMP]). A photo filed as the cover that shows a card's blue frame at all goes to the
 *    Card slot too, never cut to a circle. A photo with no kind given goes to the Card slot when a whole card is
 *    found. A photo filed as the place stays the place.
 *  - Cut to a circle only a real cover photo: filed as the cover and no card frame in it.
 *  - Cut to the card ([CardTrim]) only when a whole card is found ([CardTrim.Result.OK]); a card that can't be
 *    trimmed is kept whole in the Card slot.
 *  - A station or Ekitag item: a screenshot is the Ekitag (an Ekitag only comes as a screenshot, and a station
 *    stamp is never one), unless it was filed as the place. Elsewhere a screenshot keeps its kind.
 *  - Cut to the stamp by its shape ([StampCrop], #221) every stamp photo of a stamp type (station, Ekitag photo,
 *    roadside station, museum, place, your own types), on every way in (owner, 2026-11-28). A photo given no kind
 *    on such an item that shows a clear stamp on paper ([Facts.stamp]) is the stamp too. A goshuin is cut to its
 *    page ([Crop.PAGE]). Place and other photos (scenery) stay whole. Only a caller that has cut already (a
 *    notebook stamp) says not to ([decide]'s cutStamps).
 */
object PhotoSort {
    /** How the saved copy is cut. */
    enum class Crop { NONE, CIRCLE, CARD, STAMP, EKITAG, PAGE }

    /**
     * What was seen in the picture: [card] is what [CardTrim] made of it (null when it wasn't looked at),
     * [screenshot] is [isScreenshot], and [stamp] says a stamp of a clear shape is on paper in it ([StampCrop.plan];
     * only looked for on a stamp type's photo given no kind, [checksStamp]).
     */
    data class Facts(val card: CardTrim.Result? = null, val screenshot: Boolean = false, val stamp: Boolean = false)

    data class Decision(val kind: String, val crop: Crop)

    fun isCardType(type: ItemType?) = type == ItemType.MANHOLE_CARD || type == ItemType.DAM_CARD

    fun isStationType(type: ItemType?) = type == ItemType.EKI_STAMP || type == ItemType.EKITAG

    /** Whether the picture needs looking at for a card frame: a card item's photo not filed as the place. */
    fun checksCard(type: ItemType?, requested: String?): Boolean = isCardType(type) && norm(requested) != PhotoKind.PLACE

    private fun norm(kind: String?): String? = kind?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != PhotoKind.OTHER }

    /**
     * The slot and the cut for a photo of an item of [type], filed as [requested] (null, empty or `other`: no kind
     * given, as in a share or a photo file row without one).
     */
    /** Whether a photo of [type] given no kind ([requested]) is looked at for a stamp ([Facts.stamp]). */
    fun checksStamp(type: ItemType?, requested: String?): Boolean =
        type != null && norm(requested) == null && PhotoBundle.cropsStamp(type, PhotoKind.STAMP)

    fun decide(type: ItemType?, requested: String?, facts: Facts, cutStamps: Boolean = true): Decision {
        val req = norm(requested)
        val anyFrame = facts.card != null && facts.card != CardTrim.Result.NO_FRAME
        val wholeCard = facts.card == CardTrim.Result.OK
        val kind = when {
            isCardType(type) -> when (req) {
                PhotoKind.PLACE -> PhotoKind.PLACE
                PhotoBundle.CARD, PhotoKind.STAMP, PhotoKind.PROOF -> PhotoKind.STAMP
                PhotoKind.MANHOLE -> if (anyFrame) PhotoKind.STAMP else PhotoKind.MANHOLE
                null -> if (wholeCard) PhotoKind.STAMP else PhotoKind.OTHER
                else -> req
            }
            isStationType(type) && facts.screenshot && req != PhotoKind.PLACE -> PhotoKind.EKITAG
            req == null && facts.stamp && type != null && PhotoBundle.cropsStamp(type, PhotoKind.STAMP) -> PhotoKind.STAMP
            // A card only has its own slot on a card item
            req == PhotoBundle.CARD -> PhotoKind.OTHER
            else -> req ?: PhotoKind.OTHER
        }
        val crop = when {
            kind == PhotoKind.MANHOLE && !anyFrame -> Crop.CIRCLE
            kind == PhotoKind.STAMP && isCardType(type) && wholeCard -> Crop.CARD
            kind == PhotoKind.EKITAG -> Crop.EKITAG
            cutStamps && type != null && PhotoBundle.cutsPage(type, kind) -> Crop.PAGE
            cutStamps && type != null && PhotoBundle.cropsStamp(type, kind) -> Crop.STAMP
            else -> Crop.NONE
        }
        return Decision(kind, crop)
    }

    private val SCREENSHOT_WORDS = listOf("screenshot", "スクリーンショット", "screen_shot", "screen shot")

    /**
     * Whether a picture is a phone screenshot: its file name or folder says so, or it has no camera details
     * (EXIF make and model) and is a phone screen's shape, upright and at least twice as tall as wide.
     */
    fun isScreenshot(name: String?, path: String?, width: Int, height: Int, hasCameraExif: Boolean): Boolean {
        val words = listOfNotNull(name, path).joinToString("/").lowercase()
        if (SCREENSHOT_WORDS.any { words.contains(it) }) return true
        return !hasCameraExif && width > 0 && height >= 2 * width
    }
}
