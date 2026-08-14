package com.universalprinter.star

import com.starmicronics.stario10.StarIO10CommunicationException
import com.starmicronics.stario10.StarIO10IllegalHostDeviceStateException
import com.starmicronics.stario10.StarIO10NotFoundException
import com.starmicronics.stario10.StarIO10ServerCommunicationException
import com.universalprinter.model.PrintErrorReason

/**
 * Maps a StarXpand (StarIO10) failure to a typed [PrintErrorReason], mirroring [escPosReason]. Only
 * proven **connectivity** failures become the actionable NOT_CONNECTED ("can't reach the printer");
 * everything else (argument/bad-response/unprintable/in-use/…) stays UNKNOWN → the generic message,
 * with the real cause carried in `details`. Exception classes verified in the resolved
 * `com.starmicronics:stario10` AAR.
 *
 * `StarIO10InUseException` is intentionally NOT connectivity — the printer is reachable, just held by
 * another host — so "check it's connected" would mislead; it stays generic.
 */
internal fun starReason(t: Throwable): PrintErrorReason = when (t) {
    is StarIO10NotFoundException,
    is StarIO10CommunicationException,
    is StarIO10ServerCommunicationException,
    is StarIO10IllegalHostDeviceStateException -> PrintErrorReason.NOT_CONNECTED
    else -> PrintErrorReason.UNKNOWN
}
