package io.github.thisisartman.collectibles.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.OpeningHours
import io.github.thisisartman.collectibles.data.OpeningHours.Status
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One short line from the item's hours text, and whether it means you can't get it now; null when the text can't be read. */
data class OpenLine(val text: String, val closed: Boolean)

/** Japan time, whatever the phone's zone: the places are in Japan. */
private val JAPAN = ZoneId.of("Asia/Tokyo")
private val HM = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun openLine(item: Item, now: LocalDateTime = LocalDateTime.now(JAPAN)): OpenLine? {
    val schedule = remember(item.hoursText, item.closedDays) { OpeningHours.parse(item.hoursText, item.closedDays) } ?: return null
    val line = when (val s = OpeningHours.status(schedule, now)) {
        is Status.OpenUntil -> OpenLine(stringResource(R.string.open_until, s.close.format(HM)), false)
        is Status.OpensAt -> OpenLine(stringResource(R.string.open_at, s.open.format(HM)), true)
        is Status.ClosedForToday -> OpenLine(stringResource(R.string.open_closed_for_today), true)
        Status.ClosedToday -> OpenLine(stringResource(R.string.open_closed_today), true)
        Status.OpenToday -> OpenLine(stringResource(R.string.open_today), false)
    }
    return if (schedule.approximate) line.copy(text = line.text + stringResource(R.string.open_may_vary)) else line
}
